package com.banksms.expensetracker.data.repository

import com.banksms.expensetracker.data.file.ExpenseFileManager
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.local.PersistentDatabase
import com.banksms.expensetracker.data.local.dao.BankSenderDao
import com.banksms.expensetracker.data.local.dao.TransactionDao
import com.banksms.expensetracker.data.local.entity.*
import com.banksms.expensetracker.data.model.*
import com.banksms.expensetracker.data.parser.BankSmsParser
import com.banksms.expensetracker.data.reader.DiscoveredSender
import com.banksms.expensetracker.data.reader.SmsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyncResult(
    val messagesScanned: Int,
    val transactionsImported: Int,
    val errors: List<String> = emptyList()
)

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val bankSenderDao: BankSenderDao,
    private val smsReader: SmsReader,
    private val fileManager: ExpenseFileManager,
    private val persistentDb: PersistentDatabase
) {

    // Persistent DB DAOs
    private val pManualDao get() = persistentDb.manualExpenseDao()
    private val pSkippedDao get() = persistentDb.skippedTransactionDao()
    private val pTemplateDao get() = persistentDb.messageTemplateDao()
    private val pBankDao get() = persistentDb.monitoredBankDao()

    private val _skippedTransactions = MutableStateFlow<List<SkippedTransaction>>(fileManager.getSkippedTransactions())
    val skippedTransactions: StateFlow<List<SkippedTransaction>> = _skippedTransactions.asStateFlow()

    private val _messageTemplates = MutableStateFlow<List<MessageTemplate>>(MessageTemplate.defaultTemplates)
    val messageTemplates: StateFlow<List<MessageTemplate>> = _messageTemplates.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            refreshFromFiles()
        }
    }

    /**
     * Processes an incoming real-time SMS from BroadcastReceiver with full deduplication and skip filtering.
     * @return true if a new transaction was inserted, false if skipped or already exists.
     */
    suspend fun processIncomingSms(
        sender: String,
        body: String,
        timestamp: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val monitored = bankSenderDao.getMonitoredSendersSync()
            .map { it.senderId.lowercase() }
            .toSet()

        val senderLower = sender.lowercase()
        val isMonitored = monitored.any {
            senderLower == it || senderLower.contains(it) || it.contains(senderLower)
        }
        if (!isMonitored) return@withContext false

        // Check if this transaction is marked as skipped in persistent DB
        if (isSkippedInDb(0L, sender, timestamp, body)) {
            return@withContext false
        }

        val enabledTemplates = pTemplateDao.getEnabled().map { it.toDomain() }
        val parsed = BankSmsParser.parse(body, sender, enabledTemplates) ?: return@withContext false

        // Prevent inserting if this transaction is already present
        val existing = transactionDao.findDuplicate(
            sender = sender,
            amount = parsed.amount,
            type = parsed.type.name,
            rawBody = body,
            timestamp = timestamp
        )
        if (existing != null) {
            return@withContext false
        }

        val transaction = parsed.toTransaction(
            messageId = timestamp,
            sender = sender,
            timestamp = timestamp,
            rawBody = body
        )
        val insertId = transactionDao.insert(TransactionEntity.fromDomain(transaction))
        insertId != -1L
    }

    /**
     * Scans both SMS messages and the persistent DB manual expenses.
     * Deduplicates against existing transactions and respects skipped transaction records.
     */
    suspend fun syncTransactionsFromSms(): SyncResult = withContext(Dispatchers.IO) {
        try {
            // 1. Clean up any historical duplicate transactions
            transactionDao.deleteDuplicates()

            // 2. Read message templates from persistent DB
            val templates = pTemplateDao.getAll().map { it.toDomain() }
            _messageTemplates.value = templates

            // 3. Refresh and enforce skipped transactions from persistent DB
            val skippedList = pSkippedDao.getAll().map { it.toDomain() }
            _skippedTransactions.value = skippedList
            for (skipped in skippedList) {
                if (skipped.originalMessageId != 0L) {
                    transactionDao.deleteByMessageId(skipped.originalMessageId)
                }
                val dup = transactionDao.findDuplicate(
                    sender = skipped.sender,
                    amount = skipped.amount,
                    type = skipped.type.name,
                    rawBody = skipped.rawBody,
                    timestamp = skipped.timestamp
                )
                if (dup != null) {
                    transactionDao.delete(dup)
                }
            }

            // 4. Scan manual expenses from persistent DB
            syncManualExpensesFromDb()

            // 5. Synchronize monitored banks from persistent DB to internal Room
            syncMonitoredBanksFromDb()

            // 6. Scan SMS inbox with freshly loaded templates
            val monitored = bankSenderDao.getMonitoredSendersSync()
            if (monitored.isEmpty()) {
                return@withContext SyncResult(0, 0, listOf("No monitored bank senders configured. Please enable banks in Settings."))
            }

            val monitoredSenderIds = monitored.map { it.senderId }.toSet()
            val enabledTemplates = templates.filter { it.isEnabled }
            val parsedTransactions = smsReader.readBankMessages(monitoredSenderIds, customTemplates = enabledTemplates)

            if (parsedTransactions.isEmpty()) {
                return@withContext SyncResult(0, 0)
            }

            var newlyImported = 0
            for (transaction in parsedTransactions) {
                // Skip if user marked this transaction as skipped
                if (isSkippedInDb(transaction.messageId, transaction.sender, transaction.timestamp, transaction.rawBody)) {
                    continue
                }

                val entity = TransactionEntity.fromDomain(transaction)

                // Check 1: Already exists with this exact messageId?
                val existingByMsgId = transactionDao.getByMessageId(entity.messageId)
                if (existingByMsgId != null) {
                    continue
                }

                // Check 2: Was it already inserted by real-time receiver (which used timestamp as messageId)?
                val existingDuplicate = transactionDao.findDuplicate(
                    sender = entity.sender,
                    amount = entity.amount,
                    type = entity.type,
                    rawBody = entity.rawBody,
                    timestamp = entity.timestamp
                )

                if (existingDuplicate != null) {
                    // Update existing record to match the permanent inbox messageId
                    if (existingDuplicate.messageId != entity.messageId) {
                        transactionDao.update(existingDuplicate.copy(messageId = entity.messageId))
                    }
                } else {
                    // Brand new transaction
                    val id = transactionDao.insert(entity)
                    if (id != -1L) {
                        newlyImported++
                    }
                }
            }

            SyncResult(
                messagesScanned = parsedTransactions.size,
                transactionsImported = newlyImported
            )
        } catch (e: Exception) {
            SyncResult(0, 0, listOf(e.localizedMessage ?: "Unknown sync error"))
        }
    }

    /**
     * Synchronizes manual expenses from persistent DB into internal Room.
     */
    private suspend fun syncManualExpensesFromDb() {
        val manualExpenses = pManualDao.getAll().map { it.toDomain() }
        val currentFileIds = manualExpenses.map { it.id }.toSet()

        // Remove any manual entries from Room that were deleted from persistent DB
        val existingDbManualIds = transactionDao.getAllManualIds()
        for (dbId in existingDbManualIds) {
            if (!currentFileIds.contains(dbId)) {
                transactionDao.deleteByManualId(dbId)
            }
        }

        // Upsert all manual expenses from persistent DB
        for (expense in manualExpenses) {
            val transaction = expense.toTransaction()
            val existing = transactionDao.getByManualId(expense.id)
            if (existing != null) {
                val updated = TransactionEntity.fromDomain(transaction).copy(id = existing.id)
                transactionDao.update(updated)
            } else {
                transactionDao.insert(TransactionEntity.fromDomain(transaction))
            }
        }
    }

    /**
     * Checks if a transaction is skipped using the persistent DB.
     */
    private suspend fun isSkippedInDb(
        messageId: Long,
        sender: String,
        timestamp: Long,
        rawBody: String
    ): Boolean {
        val skippedList = pSkippedDao.getAll().map { it.toDomain() }
        if (skippedList.isEmpty()) return false

        return skippedList.any { skipped ->
            (messageId != 0L && skipped.originalMessageId == messageId) ||
            (rawBody.isNotBlank() && skipped.rawBody.isNotBlank() && skipped.sender == sender && skipped.rawBody == rawBody) ||
            (skipped.sender == sender && Math.abs(skipped.timestamp - timestamp) < 60000 &&
             rawBody.contains(skipped.amount.toInt().toString()))
        }
    }

    // ── Manual Expenses API ───────────────────────────────────────────────

    suspend fun addManualExpense(expense: ManualExpense) = withContext(Dispatchers.IO) {
        // Write to persistent DB
        pManualDao.upsert(ManualExpenseEntity.fromDomain(expense))
        // Mirror to JSON backup
        exportManualExpensesToJson()
        // Sync to internal Room for queries
        val transaction = expense.toTransaction()
        transactionDao.insert(TransactionEntity.fromDomain(transaction))
    }

    suspend fun updateManualExpense(expense: ManualExpense) = withContext(Dispatchers.IO) {
        pManualDao.upsert(ManualExpenseEntity.fromDomain(expense))
        exportManualExpensesToJson()
        val existing = transactionDao.getByManualId(expense.id)
        val transaction = expense.toTransaction()
        if (existing != null) {
            val updated = TransactionEntity.fromDomain(transaction).copy(id = existing.id)
            transactionDao.update(updated)
        } else {
            transactionDao.insert(TransactionEntity.fromDomain(transaction))
        }
    }

    suspend fun deleteManualExpense(manualId: String) = withContext(Dispatchers.IO) {
        pManualDao.deleteById(manualId)
        exportManualExpensesToJson()
        transactionDao.deleteByManualId(manualId)
    }

    suspend fun getManualExpenses(): List<ManualExpense> = withContext(Dispatchers.IO) {
        pManualDao.getAll().map { it.toDomain() }
    }

    // ── Skipped Transactions API ──────────────────────────────────────────

    suspend fun skipTransaction(transaction: Transaction, reason: String = "User skipped") = withContext(Dispatchers.IO) {
        val skipped = SkippedTransaction.fromTransaction(transaction, reason)
        pSkippedDao.insert(SkippedTransactionEntity.fromDomain(skipped))
        exportSkippedToJson()
        _skippedTransactions.value = pSkippedDao.getAll().map { it.toDomain() }

        if (transaction.messageId != 0L) {
            transactionDao.deleteByMessageId(transaction.messageId)
        }
        transactionDao.deleteById(transaction.id)
    }

    suspend fun unskipTransaction(skipped: SkippedTransaction) = withContext(Dispatchers.IO) {
        // Remove from persistent DB using matching criteria
        if (skipped.originalMessageId != 0L) {
            pSkippedDao.deleteByMessageId(skipped.originalMessageId)
        }
        if (skipped.rawBody.isNotBlank()) {
            pSkippedDao.deleteBySenderAndBody(skipped.sender, skipped.rawBody)
        }
        pSkippedDao.deleteBySenderAmountTimestamp(skipped.sender, skipped.amount, skipped.timestamp)

        exportSkippedToJson()
        _skippedTransactions.value = pSkippedDao.getAll().map { it.toDomain() }
        // Re-sync inbox so this transaction is immediately restored to active log
        syncTransactionsFromSms()
    }

    suspend fun getSkippedTransactions(): List<SkippedTransaction> = withContext(Dispatchers.IO) {
        pSkippedDao.getAll().map { it.toDomain() }
    }

    // ── Message Templates API ─────────────────────────────────────────────

    fun getMessageTemplates(): List<MessageTemplate> {
        return _messageTemplates.value.ifEmpty { MessageTemplate.defaultTemplates }
    }

    suspend fun loadMessageTemplates(): List<MessageTemplate> = withContext(Dispatchers.IO) {
        val templates = pTemplateDao.getAll().map { it.toDomain() }
        if (templates.isEmpty()) {
            // Seed defaults if empty
            val defaults = MessageTemplate.defaultTemplates
            pTemplateDao.upsertAll(defaults.map { MessageTemplateEntity.fromDomain(it) })
            _messageTemplates.value = defaults
            exportTemplatesToJson()
            defaults
        } else {
            _messageTemplates.value = templates
            templates
        }
    }

    suspend fun saveMessageTemplate(template: MessageTemplate) = withContext(Dispatchers.IO) {
        pTemplateDao.upsert(MessageTemplateEntity.fromDomain(template))
        exportTemplatesToJson()
        _messageTemplates.value = pTemplateDao.getAll().map { it.toDomain() }
    }

    suspend fun deleteMessageTemplate(id: String) = withContext(Dispatchers.IO) {
        pTemplateDao.deleteById(id)
        exportTemplatesToJson()
        _messageTemplates.value = pTemplateDao.getAll().map { it.toDomain() }
    }

    suspend fun toggleMessageTemplate(id: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        pTemplateDao.toggleEnabled(id, isEnabled)
        exportTemplatesToJson()
        _messageTemplates.value = pTemplateDao.getAll().map { it.toDomain() }
    }

    // ── Monitored Banks API ───────────────────────────────────────────────

    /**
     * Synchronizes monitored banks from persistent DB to internal Room bank_senders table.
     */
    private suspend fun syncMonitoredBanksFromDb() {
        try {
            val persistentBanks = pBankDao.getAll().map { it.toDomain() }
            val persistentSenderIds = persistentBanks.map { it.senderId.lowercase() }.toSet()

            // Remove senders from Room that are no longer in persistent DB
            val allDbSenders = bankSenderDao.getAllSendersSync()
            for (dbSender in allDbSenders) {
                if (!persistentSenderIds.contains(dbSender.senderId.lowercase())) {
                    bankSenderDao.delete(dbSender)
                }
            }

            // Upsert all senders from persistent DB into internal Room
            for (bank in persistentBanks) {
                val existing = bankSenderDao.getBySenderId(bank.senderId)
                if (existing == null) {
                    bankSenderDao.insert(BankSenderEntity.fromDomain(bank))
                } else {
                    bankSenderDao.update(
                        existing.copy(
                            displayName = bank.displayName,
                            isMonitored = bank.isMonitored,
                            customRegex = bank.customRegex
                        )
                    )
                }
            }
        } catch (e: Exception) {
            System.err.println("Error syncing monitored banks from DB: ${e.message}")
        }
    }

    suspend fun getMonitoredBanks(): List<BankSender> = withContext(Dispatchers.IO) {
        val banks = pBankDao.getAll().map { it.toDomain() }
        if (banks.isEmpty()) {
            val defaults = BankSender.defaultSenders
            pBankDao.upsertAll(defaults.map { MonitoredBankEntity.fromDomain(it) })
            exportBanksToJson()
            defaults
        } else {
            banks
        }
    }

    /**
     * Discovers all sender addresses in SMS inbox so user can pick which ones are their banks.
     */
    suspend fun discoverSendersFromInbox(): List<DiscoveredSender> = withContext(Dispatchers.IO) {
        smsReader.discoverSenders()
    }

    fun getFilteredTransactions(
        startTime: Long,
        endTime: Long,
        type: TransactionType? = null,
        sender: String? = null,
        category: String? = null,
        searchQuery: String? = null
    ): Flow<List<Transaction>> {
        return transactionDao.getFilteredTransactionsFlow(
            startTime = startTime,
            endTime = endTime,
            type = if (type == TransactionType.UNKNOWN || type == null) null else type.name,
            sender = if (sender.isNullOrBlank() || sender == "ALL") null else sender,
            category = if (category.isNullOrBlank() || category == "ALL") null else category,
            searchQuery = if (searchQuery.isNullOrBlank()) null else searchQuery.trim()
        ).map { list -> list.map { it.toDomain() } }
    }

    fun getSummaryReport(startTime: Long, endTime: Long): Flow<SummaryReport> {
        val totalExpenseFlow = transactionDao.getTotalExpenseFlow(startTime, endTime)
        val totalIncomeFlow = transactionDao.getTotalIncomeFlow(startTime, endTime)
        val bankSummaryFlow = transactionDao.getBankExpenseSummaryFlow(startTime, endTime)
        val categorySummaryFlow = transactionDao.getCategoryExpenseSummaryFlow(startTime, endTime)
        val monthlyTrendFlow = transactionDao.getMonthlyTrendsFlow()

        return combine(
            totalExpenseFlow,
            totalIncomeFlow,
            bankSummaryFlow,
            categorySummaryFlow,
            monthlyTrendFlow
        ) { expense, income, banks, categories, trends ->
            val totalExp = expense ?: 0.0
            val totalInc = income ?: 0.0
            val net = totalInc - totalExp

            val bankShares = banks.map {
                BankExpenseShare(
                    sender = it.sender,
                    totalExpense = it.totalExpense ?: 0.0,
                    totalIncome = it.totalIncome ?: 0.0,
                    transactionCount = it.transactionCount
                )
            }

            val categoryShares = categories.map {
                val catAmount = it.totalAmount ?: 0.0
                val pct = if (totalExp > 0) ((catAmount / totalExp) * 100).toFloat() else 0f
                CategoryShare(
                    category = it.category,
                    totalAmount = catAmount,
                    count = it.count,
                    percentage = pct
                )
            }

            val monthlyTrends = trends.map {
                MonthlyTrend(
                    yearMonth = it.yearMonth,
                    monthLabel = formatYearMonth(it.yearMonth),
                    totalExpense = it.totalExpense ?: 0.0,
                    totalIncome = it.totalIncome ?: 0.0
                )
            }

            SummaryReport(
                totalExpense = totalExp,
                totalIncome = totalInc,
                netSavings = net,
                totalTransactions = bankShares.sumOf { it.transactionCount },
                bankShares = bankShares,
                categoryShares = categoryShares,
                monthlyTrends = monthlyTrends
            )
        }
    }

    fun getSendersWithStats(): Flow<List<BankSender>> {
        return bankSenderDao.getSendersWithStatsFlow().map { list ->
            list.map {
                BankSender(
                    senderId = it.senderId,
                    displayName = it.displayName,
                    isMonitored = it.isMonitored,
                    customRegex = it.customRegex,
                    totalTransactionsCount = it.totalTransactionsCount,
                    lastTransactionTime = it.lastTransactionTime
                )
            }
        }
    }

    suspend fun setSenderMonitored(senderId: String, isMonitored: Boolean) = withContext(Dispatchers.IO) {
        bankSenderDao.setMonitored(senderId, isMonitored)
        pBankDao.toggleMonitored(senderId, isMonitored)
        exportBanksToJson()
    }

    suspend fun addCustomSender(sender: BankSender) = withContext(Dispatchers.IO) {
        bankSenderDao.insert(BankSenderEntity.fromDomain(sender))
        pBankDao.upsert(MonitoredBankEntity.fromDomain(sender))
        exportBanksToJson()
    }

    suspend fun updateSender(oldSenderId: String, updated: BankSender) = withContext(Dispatchers.IO) {
        if (!oldSenderId.equals(updated.senderId, ignoreCase = true)) {
            val oldEntity = bankSenderDao.getBySenderId(oldSenderId)
            if (oldEntity != null) {
                bankSenderDao.delete(oldEntity)
            }
            bankSenderDao.insert(BankSenderEntity.fromDomain(updated))
            pBankDao.deleteBySenderId(oldSenderId)
        } else {
            bankSenderDao.update(BankSenderEntity.fromDomain(updated))
        }
        pBankDao.upsert(MonitoredBankEntity.fromDomain(updated))
        exportBanksToJson()
    }

    suspend fun deleteSender(sender: BankSender) = withContext(Dispatchers.IO) {
        val entity = bankSenderDao.getBySenderId(sender.senderId)
        if (entity != null) {
            bankSenderDao.delete(entity)
        }
        pBankDao.deleteBySenderId(sender.senderId)
        exportBanksToJson()
    }

    fun getStorageDirectoryPath(): String = fileManager.getStorageDirectoryPath()

    fun isPublicStorageActive(): Boolean = fileManager.isPublicStorageActive()

    fun getPersistentDbPath(): String {
        return try {
            persistentDb.openHelper.readableDatabase.path ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    suspend fun clearAllPersistenceFiles() = withContext(Dispatchers.IO) {
        // Clear persistent DB tables
        pManualDao.deleteAll()
        pSkippedDao.deleteAll()
        pTemplateDao.deleteAll()
        pBankDao.deleteAll()

        // Clear JSON backup files
        fileManager.clearAllFiles()

        _skippedTransactions.value = emptyList()

        // Re-seed defaults
        val defaultTemplates = MessageTemplate.defaultTemplates
        pTemplateDao.upsertAll(defaultTemplates.map { MessageTemplateEntity.fromDomain(it) })
        _messageTemplates.value = defaultTemplates

        val defaultBanks = BankSender.defaultSenders
        pBankDao.upsertAll(defaultBanks.map { MonitoredBankEntity.fromDomain(it) })

        // Sync defaults to internal Room
        syncMonitoredBanksFromDb()
    }

    /**
     * Refreshes cached state from persistent DB. Called on tab switches, etc.
     */
    suspend fun refreshFromFiles() = withContext(Dispatchers.IO) {
        val templates = pTemplateDao.getAll().map { it.toDomain() }
        _messageTemplates.value = templates.ifEmpty { MessageTemplate.defaultTemplates }
        val skipped = pSkippedDao.getAll().map { it.toDomain() }
        _skippedTransactions.value = skipped
        syncManualExpensesFromDb()
        syncMonitoredBanksFromDb()
    }

    suspend fun updateTransactionCategory(transactionId: Long, newCategory: String) {
        val existing = transactionDao.getById(transactionId) ?: return
        transactionDao.update(existing.copy(category = newCategory))
    }

    suspend fun deleteTransaction(transactionId: Long) {
        transactionDao.deleteById(transactionId)
    }

    suspend fun deleteAllTransactions() {
        transactionDao.deleteAll()
    }

    // ── JSON Backup Export Methods ─────────────────────────────────────────

    private suspend fun exportManualExpensesToJson() {
        try {
            val expenses = pManualDao.getAll().map { it.toDomain() }
            val jsonArray = org.json.JSONArray()
            expenses.forEach { jsonArray.put(it.toJsonObject()) }
            fileManager.writeJsonBackup("manual_expenses.json", jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private suspend fun exportSkippedToJson() {
        try {
            val skipped = pSkippedDao.getAll().map { it.toDomain() }
            val jsonArray = org.json.JSONArray()
            skipped.forEach { jsonArray.put(it.toJsonObject()) }
            fileManager.writeJsonBackup("skipped_transactions.json", jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private suspend fun exportTemplatesToJson() {
        try {
            val templates = pTemplateDao.getAll().map { it.toDomain() }
            val jsonArray = org.json.JSONArray()
            templates.forEach { jsonArray.put(it.toJsonObject()) }
            fileManager.writeJsonBackup("message_templates.json", jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private suspend fun exportBanksToJson() {
        try {
            val banks = pBankDao.getAll().map { it.toDomain() }
            val jsonArray = org.json.JSONArray()
            banks.forEach { jsonArray.put(it.toJsonObject()) }
            fileManager.writeJsonBackup("monitored_banks.json", jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private fun formatYearMonth(ym: String): String {
        return try {
            val parts = ym.split("-")
            val year = parts[0]
            val month = parts[1].toInt()
            val monthName = when (month) {
                1 -> "Jan"
                2 -> "Feb"
                3 -> "Mar"
                4 -> "Apr"
                5 -> "May"
                6 -> "Jun"
                7 -> "Jul"
                8 -> "Aug"
                9 -> "Sep"
                10 -> "Oct"
                11 -> "Nov"
                12 -> "Dec"
                else -> ym
            }
            "$monthName $year"
        } catch (e: Exception) {
            ym
        }
    }
}

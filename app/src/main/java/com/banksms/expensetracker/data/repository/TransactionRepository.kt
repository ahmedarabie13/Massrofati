package com.banksms.expensetracker.data.repository

import com.banksms.expensetracker.data.file.ExpenseFileManager
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.local.dao.BankSenderDao
import com.banksms.expensetracker.data.local.dao.TransactionDao
import com.banksms.expensetracker.data.local.entity.BankSenderEntity
import com.banksms.expensetracker.data.local.entity.TransactionEntity
import com.banksms.expensetracker.data.model.*
import com.banksms.expensetracker.data.parser.BankSmsParser
import com.banksms.expensetracker.data.reader.DiscoveredSender
import com.banksms.expensetracker.data.reader.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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
    private val fileManager: ExpenseFileManager
) {

    private val _skippedTransactions = MutableStateFlow<List<SkippedTransaction>>(fileManager.getSkippedTransactions())
    val skippedTransactions: StateFlow<List<SkippedTransaction>> = _skippedTransactions.asStateFlow()

    private val _messageTemplates = MutableStateFlow<List<MessageTemplate>>(fileManager.getMessageTemplates())
    val messageTemplates: StateFlow<List<MessageTemplate>> = _messageTemplates.asStateFlow()

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

        // Check if this transaction is marked as skipped in the file
        if (fileManager.isSkipped(0L, sender, timestamp, body)) {
            return@withContext false
        }

        val enabledTemplates = fileManager.getMessageTemplates().filter { it.isEnabled }
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
     * Scans both SMS messages and the local manual expenses file.
     * Deduplicates against existing transactions and respects skipped transaction records.
     */
    suspend fun syncTransactionsFromSms(): SyncResult = withContext(Dispatchers.IO) {
        try {
            // 1. Clean up any historical duplicate transactions
            transactionDao.deleteDuplicates()

            // 2. Reload message templates from file system
            val templates = fileManager.getMessageTemplates()
            _messageTemplates.value = templates

            // 3. Refresh and enforce skipped transactions from the file
            val skippedList = fileManager.getSkippedTransactions()
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

            // 4. Scan manual expenses from manual_expenses.json
            syncManualExpensesFromFile()

            // 5. Synchronize monitored banks configuration with monitored_banks.json
            syncMonitoredBanksWithFile()

            // 6. Scan SMS inbox with freshly reloaded templates
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
                // Skip if user marked this transaction as skipped in the file
                if (fileManager.isSkipped(transaction.messageId, transaction.sender, transaction.timestamp, transaction.rawBody)) {
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
     * Synchronizes manual expenses from manual_expenses.json into Room.
     * Reconciles any deleted manual expenses.
     */
    private suspend fun syncManualExpensesFromFile() {
        val manualExpenses = fileManager.getManualExpenses()
        val currentFileIds = manualExpenses.map { it.id }.toSet()

        // Remove any manual entries from Room that were deleted from the file
        val existingDbManualIds = transactionDao.getAllManualIds()
        for (dbId in existingDbManualIds) {
            if (!currentFileIds.contains(dbId)) {
                transactionDao.deleteByManualId(dbId)
            }
        }

        // Upsert all manual expenses from the file
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

    // ── Manual Expenses API ───────────────────────────────────────────────

    suspend fun addManualExpense(expense: ManualExpense) = withContext(Dispatchers.IO) {
        fileManager.saveManualExpense(expense)
        val transaction = expense.toTransaction()
        transactionDao.insert(TransactionEntity.fromDomain(transaction))
    }

    suspend fun updateManualExpense(expense: ManualExpense) = withContext(Dispatchers.IO) {
        fileManager.saveManualExpense(expense)
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
        fileManager.deleteManualExpense(manualId)
        transactionDao.deleteByManualId(manualId)
    }

    fun getManualExpenses(): List<ManualExpense> {
        return fileManager.getManualExpenses()
    }

    // ── Skipped Transactions API ──────────────────────────────────────────

    suspend fun skipTransaction(transaction: Transaction, reason: String = "User skipped") = withContext(Dispatchers.IO) {
        val skipped = SkippedTransaction.fromTransaction(transaction, reason)
        fileManager.addSkippedTransaction(skipped)
        _skippedTransactions.value = fileManager.getSkippedTransactions()

        if (transaction.messageId != 0L) {
            transactionDao.deleteByMessageId(transaction.messageId)
        }
        transactionDao.deleteById(transaction.id)
    }

    suspend fun unskipTransaction(skipped: SkippedTransaction) = withContext(Dispatchers.IO) {
        fileManager.removeSkippedTransaction(skipped)
        _skippedTransactions.value = fileManager.getSkippedTransactions()
        // Re-sync inbox so this transaction is immediately restored to active log
        syncTransactionsFromSms()
    }

    fun getSkippedTransactions(): List<SkippedTransaction> {
        return fileManager.getSkippedTransactions()
    }

    // ── Message Templates API ─────────────────────────────────────────────

    fun getMessageTemplates(): List<MessageTemplate> {
        return fileManager.getMessageTemplates()
    }

    suspend fun saveMessageTemplate(template: MessageTemplate) = withContext(Dispatchers.IO) {
        fileManager.saveMessageTemplate(template)
        _messageTemplates.value = fileManager.getMessageTemplates()
    }

    suspend fun deleteMessageTemplate(id: String) = withContext(Dispatchers.IO) {
        fileManager.deleteMessageTemplate(id)
        _messageTemplates.value = fileManager.getMessageTemplates()
    }

    suspend fun toggleMessageTemplate(id: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        fileManager.toggleTemplate(id, isEnabled)
        _messageTemplates.value = fileManager.getMessageTemplates()
    }

    // ── Queries & Stats ───────────────────────────────────────────────────

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

    suspend fun syncMonitoredBanksWithFile() = withContext(Dispatchers.IO) {
        try {
            val fileBanks = fileManager.getMonitoredBanks()
            val fileSenderIds = fileBanks.map { it.senderId.lowercase() }.toSet()

            // Remove senders from Room that are no longer in monitored_banks.json
            val allDbSenders = bankSenderDao.getAllSendersSync()
            for (dbSender in allDbSenders) {
                if (!fileSenderIds.contains(dbSender.senderId.lowercase())) {
                    bankSenderDao.delete(dbSender)
                }
            }

            // Upsert all senders from monitored_banks.json
            for (bank in fileBanks) {
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
            System.err.println("Error syncing monitored banks: ${e.message}")
        }
    }

    suspend fun refreshFromFiles() = withContext(Dispatchers.IO) {
        val templates = fileManager.getMessageTemplates()
        _messageTemplates.value = templates
        val skipped = fileManager.getSkippedTransactions()
        _skippedTransactions.value = skipped
        syncManualExpensesFromFile()
        syncMonitoredBanksWithFile()
    }

    suspend fun setSenderMonitored(senderId: String, isMonitored: Boolean) = withContext(Dispatchers.IO) {
        bankSenderDao.setMonitored(senderId, isMonitored)
        fileManager.toggleMonitoredBank(senderId, isMonitored)
    }

    suspend fun addCustomSender(sender: BankSender) = withContext(Dispatchers.IO) {
        bankSenderDao.insert(BankSenderEntity.fromDomain(sender))
        fileManager.saveMonitoredBank(sender)
    }

    suspend fun updateSender(oldSenderId: String, updated: BankSender) = withContext(Dispatchers.IO) {
        if (!oldSenderId.equals(updated.senderId, ignoreCase = true)) {
            val oldEntity = bankSenderDao.getBySenderId(oldSenderId)
            if (oldEntity != null) {
                bankSenderDao.delete(oldEntity)
            }
            bankSenderDao.insert(BankSenderEntity.fromDomain(updated))
        } else {
            bankSenderDao.update(BankSenderEntity.fromDomain(updated))
        }
        fileManager.updateMonitoredBank(oldSenderId, updated)
    }

    suspend fun deleteSender(sender: BankSender) = withContext(Dispatchers.IO) {
        val entity = bankSenderDao.getBySenderId(sender.senderId)
        if (entity != null) {
            bankSenderDao.delete(entity)
        }
        fileManager.deleteMonitoredBank(sender.senderId)
    }

    fun getStorageDirectoryPath(): String = fileManager.getStorageDirectoryPath()

    fun isPublicStorageActive(): Boolean = fileManager.isPublicStorageActive()

    suspend fun clearAllPersistenceFiles() = withContext(Dispatchers.IO) {
        fileManager.clearAllFiles()
        _skippedTransactions.value = emptyList()
        _messageTemplates.value = fileManager.getMessageTemplates()
        // Re-sync monitored banks to fresh defaults
        syncMonitoredBanksWithFile()
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

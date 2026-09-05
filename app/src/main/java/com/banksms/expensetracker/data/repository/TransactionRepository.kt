package com.banksms.expensetracker.data.repository

import com.banksms.expensetracker.data.local.dao.BankSenderDao
import com.banksms.expensetracker.data.local.dao.TransactionDao
import com.banksms.expensetracker.data.local.entity.BankSenderEntity
import com.banksms.expensetracker.data.local.entity.TransactionEntity
import com.banksms.expensetracker.data.model.*
import com.banksms.expensetracker.data.parser.BankSmsParser
import com.banksms.expensetracker.data.reader.DiscoveredSender
import com.banksms.expensetracker.data.reader.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SyncResult(
    val messagesScanned: Int,
    val transactionsImported: Int,
    val errors: List<String> = emptyList()
)

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val bankSenderDao: BankSenderDao,
    private val smsReader: SmsReader
) {

    /**
     * Processes an incoming real-time SMS from BroadcastReceiver with full deduplication.
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

        val parsed = BankSmsParser.parse(body, sender) ?: return@withContext false

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
     * Scans the SMS inbox for all monitored bank senders and imports new transactions into Room DB.
     * Deduplicates against existing transactions to prevent double-counting.
     */
    suspend fun syncTransactionsFromSms(): SyncResult = withContext(Dispatchers.IO) {
        try {
            // Clean up any historical duplicate transactions first
            transactionDao.deleteDuplicates()

            val monitored = bankSenderDao.getMonitoredSendersSync()
            if (monitored.isEmpty()) {
                return@withContext SyncResult(0, 0, listOf("No monitored bank senders configured. Please enable banks in Settings."))
            }

            val monitoredSenderIds = monitored.map { it.senderId }.toSet()
            val parsedTransactions = smsReader.readBankMessages(monitoredSenderIds)

            if (parsedTransactions.isEmpty()) {
                return@withContext SyncResult(0, 0)
            }

            var newlyImported = 0
            for (transaction in parsedTransactions) {
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

    suspend fun setSenderMonitored(senderId: String, isMonitored: Boolean) {
        bankSenderDao.setMonitored(senderId, isMonitored)
    }

    suspend fun addCustomSender(sender: BankSender) {
        bankSenderDao.insert(BankSenderEntity.fromDomain(sender))
    }

    suspend fun deleteSender(sender: BankSender) {
        bankSenderDao.delete(BankSenderEntity.fromDomain(sender))
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

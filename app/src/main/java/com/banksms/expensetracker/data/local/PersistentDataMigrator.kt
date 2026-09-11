package com.banksms.expensetracker.data.local

import android.util.Log
import com.banksms.expensetracker.data.file.ExpenseFileManager
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.local.dao.ManualExpenseDao
import com.banksms.expensetracker.data.local.dao.MessageTemplateDao
import com.banksms.expensetracker.data.local.dao.MonitoredBankDao
import com.banksms.expensetracker.data.local.dao.SkippedTransactionDao
import com.banksms.expensetracker.data.local.entity.ManualExpenseEntity
import com.banksms.expensetracker.data.local.entity.MessageTemplateEntity
import com.banksms.expensetracker.data.local.entity.MonitoredBankEntity
import com.banksms.expensetracker.data.local.entity.SkippedTransactionEntity
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.MessageTemplate

/**
 * Result of a single JSON -> DB migration pass. Counts only rows actually
 * imported (rows that were already present are not counted).
 */
data class MigrationResult(
    val templatesImported: Int = 0,
    val expensesImported: Int = 0,
    val skippedImported: Int = 0,
    val banksImported: Int = 0,
    val sourceDataFound: Boolean = false
) {
    val totalImported: Int
        get() = templatesImported + expensesImported + skippedImported + banksImported

    override fun toString(): String =
        "MigrationResult(templates=$templatesImported, expenses=$expensesImported, " +
            "skipped=$skippedImported, banks=$banksImported, sourceDataFound=$sourceDataFound)"
}

/**
 * Abstraction over the legacy JSON filesystem backup so the migrator can be
 * unit tested against in-memory sources.
 */
interface JsonDataSource {
    fun getJsonMessageTemplates(): List<MessageTemplate>
    fun getJsonManualExpenses(): List<ManualExpense>
    fun getJsonSkippedTransactions(): List<SkippedTransaction>
    fun getJsonMonitoredBanks(): List<BankSender>
}

/** Adapter that reads the legacy JSON files through [ExpenseFileManager]. */
class FileJsonDataSource(private val fileManager: ExpenseFileManager) : JsonDataSource {
    override fun getJsonMessageTemplates(): List<MessageTemplate> = fileManager.getMessageTemplates()
    override fun getJsonManualExpenses(): List<ManualExpense> = fileManager.getManualExpenses()
    override fun getJsonSkippedTransactions(): List<SkippedTransaction> = fileManager.getSkippedTransactions()
    override fun getJsonMonitoredBanks(): List<BankSender> = fileManager.getMonitoredBanks()
}

/**
 * Idempotent, safe migrator that merges legacy JSON backup files into the
 * persistent Room database.
 *
 * It is intentionally safe to run more than once (and is re-run on every DB
 * open until every source has been consumed):
 *  - templates are matched by their primary id,
 *  - manual expenses by their primary id,
 *  - monitored banks by their senderId,
 *  - skipped transactions by their natural key (messageId / sender+body /
 *    sender+amount+time window), and are always inserted with a stable
 *    unique primary key so they never collide with each other.
 *
 * Existing rows are never overwritten; the DB remains authoritative.
 */
class PersistentDataMigrator(
    private val templateDao: MessageTemplateDao,
    private val expenseDao: ManualExpenseDao,
    private val skippedDao: SkippedTransactionDao,
    private val bankDao: MonitoredBankDao,
    private val source: JsonDataSource
) {

    suspend fun migrate(): MigrationResult {
        var templates = 0
        var expenses = 0
        var skipped = 0
        var banks = 0
        var found = false

        // ── Message templates ──
        val jsonTemplates = source.getJsonMessageTemplates()
        if (jsonTemplates.isNotEmpty()) {
            found = true
            for (template in jsonTemplates) {
                if (templateDao.getById(template.id) == null) {
                    templateDao.upsert(MessageTemplateEntity.fromDomain(template))
                    templates++
                }
            }
        }

        // ── Manual expenses ──
        val jsonExpenses = source.getJsonManualExpenses()
        if (jsonExpenses.isNotEmpty()) {
            found = true
            for (expense in jsonExpenses) {
                if (expenseDao.getById(expense.id) == null) {
                    expenseDao.upsert(ManualExpenseEntity.fromDomain(expense))
                    expenses++
                }
            }
        }

        // ── Skipped transactions ──
        val jsonSkipped = source.getJsonSkippedTransactions()
        if (jsonSkipped.isNotEmpty()) {
            found = true
            for (s in jsonSkipped) {
                if (skippedDao.findExisting(s.originalMessageId, s.sender, s.rawBody, s.amount, s.timestamp) == null) {
                    // Assign a stable unique id so rows never collapse into a
                    // single id=0 bucket (autoGenerate + REPLACE pitfall).
                    skippedDao.insert(SkippedTransactionEntity.fromDomain(s).copy(id = stableSkippedId(s)))
                    skipped++
                }
            }
        }

        // ── Monitored banks ──
        val jsonBanks = source.getJsonMonitoredBanks()
        if (jsonBanks.isNotEmpty()) {
            found = true
            for (bank in jsonBanks) {
                if (bankDao.getBySenderId(bank.senderId) == null) {
                    bankDao.upsert(MonitoredBankEntity.fromDomain(bank))
                    banks++
                }
            }
        }

        return MigrationResult(templates, expenses, skipped, banks, found)
    }

    companion object {
        private const val TAG = "PersistentDataMigrator"

        /**
         * Deterministic positive Long derived from a skipped transaction's
         * natural key, so the same skip always maps to the same row id and
         * distinct skips never collide.
         */
        fun stableSkippedId(s: SkippedTransaction): Long {
            val key = "${s.originalMessageId}|${s.sender}|${s.amount}|${s.timestamp}|${s.rawBody}"
            val h1 = key.hashCode().toLong() and 0x3FFFFFFFL
            val h2 = key.fold(1L) { acc, c -> acc * 31L + c.toLong() } and 0x3FFFFFFFL
            val combined = h1 or (h2 shl 30)
            return if (combined <= 0L) -combined + 1L else combined
        }

        fun logMigrationFailure(message: String, t: Throwable? = null) {
            Log.e(TAG, message, t)
        }
    }
}
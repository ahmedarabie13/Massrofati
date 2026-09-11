package com.banksms.expensetracker

import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.local.JsonDataSource
import com.banksms.expensetracker.data.local.PersistentDataMigrator
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
import com.banksms.expensetracker.data.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentDataMigratorTest {

    // ── Fake DAOs ─────────────────────────────────────────────────────────

    private class FakeMessageTemplateDao : MessageTemplateDao {
        val rows = mutableListOf<MessageTemplateEntity>()
        override suspend fun upsert(template: MessageTemplateEntity): Long {
            var found = false
            rows.replaceAll { if (it.id == template.id) { found = true; template } else it }
            if (!found) rows.add(template)
            return 1L
        }

        override suspend fun upsertAll(templates: List<MessageTemplateEntity>) {
            templates.forEach { upsert(it) }
        }

        override suspend fun update(template: MessageTemplateEntity) {
            rows.replaceAll { if (it.id == template.id) template else it }
        }

        override suspend fun deleteById(id: String): Int {
            val before = rows.size
            rows.removeAll { it.id == id }
            return before - rows.size
        }

        override suspend fun deleteAll() = rows.clear()
        override suspend fun getAll(): List<MessageTemplateEntity> = rows.toList()
        override suspend fun getEnabled(): List<MessageTemplateEntity> = rows.filter { it.isEnabled }
        override suspend fun getById(id: String): MessageTemplateEntity? = rows.firstOrNull { it.id == id }
        override suspend fun toggleEnabled(id: String, isEnabled: Boolean, updatedAt: Long) {
            val i = rows.indexOfFirst { it.id == id }
            if (i < 0) return
            rows[i] = rows[i].copy(isEnabled = isEnabled, updatedAt = updatedAt)
        }

        override suspend fun count(): Int = rows.size
    }

    private class FakeManualExpenseDao : ManualExpenseDao {
        val rows = mutableListOf<ManualExpenseEntity>()
        override suspend fun upsert(expense: ManualExpenseEntity): Long {
            var found = false
            rows.replaceAll { if (it.id == expense.id) { found = true; expense } else it }
            if (!found) rows.add(expense)
            return 1L
        }

        override suspend fun upsertAll(expenses: List<ManualExpenseEntity>) = expenses.forEach { upsert(it) }
        override suspend fun update(expense: ManualExpenseEntity) {
            rows.replaceAll { if (it.id == expense.id) expense else it }
        }

        override suspend fun deleteById(id: String): Int {
            val before = rows.size
            rows.removeAll { it.id == id }
            return before - rows.size
        }

        override suspend fun deleteAll() = rows.clear()
        override suspend fun getAll(): List<ManualExpenseEntity> = rows.toList()
        override suspend fun getById(id: String): ManualExpenseEntity? = rows.firstOrNull { it.id == id }
        override suspend fun count(): Int = rows.size
    }

    private class FakeSkippedTransactionDao : SkippedTransactionDao {
        val rows = mutableListOf<SkippedTransactionEntity>()
        override suspend fun insert(skipped: SkippedTransactionEntity): Long {
            var found = false
            rows.replaceAll { if (it.id == skipped.id) { found = true; skipped } else it }
            if (!found) rows.add(skipped)
            return 1L
        }

        override suspend fun insertAll(skipped: List<SkippedTransactionEntity>) = skipped.forEach { insert(it) }
        override suspend fun deleteByMessageId(messageId: Long): Int {
            val before = rows.size
            rows.removeAll { it.originalMessageId == messageId && it.originalMessageId != 0L }
            return before - rows.size
        }

        override suspend fun deleteBySenderAndBody(sender: String, rawBody: String): Int {
            val before = rows.size
            rows.removeAll { it.sender == sender && it.rawBody == rawBody && it.rawBody != "" }
            return before - rows.size
        }

        override suspend fun deleteBySenderAmountTimestamp(sender: String, amount: Double, timestamp: Long): Int {
            val before = rows.size
            rows.removeAll { it.sender == sender && it.amount == amount && Math.abs(it.timestamp - timestamp) < 60000 }
            return before - rows.size
        }

        override suspend fun deleteAll() = rows.clear()
        override suspend fun getAll(): List<SkippedTransactionEntity> = rows.toList()

        override suspend fun findExisting(
            originalMessageId: Long,
            sender: String,
            rawBody: String,
            amount: Double,
            timestamp: Long
        ): SkippedTransactionEntity? = rows.firstOrNull { row ->
            (originalMessageId != 0L && row.originalMessageId == originalMessageId) ||
                (row.rawBody != "" && row.sender == sender && row.rawBody == rawBody) ||
                (row.sender == sender && Math.abs(row.timestamp - timestamp) < 60000 &&
                    Math.abs(row.amount - amount) < 0.001)
        }

        override suspend fun countMatches(
            messageId: Long,
            sender: String,
            timestamp: Long,
            rawBody: String,
            amount: Double
        ): Int = rows.count { row ->
            (messageId != 0L && row.originalMessageId == messageId) ||
                (row.sender == sender && row.rawBody == rawBody && rawBody != "" && row.rawBody != "") ||
                (row.sender == sender && Math.abs(row.timestamp - timestamp) < 60000)
        }

        override suspend fun count(): Int = rows.size
    }

    private class FakeMonitoredBankDao : MonitoredBankDao {
        val rows = mutableListOf<MonitoredBankEntity>()
        override suspend fun upsert(bank: MonitoredBankEntity): Long {
            var found = false
            rows.replaceAll { if (it.senderId == bank.senderId) { found = true; bank } else it }
            if (!found) rows.add(bank)
            return 1L
        }

        override suspend fun upsertAll(banks: List<MonitoredBankEntity>) = banks.forEach { upsert(it) }
        override suspend fun update(bank: MonitoredBankEntity) {
            rows.replaceAll { if (it.senderId == bank.senderId) bank else it }
        }

        override suspend fun deleteBySenderId(senderId: String): Int {
            val before = rows.size
            rows.removeAll { it.senderId == senderId }
            return before - rows.size
        }

        override suspend fun deleteAll() = rows.clear()
        override suspend fun getAll(): List<MonitoredBankEntity> = rows.toList()
        override suspend fun getMonitored(): List<MonitoredBankEntity> = rows.filter { it.isMonitored }
        override suspend fun getBySenderId(senderId: String): MonitoredBankEntity? = rows.firstOrNull { it.senderId == senderId }
        override suspend fun toggleMonitored(senderId: String, isMonitored: Boolean) {
            val i = rows.indexOfFirst { it.senderId == senderId }
            if (i < 0) return
            rows[i] = rows[i].copy(isMonitored = isMonitored)
        }

        override suspend fun count(): Int = rows.size
    }

    // ── Fake source ───────────────────────────────────────────────────────

    private class FakeSource(
        private val templates: List<MessageTemplate> = emptyList(),
        private val expenses: List<ManualExpense> = emptyList(),
        private val skipped: List<SkippedTransaction> = emptyList(),
        private val banks: List<BankSender> = emptyList()
    ) : JsonDataSource {
        override fun getJsonMessageTemplates(): List<MessageTemplate> = templates
        override fun getJsonManualExpenses(): List<ManualExpense> = expenses
        override fun getJsonSkippedTransactions(): List<SkippedTransaction> = skipped
        override fun getJsonMonitoredBanks(): List<BankSender> = banks
    }

    private data class Harness(
        val migrator: PersistentDataMigrator,
        val templates: FakeMessageTemplateDao,
        val expenses: FakeManualExpenseDao,
        val skipped: FakeSkippedTransactionDao,
        val banks: FakeMonitoredBankDao
    )

    private fun harness(source: JsonDataSource): Harness {
        val t = FakeMessageTemplateDao()
        val e = FakeManualExpenseDao()
        val s = FakeSkippedTransactionDao()
        val b = FakeMonitoredBankDao()
        return Harness(
            migrator = PersistentDataMigrator(t, e, s, b, source),
            templates = t,
            expenses = e,
            skipped = s,
            banks = b
        )
    }

    private fun template(id: String, name: String = id) = MessageTemplate(
        id = id,
        name = name,
        sender = "SNB",
        pattern = "Purchase with card {card} of {currency} {amount} at {merchant}",
        defaultType = TransactionType.EXPENSE,
        defaultCurrency = "SAR",
        defaultCategory = "General",
        isEnabled = true
    )

    private fun expense(id: String, amount: Double) = ManualExpense(
        id = id,
        amount = amount,
        currency = "SAR",
        type = TransactionType.EXPENSE,
        category = "Dining",
        merchant = "Shawarma House",
        paymentMethod = "Cash",
        timestamp = 1727000000000L + id.hashCode(),
        note = "note $id"
    )

    private fun skipped(index: Int, sender: String = "AlRajhiBank", msgId: Long = 1000L + index) = SkippedTransaction(
        originalMessageId = msgId,
        sender = sender,
        amount = 50.0 + index,
        currency = "SAR",
        type = TransactionType.EXPENSE,
        merchant = "Amazon",
        category = "Shopping",
        rawBody = "شراء عبر الإنترنت $index",
        timestamp = 1727000000000L + index * 1000L,
        skippedAt = 1727000000500L + index * 1000L,
        reason = "User skipped"
    )

    private fun bank(senderId: String, name: String = senderId) = BankSender(
        senderId = senderId,
        displayName = name,
        isMonitored = true
    )

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun migratesAllLegacyDataIntoDatabase() = runBlocking {
        val source = FakeSource(
            templates = listOf(template("t1", "Alinma POS"), template("t2", "SNB Purchase")),
            expenses = listOf(expense("e1", 50.0), expense("e2", 120.5)),
            skipped = listOf(skipped(1), skipped(2), skipped(3)),
            banks = listOf(bank("alinma", "Alinma Bank"), bank("snb", "SNB"))
        )
        val h = harness(source)

        val result = h.migrator.migrate()

        assertEquals(2, result.templatesImported)
        assertEquals(2, result.expensesImported)
        assertEquals(3, result.skippedImported)
        assertEquals(2, result.banksImported)
        assertTrue(result.sourceDataFound)
        assertEquals(2, h.templates.count())
        assertEquals(2, h.expenses.count())
        assertEquals(3, h.skipped.count())
        assertEquals(2, h.banks.count())
    }

    @Test
    fun migrationIsIdempotent() = runBlocking {
        val source = FakeSource(
            templates = listOf(template("t1"), template("t2"), template("t3")),
            expenses = listOf(expense("e1", 10.0), expense("e2", 20.0)),
            skipped = listOf(skipped(1), skipped(2), skipped(3), skipped(4)),
            banks = listOf(bank("alinma"), bank("alrajhibank"))
        )
        val h = harness(source)

        h.migrator.migrate()
        val second = h.migrator.migrate()

        assertEquals(0, second.totalImported)
        assertEquals(3, h.templates.count())
        assertEquals(2, h.expenses.count())
        assertEquals(4, h.skipped.count())
        assertEquals(2, h.banks.count())
    }

    @Test
    fun migrationKeepsExistingRowsAsAuthoritative() = runBlocking {
        val existing = MessageTemplateEntity.fromDomain(template("t1", "DB Version"))
        val h = harness(FakeSource(templates = listOf(template("t1", "JSON Version"))))
        h.templates.upsert(existing)

        h.migrator.migrate()

        assertEquals("DB Version", h.templates.getById("t1")!!.name)
    }

    @Test
    fun multipleSkippedTransactionsAllSurviveMigration() = runBlocking {
        val source = FakeSource(skipped = listOf(skipped(1), skipped(2), skipped(3)))
        val h = harness(source)

        h.migrator.migrate()

        val ids = h.skipped.rows.map { it.id }
        assertEquals(3, ids.size)
        assertEquals(3, ids.distinct().size)
        assertTrue(ids.all { it != 0L })
    }

    @Test
    fun stableSkippedIdIsDeterministicAndDistinct() {
        val a = skipped(1)
        val b = skipped(2)
        assertEquals(
            PersistentDataMigrator.stableSkippedId(a),
            PersistentDataMigrator.stableSkippedId(skipped(1, msgId = a.originalMessageId))
        )
        assertTrue(PersistentDataMigrator.stableSkippedId(a) != PersistentDataMigrator.stableSkippedId(b))
        assertTrue(PersistentDataMigrator.stableSkippedId(a) > 0L)
        assertTrue(PersistentDataMigrator.stableSkippedId(b) > 0L)
    }

    @Test
    fun migrationWithEmptySourceIsNoOp() = runBlocking {
        val h = harness(FakeSource())
        val result = h.migrator.migrate()
        assertEquals(0, result.totalImported)
        assertEquals(false, result.sourceDataFound)
        assertEquals(0, h.templates.count())
        assertEquals(0, h.expenses.count())
        assertEquals(0, h.skipped.count())
        assertEquals(0, h.banks.count())
    }
}
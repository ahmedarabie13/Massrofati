package com.banksms.expensetracker

import com.banksms.expensetracker.data.file.ExpenseFileManager
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExpenseFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fileManager: ExpenseFileManager

    @Before
    fun setup() {
        fileManager = ExpenseFileManager(baseDirectory = tempFolder.root)
    }

    @Test
    fun saveAndReadManualExpense() {
        val expense = ManualExpense(
            id = "man-1",
            amount = 45.5,
            currency = "SAR",
            category = "Food & Dining",
            merchant = "AlBaik",
            paymentMethod = "Cash",
            note = "Dinner"
        )

        fileManager.saveManualExpense(expense)

        val list = fileManager.getManualExpenses()
        assertEquals(1, list.size)
        assertEquals("man-1", list[0].id)
        assertEquals(45.5, list[0].amount, 0.001)
        assertEquals("AlBaik", list[0].merchant)
        assertEquals("Cash", list[0].paymentMethod)

        // Convert to transaction
        val tx = list[0].toTransaction()
        assertTrue(tx.isManual)
        assertEquals("man-1", tx.manualId)
        assertTrue(tx.messageId < 0) // Negative ID to avoid collision
    }

    @Test
    fun updateManualExpense() {
        val expense = ManualExpense(
            id = "man-1",
            amount = 50.0,
            merchant = "Initial Store"
        )
        fileManager.saveManualExpense(expense)

        val updated = expense.copy(amount = 75.0, merchant = "Updated Store")
        fileManager.saveManualExpense(updated)

        val list = fileManager.getManualExpenses()
        assertEquals(1, list.size)
        assertEquals(75.0, list[0].amount, 0.001)
        assertEquals("Updated Store", list[0].merchant)
    }

    @Test
    fun deleteManualExpensePermanently() {
        val e1 = ManualExpense(id = "man-1", amount = 20.0)
        val e2 = ManualExpense(id = "man-2", amount = 40.0)
        fileManager.saveManualExpense(e1)
        fileManager.saveManualExpense(e2)

        assertEquals(2, fileManager.getManualExpenses().size)

        val removed = fileManager.deleteManualExpense("man-1")
        assertTrue(removed)

        val remaining = fileManager.getManualExpenses()
        assertEquals(1, remaining.size)
        assertEquals("man-2", remaining[0].id)
    }

    @Test
    fun addAndCheckSkippedTransaction() {
        val skipped = SkippedTransaction(
            originalMessageId = 555L,
            sender = "AlRajhiBank",
            amount = 412.1,
            currency = "SAR",
            type = TransactionType.EXPENSE,
            rawBody = "شراء إنترنت بـSR 412.1...",
            timestamp = 1725051240000L
        )

        fileManager.addSkippedTransaction(skipped)

        val list = fileManager.getSkippedTransactions()
        assertEquals(1, list.size)
        assertEquals(555L, list[0].originalMessageId)

        // Check isSkipped by messageId
        assertTrue(fileManager.isSkipped(555L, "AlRajhiBank", 1725051240000L, ""))

        // Check isSkipped by rawBody
        assertTrue(fileManager.isSkipped(999L, "AlRajhiBank", 0L, "شراء إنترنت بـSR 412.1..."))

        // Other non-skipped message should be false
        assertFalse(fileManager.isSkipped(888L, "Alinma", 1725051240000L, "Different message"))
    }

    @Test
    fun unskipTransaction() {
        val skipped = SkippedTransaction(
            originalMessageId = 777L,
            sender = "Alinma",
            amount = 100.0,
            timestamp = 1725051240000L
        )

        fileManager.addSkippedTransaction(skipped)
        assertEquals(1, fileManager.getSkippedTransactions().size)

        val removed = fileManager.removeSkippedTransaction(skipped)
        assertTrue(removed)
        assertEquals(0, fileManager.getSkippedTransactions().size)
        assertFalse(fileManager.isSkipped(777L, "Alinma", 1725051240000L, ""))
    }

    @Test
    fun testMessageTemplatePersistenceAndToggle() {
        // Initial call seeds default templates
        val initial = fileManager.getMessageTemplates()
        assertTrue(initial.isNotEmpty())

        val custom = com.banksms.expensetracker.data.model.MessageTemplate(
            id = "custom_test_1",
            name = "Custom Template Test",
            sender = "SNB",
            pattern = "Amount: {amount} SAR",
            isEnabled = true
        )

        fileManager.saveMessageTemplate(custom)
        val afterSave = fileManager.getMessageTemplates()
        val found = afterSave.find { it.id == "custom_test_1" }
        assertNotNull(found)
        assertEquals("Custom Template Test", found!!.name)
        assertTrue(found.isEnabled)

        // Toggle enabled to false
        fileManager.toggleTemplate("custom_test_1", false)
        val afterToggle = fileManager.getMessageTemplates()
        val foundToggled = afterToggle.find { it.id == "custom_test_1" }
        assertNotNull(foundToggled)
        assertFalse(foundToggled!!.isEnabled)

        // Delete template
        val deleted = fileManager.deleteMessageTemplate("custom_test_1")
        assertTrue(deleted)
        val afterDelete = fileManager.getMessageTemplates()
        assertNull(afterDelete.find { it.id == "custom_test_1" })
    }

    @Test
    fun testMonitoredBanksPersistence() {
        // Initial call seeds default banks
        val initial = fileManager.getMonitoredBanks()
        assertTrue(initial.size >= 3)
        assertTrue(initial.any { it.senderId.equals("alinma", ignoreCase = true) })
        assertTrue(initial.any { it.senderId.equals("alrajhibank", ignoreCase = true) })
        assertTrue(initial.any { it.senderId.equals("alinmapay", ignoreCase = true) })

        // Add custom bank
        val customBank = com.banksms.expensetracker.data.model.BankSender(
            senderId = "SNB",
            displayName = "Saudi National Bank",
            isMonitored = true
        )
        fileManager.saveMonitoredBank(customBank)

        val afterAdd = fileManager.getMonitoredBanks()
        val found = afterAdd.find { it.senderId == "SNB" }
        assertNotNull(found)
        assertEquals("Saudi National Bank", found!!.displayName)
        assertTrue(found.isMonitored)

        // Update custom bank
        val updatedBank = found.copy(displayName = "SNB AlAhli")
        fileManager.updateMonitoredBank("SNB", updatedBank)
        val afterUpdate = fileManager.getMonitoredBanks()
        assertEquals("SNB AlAhli", afterUpdate.find { it.senderId == "SNB" }?.displayName)

        // Toggle monitored state
        fileManager.toggleMonitoredBank("SNB", false)
        assertFalse(fileManager.getMonitoredBanks().find { it.senderId == "SNB" }!!.isMonitored)

        // Delete bank
        val removed = fileManager.deleteMonitoredBank("SNB")
        assertTrue(removed)
        assertNull(fileManager.getMonitoredBanks().find { it.senderId == "SNB" })
    }

    @Test
    fun testClearAllFiles() {
        // Seed data in all 4 files
        fileManager.saveManualExpense(ManualExpense(amount = 100.0))
        fileManager.addSkippedTransaction(SkippedTransaction(originalMessageId = 123L, sender = "Test", amount = 50.0))
        fileManager.saveMessageTemplate(com.banksms.expensetracker.data.model.MessageTemplate(name = "Test", pattern = "{amount}"))
        fileManager.saveMonitoredBank(com.banksms.expensetracker.data.model.BankSender(senderId = "TestBank", displayName = "Test Bank"))

        assertTrue(fileManager.manualExpensesFile.exists())
        assertTrue(fileManager.skippedTransactionsFile.exists())
        assertTrue(fileManager.messageTemplatesFile.exists())
        assertTrue(fileManager.monitoredBanksFile.exists())

        // Clear all files
        val wiped = fileManager.clearAllFiles()
        assertTrue(wiped)

        assertFalse(fileManager.manualExpensesFile.exists())
        assertFalse(fileManager.skippedTransactionsFile.exists())
        assertFalse(fileManager.messageTemplatesFile.exists())
        assertFalse(fileManager.monitoredBanksFile.exists())
    }

    @Test
    fun testTemplatesReloadWhenFileModifiedOnDisk() {
        // Initial call seeds templates
        val initial = fileManager.getMessageTemplates()
        assertTrue(initial.isNotEmpty())

        // Simulate external edit to message_templates.json on disk
        val customTemplate = com.banksms.expensetracker.data.model.MessageTemplate(
            id = "disk_edit_tpl",
            name = "Direct Disk Edit Template",
            pattern = "Transfer {amount} {currency}"
        )
        val list = listOf(customTemplate)
        val jsonArray = org.json.JSONArray()
        list.forEach { jsonArray.put(it.toJsonObject()) }
        fileManager.messageTemplatesFile.writeText(jsonArray.toString(2))

        // Re-read via getMessageTemplates()
        val reloaded = fileManager.getMessageTemplates()
        assertEquals(1, reloaded.size)
        assertEquals("disk_edit_tpl", reloaded[0].id)
        assertEquals("Direct Disk Edit Template", reloaded[0].name)
    }
}

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
}

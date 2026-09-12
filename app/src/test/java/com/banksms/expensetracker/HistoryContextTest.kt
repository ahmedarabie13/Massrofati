package com.banksms.expensetracker

import com.banksms.expensetracker.data.llm.HistoryContext
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class HistoryContextTest {

    // 2026-09-11 12:00 local; HistoryContext formats Locale.US in the same
    // default timezone, so they agree.
    private val sep11: Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, 2026)
        set(java.util.Calendar.MONTH, java.util.Calendar.SEPTEMBER)
        set(java.util.Calendar.DAY_OF_MONTH, 11)
        set(java.util.Calendar.HOUR_OF_DAY, 12)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun tx(
        merchant: String?,
        amount: Double,
        type: TransactionType = TransactionType.EXPENSE,
        ts: Long = sep11
    ) = Transaction(
        sender = "Alinma",
        type = type,
        amount = amount,
        currency = "SAR",
        merchant = merchant,
        category = "General",
        timestamp = ts
    )

    @Test
    fun rowFormatIsCompactSingleLine() {
        val out = HistoryContext.formatRow(tx("DAWAHI ALR*", 3.0))
        assertEquals("2026-09-11 | DAWAHI ALR* | EXPENSE 3.0 SAR | General | Alinma", out)
        assertFalse(out.contains('\n'))
    }

    @Test
    fun sectionsPresent() {
        val out = HistoryContext.format(listOf(tx("DAWAHI ALR*", 3.0)))
        assertTrue(out.contains("## Recent transactions"))
        assertTrue(out.contains("## All-time totals per merchant"))
        assertTrue(out.contains("## Monthly totals"))
        assertTrue(out.contains("DAWAHI ALR*: 3.0 (1)"))
        assertTrue(out.contains("2026-09: 3.0 / 0.0"))
    }

    @Test
    fun merchantsSortedByTotal() {
        val rows = listOf(
            tx("SMALL", 1.0),
            tx("BIG", 100.0),
            tx("BIG", 50.0)
        )
        val out = HistoryContext.format(rows)
        assertTrue(out.indexOf("BIG: 150.0") < out.indexOf("SMALL: 1.0"))
    }

    @Test
    fun hugeHistoryShrinksIntoBudget() {
        val rows = (1..3000).map { tx("MERCHANT $it", 1.0 + (it % 50)) }
        val out = HistoryContext.format(rows)
        assertTrue(
            "estimate ${HistoryContext.estimateTokens(out)} over budget",
            HistoryContext.estimateTokens(out) <= HistoryContext.HARD_TOKEN_BUDGET
        )
        assertTrue(out.contains("## Recent transactions"))
    }
}

package com.banksms.expensetracker.data.llm

import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.data.repository.TransactionRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Packs transaction knowledge into a ~4k-token model window (measured: a full
 * dump is 36k tokens and the runtime rejects it). Three sections:
 * 1. Recent transactions in full row detail (newest first),
 * 2. All-time totals per merchant (covers the whole history),
 * 3. Monthly expense/income totals (trends + period math).
 * Sections shrink until a chars/4 token estimate fits [HARD_TOKEN_BUDGET].
 */
object HistoryContext {

    private const val RECENT_ROWS = 50
    private const val TOP_MERCHANTS = 40
    private const val MONTHS_BACK = 12

    /** Hard ceiling with margin under the runtime's 4096-token input limit. */
    const val HARD_TOKEN_BUDGET = 3500

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    suspend fun build(repository: TransactionRepository): String =
        withContext(Dispatchers.IO) {
            format(repository.getAllTransactions())
        }

    /** Pure rendering — unit-testable. Input newest-first. */
    fun format(rows: List<Transaction>): String {
        var recent = RECENT_ROWS
        var merchants = TOP_MERCHANTS
        var months = MONTHS_BACK
        // Shrink cheapest context first until the estimate fits.
        while (true) {
            val text = render(rows, recent, merchants, months)
            if (estimateTokens(text) <= HARD_TOKEN_BUDGET) return text
            when {
                months > 6 -> months = 6
                merchants > 20 -> merchants = 20
                recent > 30 -> recent = 30
                merchants > 10 -> merchants = 10
                else -> return text // Give up shrinking; engine will reject loudly.
            }
        }
    }

    fun estimateTokens(text: String): Int = text.length / 4

    private fun render(
        rows: List<Transaction>,
        recent: Int,
        merchantCount: Int,
        months: Int
    ): String = buildString {
        append("SPENDING DATA (${rows.size} transactions total). ")
        append("Section 1 lists the newest transactions in full detail. ")
        append("Section 2 has ALL-TIME totals per merchant (whole history). ")
        append("Section 3 has monthly totals. Use section 2/3 for anything older ")
        append("than section 1.\n")

        append("## Recent transactions (newest first). Columns: date | merchant | type amount currency | category | bank\n")
        rows.take(recent).forEach { append(formatRow(it)).append('\n') }

        val expenses = rows.filter { it.type == TransactionType.EXPENSE }
        val currency = rows.firstOrNull()?.currency ?: "SAR"
        append("## All-time totals per merchant (expenses, $currency)\n")
        expenses.filter { !it.merchant.isNullOrBlank() }
            .groupBy { it.merchant!! }
            .mapValues { (_, list) -> list.sumOf { it.amount } to list.size }
            .entries.sortedByDescending { it.value.first }
            .take(merchantCount)
            .forEach { append("${it.key}: ${it.value.first} (${it.value.second})\n") }

        append("## Monthly totals ($currency, expense / income)\n")
        expenses.groupBy { monthFormat.format(Date(it.timestamp)) }
            .entries.sortedByDescending { it.key }
            .take(months)
            .forEach { (month, list) ->
                val exp = list.sumOf { it.amount }
                val inc = rows.filter {
                    it.type == TransactionType.INCOME &&
                        monthFormat.format(Date(it.timestamp)) == month
                }.sumOf { it.amount }
                append("$month: $exp / $inc\n")
            }
    }

    fun formatRow(tx: Transaction): String = buildString {
        append(dayFormat.format(Date(tx.timestamp)))
        append(" | ${tx.merchant ?: "Unknown"}")
        val sign = if (tx.type == TransactionType.INCOME) "INCOME" else "EXPENSE"
        append(" | $sign ${tx.amount} ${tx.currency}")
        append(" | ${tx.category}")
        append(" | ${tx.sender}")
        if (!tx.accountOrCard.isNullOrBlank()) append(" ${tx.accountOrCard}")
    }.trimEnd()
}

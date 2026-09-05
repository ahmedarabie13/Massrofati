package com.banksms.expensetracker.data.file

import android.content.Context
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ManualExpense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val currency: String = "SAR",
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "General",
    val merchant: String = "",
    val paymentMethod: String = "Cash", // Cash, Card, Bank Transfer, etc.
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toTransaction(): Transaction {
        // Create deterministic unique negative messageId from manualId so it never conflicts with SMS messageIds
        val stableHash = id.hashCode().toLong()
        val uniqueMsgId = if (stableHash >= 0) -stableHash - 1L else stableHash

        val displaySender = if (paymentMethod.isNotBlank()) "Manual ($paymentMethod)" else "Manual Entry"
        val displayMerchant = if (merchant.isNotBlank()) merchant else "Manual Expense"
        val bodySnippet = if (note.isNotBlank()) "Note: $note" else "Manual entry recorded on app"

        return Transaction(
            id = 0,
            messageId = uniqueMsgId,
            sender = displaySender,
            type = type,
            amount = amount,
            currency = currency,
            merchant = displayMerchant,
            accountOrCard = paymentMethod,
            availableBalance = null,
            category = category,
            timestamp = timestamp,
            rawBody = bodySnippet,
            isManual = true,
            manualId = id
        )
    }

    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("amount", amount)
        obj.put("currency", currency)
        obj.put("type", type.name)
        obj.put("category", category)
        obj.put("merchant", merchant)
        obj.put("paymentMethod", paymentMethod)
        obj.put("timestamp", timestamp)
        obj.put("note", note)
        obj.put("createdAt", createdAt)
        obj.put("updatedAt", updatedAt)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ManualExpense {
            return ManualExpense(
                id = obj.optString("id", UUID.randomUUID().toString()),
                amount = obj.optDouble("amount", 0.0),
                currency = obj.optString("currency", "SAR"),
                type = try {
                    TransactionType.valueOf(obj.optString("type", "EXPENSE"))
                } catch (e: Exception) {
                    TransactionType.EXPENSE
                },
                category = obj.optString("category", "General"),
                merchant = obj.optString("merchant", ""),
                paymentMethod = obj.optString("paymentMethod", "Cash"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                note = obj.optString("note", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

data class SkippedTransaction(
    val originalMessageId: Long,
    val sender: String,
    val amount: Double,
    val currency: String = "SAR",
    val type: TransactionType = TransactionType.EXPENSE,
    val merchant: String? = null,
    val category: String = "General",
    val rawBody: String = "",
    val timestamp: Long,
    val skippedAt: Long = System.currentTimeMillis(),
    val reason: String = "User skipped"
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("originalMessageId", originalMessageId)
        obj.put("sender", sender)
        obj.put("amount", amount)
        obj.put("currency", currency)
        obj.put("type", type.name)
        obj.put("merchant", merchant ?: "")
        obj.put("category", category)
        obj.put("rawBody", rawBody)
        obj.put("timestamp", timestamp)
        obj.put("skippedAt", skippedAt)
        obj.put("reason", reason)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): SkippedTransaction {
            return SkippedTransaction(
                originalMessageId = obj.optLong("originalMessageId", 0L),
                sender = obj.optString("sender", ""),
                amount = obj.optDouble("amount", 0.0),
                currency = obj.optString("currency", "SAR"),
                type = try {
                    TransactionType.valueOf(obj.optString("type", "EXPENSE"))
                } catch (e: Exception) {
                    TransactionType.EXPENSE
                },
                merchant = obj.optString("merchant").takeIf { !it.isNullOrBlank() },
                category = obj.optString("category", "General"),
                rawBody = obj.optString("rawBody", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                skippedAt = obj.optLong("skippedAt", System.currentTimeMillis()),
                reason = obj.optString("reason", "User skipped")
            )
        }

        fun fromTransaction(transaction: Transaction, reason: String = "User skipped"): SkippedTransaction {
            return SkippedTransaction(
                originalMessageId = transaction.messageId,
                sender = transaction.sender,
                amount = transaction.amount,
                currency = transaction.currency,
                type = transaction.type,
                merchant = transaction.merchant,
                category = transaction.category,
                rawBody = transaction.rawBody,
                timestamp = transaction.timestamp,
                skippedAt = System.currentTimeMillis(),
                reason = reason
            )
        }
    }
}

class ExpenseFileManager(
    private val context: Context? = null,
    baseDirectory: File? = null
) {

    private val dataDir: File by lazy {
        val dir = baseDirectory ?: File(context?.filesDir ?: File("."), "data")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val manualExpensesFile: File by lazy {
        File(dataDir, "manual_expenses.json")
    }

    private val skippedTransactionsFile: File by lazy {
        File(dataDir, "skipped_transactions.json")
    }

    private val lock = Any()

    // ── Manual Expenses Operations ────────────────────────────────────────

    fun getManualExpenses(): List<ManualExpense> = synchronized(lock) {
        if (!manualExpensesFile.exists()) return emptyList()
        try {
            val content = manualExpensesFile.readText()
            if (content.isBlank()) return emptyList()
            val jsonArray = JSONArray(content)
            val result = mutableListOf<ManualExpense>()
            for (i in 0 until jsonArray.length()) {
                result.add(ManualExpense.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            result.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
            emptyList()
        }
    }

    fun saveManualExpense(expense: ManualExpense): Unit = synchronized(lock) {
        val currentList = getManualExpenses().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == expense.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = expense.copy(updatedAt = System.currentTimeMillis())
        } else {
            currentList.add(0, expense)
        }
        writeManualExpenses(currentList)
    }

    fun deleteManualExpense(id: String): Boolean = synchronized(lock) {
        val currentList = getManualExpenses().toMutableList()
        val removed = currentList.removeAll { it.id == id }
        if (removed) {
            writeManualExpenses(currentList)
        }
        removed
    }

    private fun writeManualExpenses(expenses: List<ManualExpense>) {
        try {
            val jsonArray = JSONArray()
            expenses.forEach { jsonArray.put(it.toJsonObject()) }
            writeAtomically(manualExpensesFile, jsonArray.toString(2))
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
        }
    }

    // ── Skipped Transactions Operations ──────────────────────────────────

    fun getSkippedTransactions(): List<SkippedTransaction> = synchronized(lock) {
        if (!skippedTransactionsFile.exists()) return emptyList()
        try {
            val content = skippedTransactionsFile.readText()
            if (content.isBlank()) return emptyList()
            val jsonArray = JSONArray(content)
            val result = mutableListOf<SkippedTransaction>()
            for (i in 0 until jsonArray.length()) {
                result.add(SkippedTransaction.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            result.sortedByDescending { it.skippedAt }
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
            emptyList()
        }
    }

    fun addSkippedTransaction(skipped: SkippedTransaction): Unit = synchronized(lock) {
        val currentList = getSkippedTransactions().toMutableList()
        // Prevent duplicate skips
        val alreadyExists = currentList.any {
            (it.originalMessageId != 0L && it.originalMessageId == skipped.originalMessageId) ||
            (it.sender == skipped.sender && it.rawBody.isNotBlank() && it.rawBody == skipped.rawBody) ||
            (it.sender == skipped.sender && it.amount == skipped.amount && Math.abs(it.timestamp - skipped.timestamp) < 60000)
        }
        if (!alreadyExists) {
            currentList.add(0, skipped)
            writeSkippedTransactions(currentList)
        }
    }

    fun removeSkippedTransaction(skipped: SkippedTransaction): Boolean = synchronized(lock) {
        val currentList = getSkippedTransactions().toMutableList()
        val removed = currentList.removeAll {
            (skipped.originalMessageId != 0L && it.originalMessageId == skipped.originalMessageId) ||
            (it.sender == skipped.sender && it.rawBody.isNotBlank() && it.rawBody == skipped.rawBody) ||
            (it.sender == skipped.sender && it.amount == skipped.amount && Math.abs(it.timestamp - skipped.timestamp) < 60000)
        }
        if (removed) {
            writeSkippedTransactions(currentList)
        }
        removed
    }

    fun isSkipped(
        messageId: Long,
        sender: String,
        timestamp: Long,
        rawBody: String
    ): Boolean = synchronized(lock) {
        val skippedList = getSkippedTransactions()
        if (skippedList.isEmpty()) return false

        return skippedList.any { skipped ->
            (messageId != 0L && skipped.originalMessageId == messageId) ||
            (rawBody.isNotBlank() && skipped.rawBody.isNotBlank() && skipped.sender == sender && skipped.rawBody == rawBody) ||
            (skipped.sender == sender && Math.abs(skipped.timestamp - timestamp) < 60000 &&
             rawBody.contains(skipped.amount.toInt().toString()))
        }
    }

    private fun writeSkippedTransactions(skipped: List<SkippedTransaction>) {
        try {
            val jsonArray = JSONArray()
            skipped.forEach { jsonArray.put(it.toJsonObject()) }
            writeAtomically(skippedTransactionsFile, jsonArray.toString(2))
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
        }
    }

    private fun writeAtomically(targetFile: File, content: String) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        FileOutputStream(tempFile).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.flush()
        }
        if (tempFile.exists()) {
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        }
    }
}

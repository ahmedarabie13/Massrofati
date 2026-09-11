package com.banksms.expensetracker.data.file

import android.content.Context
import android.os.Environment
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.MessageTemplate
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
    val timestamp: Long = System.currentTimeMillis(),
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
    private val baseDirectory: File? = null
) {

    companion object {
        private const val TAG = "ExpenseFileManager"
    }

    /**
     * Tests if a directory is writable by creating + reading + deleting a temp file.
     */
    private fun isDirectoryWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created && !dir.exists()) return false
            }
            val testFile = File(dir, ".perm_test_${System.currentTimeMillis()}.tmp")
            testFile.writeText("ok")
            val readable = testFile.readText() == "ok"
            testFile.delete()
            readable
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Tests if a file is readable (exists, non-empty, and can be opened for reading).
     */
    private fun isFileReadable(file: File): Boolean {
        return try {
            file.exists() && file.length() > 0L && file.canRead()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Tests if a directory exists and contains at least one of our data files that can be read.
     */
    private fun isDirectoryReadable(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val filenames = listOf(
            "manual_expenses.json",
            "skipped_transactions.json",
            "message_templates.json",
            "monitored_banks.json"
        )
        return filenames.any { filename ->
            try {
                val f = File(dir, filename)
                f.exists() && f.length() > 0L && f.readText().isNotBlank()
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun getCandidateDirectories(): List<File> {
        val candidates = mutableListOf<File>()
        if (baseDirectory != null) {
            candidates.add(baseDirectory)
        }
        // 1. External Public Documents
        try {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            candidates.add(File(publicDocs, "Masari"))
        } catch (_: Throwable) {}

        // 2. Root Documents
        try {
            candidates.add(File(Environment.getExternalStorageDirectory(), "Documents/Masari"))
        } catch (_: Throwable) {}

        // 3. External Public Downloads
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            candidates.add(File(publicDownloads, "Masari"))
        } catch (_: Throwable) {}

        // 4. Root Downloads
        try {
            candidates.add(File(Environment.getExternalStorageDirectory(), "Download/Masari"))
        } catch (_: Throwable) {}

        // 5. App External Files Dir
        try {
            context?.getExternalFilesDir(null)?.let {
                candidates.add(File(it, "Masari"))
            }
        } catch (_: Throwable) {}

        // 6. App Internal Files Dir
        try {
            context?.filesDir?.let {
                candidates.add(File(it, "Masari"))
                candidates.add(File(it, "data")) // Legacy
            }
        } catch (_: Throwable) {}

        return candidates.distinctBy { it.absolutePath }
    }

    /**
     * The directory used for WRITING files. Requires write permission.
     */
    val dataDir: File
        get() {
            if (baseDirectory != null) {
                if (!baseDirectory.exists()) baseDirectory.mkdirs()
                return baseDirectory
            }

            // Try candidate directories in priority order for write capability
            for (candidateGetter in listOf(
                {
                    File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "Masari"
                    )
                },
                { File(Environment.getExternalStorageDirectory(), "Documents/Masari") },
                {
                    File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "Masari"
                    )
                },
                { context?.getExternalFilesDir(null)?.let { File(it, "Masari") } }
            )) {
                try {
                    val dir = candidateGetter() ?: continue
                    if (isDirectoryWritable(dir)) {
                        return dir
                    }
                } catch (_: Throwable) {}
            }

            // Fallback to app private directory
            val fallbackDir = File(context?.filesDir ?: File("."), "Masari")
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            return fallbackDir
        }

    fun getStorageDirectoryPath(): String = dataDir.absolutePath

    fun isPublicStorageActive(): Boolean {
        val path = dataDir.absolutePath
        return !path.contains("/data/user/") && !path.contains("/data/data/") &&
                (path.contains("Documents/Masari") || path.contains("Masari") || path.contains("Download"))
    }

    /**
     * Finds the best readable file across ALL candidate directories.
     * This is the KEY fix: reading does NOT depend on dataDir (which requires write permission).
     * Even if we can't write to Documents/Masari, we can still READ files from there.
     */
    private fun findReadableFile(filename: String): String? {
        // 1. First check all candidate directories for the file (external first)
        for (candDir in getCandidateDirectories()) {
            try {
                val candFile = File(candDir, filename)
                if (candFile.exists() && candFile.length() > 0L) {
                    val content = candFile.readText()
                    if (content.isNotBlank()) {
                        android.util.Log.d(TAG, "Read $filename from: ${candDir.absolutePath}")
                        return content
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to read $filename from ${candDir.absolutePath}: ${e.message}")
            }
        }

        // 2. Check for .tmp files left by interrupted atomic writes
        for (candDir in getCandidateDirectories()) {
            try {
                val tmpFile = File(candDir, "$filename.tmp")
                if (tmpFile.exists() && tmpFile.length() > 0L) {
                    val content = tmpFile.readText()
                    if (content.isNotBlank()) {
                        android.util.Log.d(TAG, "Read $filename.tmp from: ${candDir.absolutePath}")
                        // Try to promote the .tmp to the real file
                        try {
                            tmpFile.copyTo(File(candDir, filename), overwrite = true)
                        } catch (_: Throwable) {}
                        return content
                    }
                }
            } catch (_: Throwable) {}
        }

        android.util.Log.d(TAG, "No readable file found for: $filename")
        return null
    }

    private fun mirrorWrite(filename: String, content: String) {
        // If dataDir is outside app internal storage, also mirror to context.filesDir as local cache
        try {
            val internalBackupDir = File(context?.filesDir ?: return, "Masari")
            if (internalBackupDir.absolutePath != dataDir.absolutePath) {
                val backupFile = File(internalBackupDir, filename)
                writeAtomically(backupFile, content)
            }
        } catch (_: Throwable) {}

        // If dataDir is inside app internal storage, also attempt to mirror to external Documents/Masari
        try {
            val publicDocs = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Masari")
            if (publicDocs.absolutePath != dataDir.absolutePath && isDirectoryWritable(publicDocs)) {
                val extFile = File(publicDocs, filename)
                writeAtomically(extFile, content)
            }
        } catch (_: Throwable) {}
    }

    val manualExpensesFile: File get() = File(dataDir, "manual_expenses.json")
    val skippedTransactionsFile: File get() = File(dataDir, "skipped_transactions.json")
    val messageTemplatesFile: File get() = File(dataDir, "message_templates.json")
    val monitoredBanksFile: File get() = File(dataDir, "monitored_banks.json")

    private val lock = Any()

    // ── Manual Expenses Operations ────────────────────────────────────────

    fun getManualExpenses(): List<ManualExpense> = synchronized(lock) {
        val content = findReadableFile("manual_expenses.json") ?: return emptyList()
        if (content.isBlank()) return emptyList()
        try {
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
            val content = jsonArray.toString(2)
            writeAtomically(manualExpensesFile, content)
            mirrorWrite("manual_expenses.json", content)
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
        }
    }

    // ── Skipped Transactions Operations ──────────────────────────────────

    fun getSkippedTransactions(): List<SkippedTransaction> = synchronized(lock) {
        val content = findReadableFile("skipped_transactions.json") ?: return emptyList()
        if (content.isBlank()) return emptyList()
        try {
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
            val content = jsonArray.toString(2)
            writeAtomically(skippedTransactionsFile, content)
            mirrorWrite("skipped_transactions.json", content)
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
        }
    }

    // ── Message Templates Operations ─────────────────────────────────────

    fun getMessageTemplates(): List<MessageTemplate> = synchronized(lock) {
        val content = findReadableFile("message_templates.json")
        if (content.isNullOrBlank()) {
            // Seed with default templates and save to file system
            val initial = MessageTemplate.defaultTemplates
            writeMessageTemplates(initial)
            return initial
        }
        try {
            val jsonArray = JSONArray(content)
            val result = mutableListOf<MessageTemplate>()
            for (i in 0 until jsonArray.length()) {
                result.add(MessageTemplate.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            if (result.isEmpty()) {
                val initial = MessageTemplate.defaultTemplates
                writeMessageTemplates(initial)
                initial
            } else {
                result.sortedByDescending { it.updatedAt }
            }
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
            MessageTemplate.defaultTemplates
        }
    }

    fun saveMessageTemplate(template: MessageTemplate): Unit = synchronized(lock) {
        val currentList = getMessageTemplates().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == template.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = template.copy(updatedAt = System.currentTimeMillis())
        } else {
            currentList.add(0, template)
        }
        writeMessageTemplates(currentList)
    }

    fun deleteMessageTemplate(id: String): Boolean = synchronized(lock) {
        val currentList = getMessageTemplates().toMutableList()
        val removed = currentList.removeAll { it.id == id }
        if (removed) {
            writeMessageTemplates(currentList)
        }
        removed
    }

    fun toggleTemplate(id: String, isEnabled: Boolean): Boolean = synchronized(lock) {
        val currentList = getMessageTemplates().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(
                isEnabled = isEnabled,
                updatedAt = System.currentTimeMillis()
            )
            writeMessageTemplates(currentList)
            true
        } else {
            false
        }
    }

    private fun writeMessageTemplates(templates: List<MessageTemplate>) {
        try {
            val jsonArray = JSONArray()
            templates.forEach { jsonArray.put(it.toJsonObject()) }
            val content = jsonArray.toString(2)
            writeAtomically(messageTemplatesFile, content)
            mirrorWrite("message_templates.json", content)
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
        }
    }

    // ── Monitored Banks Operations ────────────────────────────────────────

    fun getMonitoredBanks(): List<BankSender> = synchronized(lock) {
        val content = findReadableFile("monitored_banks.json")
        if (content.isNullOrBlank()) {
            val initial = BankSender.defaultSenders
            writeMonitoredBanks(initial)
            return initial
        }
        try {
            val jsonArray = JSONArray(content)
            val result = mutableListOf<BankSender>()
            for (i in 0 until jsonArray.length()) {
                result.add(BankSender.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            if (result.isEmpty()) BankSender.defaultSenders else result
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error reading banks: ${e.message}")
            BankSender.defaultSenders
        }
    }

    fun saveMonitoredBank(bank: BankSender): Unit = synchronized(lock) {
        val currentList = getMonitoredBanks().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.senderId.equals(bank.senderId, ignoreCase = true) }
        if (existingIndex >= 0) {
            currentList[existingIndex] = bank
        } else {
            currentList.add(bank)
        }
        writeMonitoredBanks(currentList)
    }

    fun updateMonitoredBank(oldSenderId: String, updated: BankSender): Unit = synchronized(lock) {
        val currentList = getMonitoredBanks().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.senderId.equals(oldSenderId, ignoreCase = true) }
        if (existingIndex >= 0) {
            currentList[existingIndex] = updated
        } else {
            currentList.add(updated)
        }
        writeMonitoredBanks(currentList)
    }

    fun deleteMonitoredBank(senderId: String): Boolean = synchronized(lock) {
        val currentList = getMonitoredBanks().toMutableList()
        val removed = currentList.removeAll { it.senderId.equals(senderId, ignoreCase = true) }
        if (removed) {
            writeMonitoredBanks(currentList)
        }
        removed
    }

    fun toggleMonitoredBank(senderId: String, isMonitored: Boolean): Boolean = synchronized(lock) {
        val currentList = getMonitoredBanks().toMutableList()
        val index = currentList.indexOfFirst { it.senderId.equals(senderId, ignoreCase = true) }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(isMonitored = isMonitored)
            writeMonitoredBanks(currentList)
            true
        } else {
            false
        }
    }

    fun writeMonitoredBanks(banks: List<BankSender>) {
        try {
            val jsonArray = JSONArray()
            banks.forEach { jsonArray.put(it.toJsonObject()) }
            val content = jsonArray.toString(2)
            writeAtomically(monitoredBanksFile, content)
            mirrorWrite("monitored_banks.json", content)
        } catch (e: Exception) {
            System.err.println("ExpenseFileManager error: ${e.message}")
        }
    }

    fun writeJsonBackup(filename: String, content: String) = synchronized(lock) {
        val targetFile = File(dataDir, filename)
        writeAtomically(targetFile, content)
        mirrorWrite(filename, content)
    }

    // ── Persistent Files Wiper ────────────────────────────────────────────

    fun clearAllFiles(): Boolean = synchronized(lock) {
        var allSuccess = true
        val filenames = listOf(
            "manual_expenses.json",
            "skipped_transactions.json",
            "message_templates.json",
            "monitored_banks.json"
        )
        // Clear from active dataDir
        for (name in filenames) {
            val f = File(dataDir, name)
            if (f.exists()) {
                val deleted = f.delete()
                if (!deleted) allSuccess = false
            }
            val tmp = File(dataDir, "$name.tmp")
            if (tmp.exists()) tmp.delete()
        }
        // Also clear from candidate backup directories
        for (dir in getCandidateDirectories()) {
            for (name in filenames) {
                val f = File(dir, name)
                if (f.exists()) f.delete()
                val tmp = File(dir, "$name.tmp")
                if (tmp.exists()) tmp.delete()
            }
        }
        allSuccess
    }

    private fun writeAtomically(targetFile: File, content: String) {
        try {
            val parent = targetFile.parentFile ?: return
            if (!parent.exists()) parent.mkdirs()
            val tempFile = File(parent, "${targetFile.name}.tmp")
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                try {
                    fos.fd.sync()
                } catch (_: Throwable) {}
            }
            if (tempFile.exists()) {
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            // Direct write fallback
            try {
                FileOutputStream(targetFile).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
            } catch (ex: Exception) {
                System.err.println("ExpenseFileManager writeAtomically error: ${ex.message}")
            }
        }
    }
}

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

    private fun isDirectoryUsable(dir: File): Boolean {
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

    val dataDir: File
        get() {
            if (baseDirectory != null) {
                if (!baseDirectory.exists()) baseDirectory.mkdirs()
                return baseDirectory
            }

            // 1. Try public Documents/Masari outside app sandboxed data folder
            try {
                val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val masariDir = File(publicDocs, "Masari")
                if (isDirectoryUsable(masariDir)) {
                    recoverAndMigrateFiles(masariDir)
                    return masariDir
                }
            } catch (_: Throwable) {}

            // 2. Try root ExternalStorage/Documents/Masari
            try {
                val extDocs = File(Environment.getExternalStorageDirectory(), "Documents/Masari")
                if (isDirectoryUsable(extDocs)) {
                    recoverAndMigrateFiles(extDocs)
                    return extDocs
                }
            } catch (_: Throwable) {}

            // 3. Try public Downloads/Masari
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val masariDir = File(publicDownloads, "Masari")
                if (isDirectoryUsable(masariDir)) {
                    recoverAndMigrateFiles(masariDir)
                    return masariDir
                }
            } catch (_: Throwable) {}

            // 4. Try external app storage (survives when internal is cleared, if permission allows)
            try {
                val extAppDir = context?.getExternalFilesDir(null)?.let { File(it, "Masari") }
                if (extAppDir != null && isDirectoryUsable(extAppDir)) {
                    recoverAndMigrateFiles(extAppDir)
                    return extAppDir
                }
            } catch (_: Throwable) {}

            // 5. Fallback to app private directory if external is not yet permitted
            val fallbackDir = File(context?.filesDir ?: File("."), "Masari")
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            recoverAndMigrateFiles(fallbackDir)
            return fallbackDir
        }

    fun getStorageDirectoryPath(): String = dataDir.absolutePath

    fun isPublicStorageActive(): Boolean {
        val path = dataDir.absolutePath
        return !path.contains("/data/user/") && !path.contains("/data/data/") &&
                (path.contains("Documents/Masari") || path.contains("Masari") || path.contains("Download"))
    }

    private fun recoverAndMigrateFiles(targetDir: File) {
        val filesToMigrate = listOf(
            "manual_expenses.json",
            "skipped_transactions.json",
            "message_templates.json",
            "monitored_banks.json"
        )
        for (filename in filesToMigrate) {
            val targetFile = File(targetDir, filename)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                // Search across all candidate directories for existing data
                for (candDir in getCandidateDirectories()) {
                    if (candDir.absolutePath == targetDir.absolutePath) continue
                    val candFile = File(candDir, filename)
                    if (candFile.exists() && candFile.length() > 0L) {
                        try {
                            candFile.copyTo(targetFile, overwrite = true)
                            break
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    private fun readFileContent(file: File): String? {
        if (file.exists() && file.length() > 0L) {
            try {
                val text = file.readText()
                if (text.isNotBlank()) return text
            } catch (_: Exception) {}
        }
        // Check for temp file fallback if a previous rename failed
        val parent = file.parentFile
        if (parent != null) {
            val tmp = File(parent, "${file.name}.tmp")
            if (tmp.exists() && tmp.length() > 0L) {
                try {
                    val content = tmp.readText()
                    if (content.isNotBlank()) {
                        tmp.copyTo(file, overwrite = true)
                        return content
                    }
                } catch (_: Throwable) {}
            }
        }
        // Check across other candidate directories for recovery
        for (candDir in getCandidateDirectories()) {
            if (candDir.absolutePath == file.parentFile?.absolutePath) continue
            val candFile = File(candDir, file.name)
            if (candFile.exists() && candFile.length() > 0L) {
                try {
                    val content = candFile.readText()
                    if (content.isNotBlank()) {
                        candFile.copyTo(file, overwrite = true)
                        return content
                    }
                } catch (_: Throwable) {}
            }
        }
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
            if (publicDocs.absolutePath != dataDir.absolutePath && isDirectoryUsable(publicDocs)) {
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
        val content = readFileContent(manualExpensesFile) ?: return emptyList()
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
        val content = readFileContent(skippedTransactionsFile) ?: return emptyList()
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
        val content = readFileContent(messageTemplatesFile)
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
        val content = readFileContent(monitoredBanksFile)
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

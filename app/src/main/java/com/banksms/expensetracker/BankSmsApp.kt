package com.banksms.expensetracker

import android.app.Application
import com.banksms.expensetracker.data.file.ExpenseFileManager
import com.banksms.expensetracker.data.llm.ChatEngine
import com.banksms.expensetracker.data.llm.FakeChatEngine
import com.banksms.expensetracker.data.llm.LiteRtLmChatEngine
import com.banksms.expensetracker.data.llm.LlmEngines
import com.banksms.expensetracker.data.llm.LlmModelFiles
import com.banksms.expensetracker.data.local.AiAppDatabase
import com.banksms.expensetracker.data.local.AppDatabase
import com.banksms.expensetracker.data.local.PersistentDatabase
import com.banksms.expensetracker.data.reader.SmsReader
import com.banksms.expensetracker.data.repository.TransactionRepository

class BankSmsApp : Application() {

    lateinit var database: AppDatabase
        private set

    /** Separate database holding ONLY LLM-extracted transactions. */
    lateinit var aiDatabase: AiAppDatabase
        private set

    lateinit var persistentDatabase: PersistentDatabase
        private set

    lateinit var fileManager: ExpenseFileManager
        private set

    lateinit var repository: TransactionRepository
        private set

    /**
     * Single shared engine for chat AND background AI parsing (one 2.5 GB
     * model load, inference serialized inside LiteRtLmChatEngine).
     */
    @Volatile
    var llmEngine: ChatEngine = FakeChatEngine()
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        aiDatabase = AiAppDatabase.getInstance(this)
        val smsReader = SmsReader(this)
        fileManager = ExpenseFileManager(this)
        persistentDatabase = PersistentDatabase.getInstance(this, fileManager)
        // Kick off the idempotent JSON -> DB migration (safe to call repeatedly).
        PersistentDatabase.ensureMigrated(fileManager)
        refreshEngine()
        val prefs = getSharedPreferences("masari_prefs", MODE_PRIVATE)
        repository = TransactionRepository(
            transactionDao = database.transactionDao(),
            bankSenderDao = database.bankSenderDao(),
            smsReader = smsReader,
            fileManager = fileManager,
            persistentDb = persistentDatabase,
            aiTransactionDao = aiDatabase.aiTransactionDao(),
            aiScannedDao = aiDatabase.aiScannedDao(),
            prefs = prefs,
            engineProvider = { llmEngine },
            // Fresh engine per scan batch (single owner each, so a stopped
            // scan can abandon in-flight batches safely).
            engineFactory = { LiteRtLmChatEngine(this) }
        )
    }

    /**
     * Returns the shared engine, swapping Fake <-> real when the model-file
     * state changed (e.g. right after a download/import). Cheap otherwise.
     */
    fun refreshEngine(): ChatEngine {
        val hasModel = try {
            LlmModelFiles.modelFile(this).exists()
        } catch (_: Exception) {
            false
        }
        val current = llmEngine
        if (hasModel == !current.isDemo) return current
        try {
            current.close()
        } catch (_: Throwable) {
        }
        return LlmEngines.get(this).also { llmEngine = it }
    }
}

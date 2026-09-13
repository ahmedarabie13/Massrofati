package com.banksms.expensetracker

import android.app.Application
import android.content.Context
import android.util.Log
import com.banksms.expensetracker.data.auth.AuthRepository
import com.banksms.expensetracker.data.auth.BiometricUnlock
import com.banksms.expensetracker.data.auth.PasscodeLock
import com.banksms.expensetracker.data.cloud.FirestoreStore
import com.banksms.expensetracker.data.cloud.LegacyLocalMigration
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BankSmsApp : Application() {

    /** Legacy on-device databases: kept ONLY as the migration source. */
    lateinit var database: AppDatabase
        private set

    /** Separate database holding ONLY LLM-extracted transactions. */
    lateinit var aiDatabase: AiAppDatabase
        private set

    lateinit var persistentDatabase: PersistentDatabase
        private set

    lateinit var fileManager: ExpenseFileManager
        private set

    /**
     * Active user session's repository (Firestore-backed). Opened by
     * [openSession] — never touch before [isSessionOpen] is true.
     */
    lateinit var repository: TransactionRepository
        private set

    /** Uid the current [repository] belongs to (null = no session). */
    @Volatile
    var sessionUid: String? = null
        private set

    /**
     * Cold-start inbox sync runs once per process. Account switches and
     * post-login compositions must NOT retrigger a full sync the moment
     * the user lands in the app (it looks like a stuck reload screen and
     * hammers write quotas); manual sync stays one tap away.
     */
    @Volatile
    var didColdStartSync = false

    fun isSessionOpen(uid: String? = null): Boolean {
        if (!::repository.isInitialized) return false
        return uid == null || sessionUid == uid
    }

    /**
     * Opens (or reuses) the Firestore session for [uid]: builds the store +
     * repository and fires the one-time legacy upload. Lightweight and safe
     * to call from the receiver, the gate, or onCreate.
     */
    @Synchronized
    fun openSession(uid: String): TransactionRepository {
        Log.d("BankSmsApp", "openSession ${uid.take(6)} (current=$sessionUid)")
        if (isSessionOpen(uid)) return repository
        closeSession()
        val store = FirestoreStore(uid, FirebaseFirestore.getInstance())
        repository = TransactionRepository(
            store = store,
            smsReader = SmsReader(this),
            prefs = getSharedPreferences("masari_prefs", Context.MODE_PRIVATE),
            uid = uid,
            engineProvider = { llmEngine },
            // Fresh engine per scan batch (single owner each, so a stopped
            // scan can abandon in-flight batches safely).
            engineFactory = { LiteRtLmChatEngine(this) }
        )
        sessionUid = uid
        sessionScope.launch {
            try {
                LegacyLocalMigration.uploadIfNeeded(this@BankSmsApp, store, uid)
            } catch (e: Exception) {
                Log.w("BankSmsApp", "legacy migration launch failed", e)
            }
        }
        return repository
    }

    /**
     * Returns the session repository for the currently signed-in user,
     * opening it if needed. Null when signed out (callers must drop work —
     * cloud data can't be attributed without a user).
     */
    fun ensureSession(): TransactionRepository? {
        val uid = authRepository.currentUser?.uid ?: return null
        return try {
            openSession(uid)
        } catch (e: Exception) {
            Log.w("BankSmsApp", "openSession failed", e)
            null
        }
    }

    @Synchronized
    fun closeSession() {
        if (::repository.isInitialized) {
            runCatching { repository.close() }
        }
        sessionUid = null
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Firebase Auth wrapper (session survives process death via the SDK). */
    lateinit var authRepository: AuthRepository
        private set

    /** App-level biometric gate over the persisted Firebase session. */
    lateinit var biometricUnlock: BiometricUnlock
        private set

    /** Per-account 4-digit passcode gate (fallback when biometrics are off). */
    lateinit var passcodeLock: PasscodeLock
        private set

    /**
     * Single shared engine for chat AND background AI parsing (one 3.7 GB
     * model load, inference serialized inside LiteRtLmChatEngine).
     */
    @Volatile
    var llmEngine: ChatEngine = FakeChatEngine()
        private set

    override fun onCreate() {
        super.onCreate()
        authRepository = AuthRepository()
        biometricUnlock = BiometricUnlock(this)
        passcodeLock = PasscodeLock(this)
        database = AppDatabase.getInstance(this)
        aiDatabase = AiAppDatabase.getInstance(this)
        fileManager = ExpenseFileManager(this)
        persistentDatabase = PersistentDatabase.getInstance(this, fileManager)
        // Kick off the idempotent JSON -> DB migration (safe to call repeatedly).
        PersistentDatabase.ensureMigrated(fileManager)
        refreshEngine()
        // Reopen the previous session (if any) so background SMS intake works
        // even before the UI gate resolves.
        authRepository.currentUser?.uid?.let { uid ->
            runCatching { openSession(uid) }
        }
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

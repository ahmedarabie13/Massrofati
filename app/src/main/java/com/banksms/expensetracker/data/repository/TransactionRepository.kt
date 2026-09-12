package com.banksms.expensetracker.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.banksms.expensetracker.data.file.ExpenseFileManager
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.llm.ChatEngine
import com.banksms.expensetracker.data.llm.EngineState
import com.banksms.expensetracker.data.local.PersistentDatabase
import com.banksms.expensetracker.data.local.dao.AiScannedDao
import com.banksms.expensetracker.data.local.dao.AiTransactionDao
import com.banksms.expensetracker.data.local.dao.BankSenderDao
import com.banksms.expensetracker.data.local.dao.BankExpenseDbSummary
import com.banksms.expensetracker.data.local.dao.CategoryDbSummary
import com.banksms.expensetracker.data.local.dao.MonthlyDbTrend
import com.banksms.expensetracker.data.local.dao.TransactionDao
import com.banksms.expensetracker.data.local.entity.*
import com.banksms.expensetracker.data.model.*
import com.banksms.expensetracker.data.parser.AiScanProgress
import com.banksms.expensetracker.data.parser.AiScanTracker
import com.banksms.expensetracker.data.parser.AiSmsParser
import com.banksms.expensetracker.data.parser.BankSmsParser
import com.banksms.expensetracker.data.parser.RawSms
import com.banksms.expensetracker.data.reader.DiscoveredSender
import com.banksms.expensetracker.data.reader.SmsReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class SyncResult(
    val messagesScanned: Int,
    val transactionsImported: Int,
    val errors: List<String> = emptyList()
)

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val bankSenderDao: BankSenderDao,
    private val smsReader: SmsReader,
    private val fileManager: ExpenseFileManager,
    private val persistentDb: PersistentDatabase,
    private val aiTransactionDao: AiTransactionDao,
    private val aiScannedDao: AiScannedDao,
    prefs: SharedPreferences,
    /** Provides the shared on-device engine; null where unavailable (e.g. receiver fallback). */
    private val engineProvider: (() -> ChatEngine)? = null,
    /**
     * Builds a FRESH engine per scan batch (single owner per instance, so a
     * stopped scan can abandon in-flight batches safely). Null where unavailable.
     */
    private val engineFactory: (() -> ChatEngine)? = null
) {

    companion object {
        private const val PREFS_PARSE_MODE = "parsing_mode"
        private const val TAG = "AiScan"
    }

    // ── Parsing mode (manual regex vs on-device AI) ──────────────────────

    private val modePrefs = prefs

    private val _parseMode = MutableStateFlow(
        ParseMode.fromStored(prefs.getString(PREFS_PARSE_MODE, null))
    )
    /** Active pipeline. All read paths below follow this flow. */
    val parseMode: StateFlow<ParseMode> = _parseMode.asStateFlow()

    /**
     * Latest AI rescan report (per-chat status + scanned/excluded/remaining
     * counts). Published after every batch and kept when the run finishes so
     * the result stays viewable; cleared on dismiss / next scan / data clear.
     */
    private val _aiScanProgress = MutableStateFlow<AiScanProgress?>(null)
    val aiScanProgress: StateFlow<AiScanProgress?> = _aiScanProgress.asStateFlow()

    fun dismissAiScanProgress() {
        _aiScanProgress.value = null
    }

    suspend fun setParseMode(mode: ParseMode) = withContext(Dispatchers.IO) {
        modePrefs.edit().putString(PREFS_PARSE_MODE, mode.name).apply()
        _parseMode.value = mode
    }

    /** True when the LLM file is present (real engine), so AI parsing can run. */
    fun isAiEngineAvailable(): Boolean =
        try {
            engineProvider?.invoke()?.isDemo == false
        } catch (_: Exception) {
            false
        }

    /** Wipes the AI database (transactions + scan verdicts); manual data is untouched. */
    suspend fun clearAiTransactions() = withContext(Dispatchers.IO) {
        aiTransactionDao.deleteAll()
        aiScannedDao.deleteAll()
        _aiScanProgress.value = null
    }

    // Persistent DB DAOs
    private val pManualDao get() = persistentDb.manualExpenseDao()
    private val pSkippedDao get() = persistentDb.skippedTransactionDao()
    private val pTemplateDao get() = persistentDb.messageTemplateDao()
    private val pBankDao get() = persistentDb.monitoredBankDao()

    private val _skippedTransactions = MutableStateFlow<List<SkippedTransaction>>(fileManager.getSkippedTransactions())
    val skippedTransactions: StateFlow<List<SkippedTransaction>> = _skippedTransactions.asStateFlow()

    private val _messageTemplates = MutableStateFlow<List<MessageTemplate>>(MessageTemplate.defaultTemplates)
    val messageTemplates: StateFlow<List<MessageTemplate>> = _messageTemplates.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            refreshFromFiles()
        }
    }

    /**
     * Processes an incoming real-time SMS from BroadcastReceiver with full deduplication and skip filtering.
     * @return true if a new transaction was inserted, false if skipped or already exists.
     */
    suspend fun processIncomingSms(
        sender: String,
        body: String,
        timestamp: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val monitored = bankSenderDao.getMonitoredSendersSync()
            .map { it.senderId.lowercase() }
            .toSet()

        val senderLower = sender.lowercase()
        val isMonitored = monitored.any {
            senderLower == it || senderLower.contains(it) || it.contains(senderLower)
        }
        if (!isMonitored) return@withContext false

        // Check if this transaction is marked as skipped in persistent DB
        if (isSkippedInDb(0L, sender, timestamp, body)) {
            return@withContext false
        }

        return@withContext if (_parseMode.value == ParseMode.AI) {
            processIncomingSmsAi(sender, body, timestamp)
        } else {
            val enabledTemplates = pTemplateDao.getEnabled().map { it.toDomain() }
            processIncomingSmsManual(sender, body, timestamp, enabledTemplates)
        }
    }

    /** Regex path: parse + insert into the manual database. */
    private suspend fun processIncomingSmsManual(
        sender: String,
        body: String,
        timestamp: Long,
        enabledTemplates: List<MessageTemplate>
    ): Boolean {
        val parsed = BankSmsParser.parse(body, sender, enabledTemplates) ?: return false

        // Prevent inserting if this transaction is already present
        val existing = transactionDao.findDuplicate(
            sender = sender,
            amount = parsed.amount,
            type = parsed.type.name,
            rawBody = body,
            timestamp = timestamp
        )
        if (existing != null) {
            return false
        }

        val transaction = parsed.toTransaction(
            messageId = timestamp,
            sender = sender,
            timestamp = timestamp,
            rawBody = body
        )
        val insertId = transactionDao.insert(TransactionEntity.fromDomain(transaction))
        return insertId != -1L
    }

    /**
     * AI path: single message to the on-device model, result into the AI
     * database. No model (or no engine) means the SMS is dropped — it must
     * NOT fall back to manual parsing, or the two databases would mix.
     */
    private suspend fun processIncomingSmsAi(
        sender: String,
        body: String,
        timestamp: Long
    ): Boolean {
        val engine = engineProvider?.invoke() ?: return false
        if (engine.isDemo) return false
        // OTPs/ads without a currency token never reach the model.
        if (!AiSmsParser.hasMoneyHint(body)) return false
        val parsed = try {
            AiSmsParser.parseSingle(engine, RawSms(timestamp, sender, body, timestamp))
        } catch (_: Exception) {
            null
        } ?: return false

        val existing = aiTransactionDao.findDuplicate(
            sender = sender,
            amount = parsed.amount,
            type = parsed.type.name,
            rawBody = body,
            timestamp = timestamp
        )
        if (existing != null) return false

        val insertId = aiTransactionDao.insert(AiTransactionEntity.fromDomain(parsed))
        return insertId != -1L
    }

    /** One sync at a time: Dashboard auto-sync and manual rescans share this path. */
    private val syncRunning = AtomicBoolean(false)

    /** Detached batch workers live here: a stop abandons the wait, never the worker. */
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializes native Engine creation (concurrent init can crash); inference overlaps. */
    private val engineCreateMutex = Mutex()

    /** Bumped on every stop; workers with a stale generation self-discard. */
    private val scanGeneration = AtomicLong(0L)

    /** The caller's job while a sync runs, so any screen can stop it. */
    @Volatile
    private var runningSyncJob: Job? = null

    /** True while any sync (manual or AI) is running. */
    private val _isSyncRunning = MutableStateFlow(false)
    val isSyncRunning: StateFlow<Boolean> = _isSyncRunning.asStateFlow()

    /**
     * Stops a running sync from any screen and returns IMMEDIATELY: no new
     * work starts, the wait is abandoned (in-flight batches finish privately
     * on their own engines, then self-discard and close up), recorded partial
     * results are kept. The Banks tab keeps the frozen final report.
     * Returns false when idle.
     */
    fun cancelSync(): Boolean {
        val job = runningSyncJob ?: return false
        if (!job.isActive) return false
        // Bump first: workers check this before recording anything.
        scanGeneration.incrementAndGet()
        job.cancel(CancellationException("Sync stopped by user"))
        return true
    }

    suspend fun syncTransactionsFromSms(): SyncResult {
        // Overlapping runs would duplicate model work (AI mode) or inbox
        // writes; the Banks tab shows the running scan's live progress.
        if (!syncRunning.compareAndSet(false, true)) {
            return SyncResult(0, 0, listOf("A sync is already running."))
        }
        runningSyncJob = currentCoroutineContext()[Job]
        _isSyncRunning.value = true
        try {
            return doSyncTransactionsFromSms()
        } finally {
            runningSyncJob = null
            _isSyncRunning.value = false
            syncRunning.set(false)
        }
    }

    /**
     * Scans both SMS messages and the persistent DB manual expenses.
     * Deduplicates against existing transactions and respects skipped transaction records.
     */
    private suspend fun doSyncTransactionsFromSms(): SyncResult = withContext(Dispatchers.IO) {
        try {
            // 1. Clean up any historical duplicate transactions
            transactionDao.deleteDuplicates()

            // 2. Read message templates from persistent DB
            val templates = pTemplateDao.getAll().map { it.toDomain() }
            _messageTemplates.value = templates

            // 3. Refresh and enforce skipped transactions from persistent DB
            val skippedList = pSkippedDao.getAll().map { it.toDomain() }
            _skippedTransactions.value = skippedList
            for (skipped in skippedList) {
                ensureActive() // Stop takes effect between rows.
                if (skipped.originalMessageId != 0L) {
                    transactionDao.deleteByMessageId(skipped.originalMessageId)
                }
                val dup = transactionDao.findDuplicate(
                    sender = skipped.sender,
                    amount = skipped.amount,
                    type = skipped.type.name,
                    rawBody = skipped.rawBody,
                    timestamp = skipped.timestamp
                )
                if (dup != null) {
                    transactionDao.delete(dup)
                }
            }

            // 4. Scan manual expenses from persistent DB
            syncManualExpensesFromDb()

            // 5. Synchronize monitored banks from persistent DB to internal Room
            syncMonitoredBanksFromDb()

            // 6. Scan SMS inbox with freshly loaded templates
            val monitored = bankSenderDao.getMonitoredSendersSync()
            if (monitored.isEmpty()) {
                return@withContext SyncResult(0, 0, listOf("No monitored bank senders configured. Please enable banks in Settings."))
            }

            val monitoredSenderIds = monitored.map { it.senderId }.toSet()

            // AI mode never touches the regex pipeline: raw inbox rows go to
            // the model pool in batches of 20 and land in the AI database.
            if (_parseMode.value == ParseMode.AI) {
                return@withContext syncAiTransactionsFromSms(monitoredSenderIds)
            }

            val enabledTemplates = templates.filter { it.isEnabled }
            val parsedTransactions = smsReader.readBankMessages(monitoredSenderIds, customTemplates = enabledTemplates)

            if (parsedTransactions.isEmpty()) {
                return@withContext SyncResult(0, 0)
            }

            var newlyImported = 0
            for (transaction in parsedTransactions) {
                ensureActive() // Stop takes effect between rows.
                // Skip if user marked this transaction as skipped
                if (isSkippedInDb(transaction.messageId, transaction.sender, transaction.timestamp, transaction.rawBody)) {
                    continue
                }

                val entity = TransactionEntity.fromDomain(transaction)

                // Check 1: Already exists with this exact messageId?
                val existingByMsgId = transactionDao.getByMessageId(entity.messageId)
                if (existingByMsgId != null) {
                    continue
                }

                // Check 2: Was it already inserted by real-time receiver (which used timestamp as messageId)?
                val existingDuplicate = transactionDao.findDuplicate(
                    sender = entity.sender,
                    amount = entity.amount,
                    type = entity.type,
                    rawBody = entity.rawBody,
                    timestamp = entity.timestamp
                )

                if (existingDuplicate != null) {
                    // Update existing record to match the permanent inbox messageId
                    if (existingDuplicate.messageId != entity.messageId) {
                        transactionDao.update(existingDuplicate.copy(messageId = entity.messageId))
                    }
                } else {
                    // Brand new transaction
                    val id = transactionDao.insert(entity)
                    if (id != -1L) {
                        newlyImported++
                    }
                }
            }

            SyncResult(
                messagesScanned = parsedTransactions.size,
                transactionsImported = newlyImported
            )
        } catch (e: CancellationException) {
            // Never swallow cancellation (stopSync): propagate so the caller's
            // job completes as cancelled and flags reset in finally blocks.
            throw e
        } catch (e: Exception) {
            SyncResult(0, 0, listOf(e.localizedMessage ?: "Unknown sync error"))
        }
    }

    /**
     * AI bulk scan: raw inbox rows -> model (20 per call, each batch on its
     * own private engine) -> AI database. Rows already stored or skipped are
     * filtered BEFORE inference so no model time is wasted on them. One
     * failing batch never aborts the run.
     *
     * Stop semantics: batch workers are detached from the waiting scan, so a
     * stop returns IMMEDIATELY without joining in-flight native calls. Each
     * worker owns its engine cradle-to-grave, finishes privately, then either
     * records results (scan alive) or discards them (stopped) and closes its
     * engine. Nothing is ever half-recorded; nothing leaks.
     */
    private suspend fun syncAiTransactionsFromSms(monitoredSenderIds: Set<String>): SyncResult {
        val factory = engineFactory
        if (factory == null || !isAiEngineAvailable()) {
            return SyncResult(
                0, 0,
                listOf("AI parsing needs the on-device model. Download it from the Assistant tab first.")
            )
        }
        val tracker = AiScanTracker()
        val myGen = scanGeneration.get()
        fun sessionStopped() = myGen != scanGeneration.get()
        try {
            aiTransactionDao.deleteDuplicates()

            // Monitored senders only (readRawBankMessages filters by them).
            val raw = smsReader.readRawBankMessages(monitoredSenderIds)
            if (raw.isEmpty()) return SyncResult(0, 0)
            tracker.setTotals(raw)
            _aiScanProgress.value = tracker.snapshot()

            // Attribute every row up front: no-currency / duplicate / skipped /
            // already-judged never cost inference; only the rest goes to the model.
            val fresh = mutableListOf<RawSms>()
            for (sms in raw) {
                when {
                    !AiSmsParser.hasMoneyHint(sms.body) -> tracker.markNoCurrency(sms)
                    aiTransactionDao.getByMessageId(sms.messageId) != null ->
                        tracker.markDuplicate(sms)
                    isSkippedInDb(sms.messageId, sms.sender, sms.timestamp, sms.body) ->
                        tracker.markSkipped(sms)
                    aiScannedDao.getByMessageId(sms.messageId) != null ->
                        tracker.markAlreadyScanned(sms)
                    else -> fresh.add(sms)
                }
            }
            _aiScanProgress.value = tracker.snapshot()
            if (fresh.isEmpty()) {
                val done = tracker.snapshot(finished = true)
                _aiScanProgress.value = done
                return SyncResult(raw.size, 0, done.errors)
            }

            Log.d(TAG, "AI scan: ${fresh.size} messages in ${AiSmsParser.chunkMessages(fresh).size} batches")

            val batches = AiSmsParser.chunkMessages(fresh)
            tracker.setBatchesTotal(batches.size)
            // Windows bound how many batches run at once. Workers are detached
            // (workerScope): awaiting them throws the moment the scan is
            // stopped, WITHOUT joining in-flight native calls — teardown is
            // instant, workers finish privately and self-discard.
            var base = 0
            batches.chunked(AiSmsParser.PARALLEL_ENGINES).forEach { window ->
                if (sessionStopped()) throw CancellationException("Scan stopped by user")
                val jobs = window.mapIndexed { slot, batch ->
                    workerScope.async {
                        runScanBatch(factory, batch, base + slot + 1, batches.size, myGen, tracker)
                    }
                }
                try {
                    jobs.awaitAll()
                } catch (e: CancellationException) {
                    // Stop (or caller death): abandon the wait now. Workers keep
                    // their private engines, finish alone, and self-discard.
                    throw CancellationException("Scan stopped by user")
                }
                base += window.size
            }
            val final = tracker.snapshot(finished = true)
            _aiScanProgress.value = final
            return SyncResult(
                messagesScanned = raw.size,
                transactionsImported = final.imported,
                errors = final.errors
            )
        } catch (e: CancellationException) {
            // Stopped by the user (or caller gone): freeze the last progress as
            // the final report. The note only claims a user stop when the stop
            // flag proves it; partial results are kept either way.
            if (sessionStopped()) {
                tracker.addError("Stopped by user — partial results kept; rescan resumes where this left off.")
            }
            _aiScanProgress.value = tracker.snapshot(finished = true)
            throw e
        } catch (e: Exception) {
            _aiScanProgress.value = tracker.snapshot(finished = true)
            return SyncResult(0, 0, listOf(e.localizedMessage ?: "Unknown AI sync error"))
        }
    }

    /**
     * One batch on a private engine, detached from the waiting scan. Even if
     * the scan is stopped mid-call, this runs to completion, then records
     * results (generation alive) or silently discards them (stale), and always
     * closes its engine. Single owner per engine, so abandoning is safe.
     */
    private suspend fun runScanBatch(
        factory: () -> ChatEngine,
        batch: List<RawSms>,
        batchNumber: Int,
        batchCount: Int,
        myGen: Long,
        tracker: AiScanTracker
    ) {
        // Stale workers never touch the live UI: only the current generation
        // may publish.
        fun publish() {
            if (myGen == scanGeneration.get()) _aiScanProgress.value = tracker.snapshot()
        }
        // Engine creation is serialized: concurrent native Engine
        // initialization can crash the process. Inference itself overlaps.
        val engine = try {
            engineCreateMutex.withLock {
                factory().also {
                    it.ensureLoaded()
                    check(it.state.value == EngineState.READY) {
                        it.status.value ?: "engine failed to load"
                    }
                }
            }
        } catch (e: Exception) {
            tracker.addError("Batch $batchNumber: engine unavailable (${e.message})")
            tracker.markBatchDone(batch, emptyList())
            publish()
            return
        }
        try {
            val inFlight = tracker.batchStarted()
            publish()
            Log.d(TAG, "Batch $batchNumber/$batchCount started (active=$inFlight)")
            val parsed = AiSmsParser.parseBatch(engine, batch)
            if (myGen != scanGeneration.get()) {
                Log.d(TAG, "Batch $batchNumber discarded (scan stopped)")
                return
            }
            val inserted = mutableListOf<Transaction>()
            val parsedIds = parsed.map { it.messageId }.toSet()
            for (tx in parsed) {
                if (isSkippedInDb(tx.messageId, tx.sender, tx.timestamp, tx.rawBody)) {
                    tracker.markSkipped(
                        RawSms(tx.messageId, tx.sender, tx.rawBody, tx.timestamp)
                    )
                } else {
                    val dup = aiTransactionDao.findDuplicate(
                        sender = tx.sender,
                        amount = tx.amount,
                        type = tx.type.name,
                        rawBody = tx.rawBody,
                        timestamp = tx.timestamp
                    )
                    if (dup == null &&
                        aiTransactionDao.insert(AiTransactionEntity.fromDomain(tx)) != -1L
                    ) {
                        inserted.add(tx)
                    }
                }
                // Verdict recorded per row AFTER handling, so every row is
                // consistent (both verdict+insert present, or neither).
                aiScannedDao.upsert(
                    AiScannedEntity(
                        tx.messageId, tx.sender, tx.timestamp,
                        tx.rawBody, true
                    )
                )
            }
            batch.filter { it.messageId !in parsedIds }.forEach { row ->
                aiScannedDao.upsert(
                    AiScannedEntity(
                        row.messageId, row.sender, row.timestamp,
                        row.body, false
                    )
                )
            }
            tracker.markBatchDone(batch, inserted)
        } catch (e: Exception) {
            tracker.addError("Batch $batchNumber: ${e.message}")
            // Count failed rows as attempted so remaining-math still
            // converges and chats show Done with errors.
            tracker.markBatchDone(batch, emptyList())
        } finally {
            val left = tracker.batchFinished()
            Log.d(TAG, "Batch $batchNumber finished (active=$left)")
            publish()
            try {
                engine.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Synchronizes manual expenses from persistent DB into internal Room.
     */
    private suspend fun syncManualExpensesFromDb() {
        val manualExpenses = pManualDao.getAll().map { it.toDomain() }
        val currentFileIds = manualExpenses.map { it.id }.toSet()

        // Remove any manual entries from Room that were deleted from persistent DB
        val existingDbManualIds = transactionDao.getAllManualIds()
        for (dbId in existingDbManualIds) {
            if (!currentFileIds.contains(dbId)) {
                transactionDao.deleteByManualId(dbId)
            }
        }
        // Same pruning for the AI database mirror.
        val existingAiManualIds = aiTransactionDao.getAllManualIds()
        for (dbId in existingAiManualIds) {
            if (!currentFileIds.contains(dbId)) {
                aiTransactionDao.deleteByManualId(dbId)
            }
        }

        // Upsert all manual expenses from persistent DB
        for (expense in manualExpenses) {
            // Stop takes effect between rows (also aborts stale refreshes).
            currentCoroutineContext().ensureActive()
            val transaction = expense.toTransaction()
            val existing = transactionDao.getByManualId(expense.id)
            if (existing != null) {
                val updated = TransactionEntity.fromDomain(transaction).copy(id = existing.id)
                transactionDao.update(updated)
            } else {
                transactionDao.insert(TransactionEntity.fromDomain(transaction))
            }
            // Mirror into the AI database so manual entries stay visible in AI mode.
            val aiExisting = aiTransactionDao.getByManualId(expense.id)
            val aiEntity = AiTransactionEntity.fromDomain(transaction)
            if (aiExisting != null) {
                aiTransactionDao.update(aiEntity.copy(id = aiExisting.id))
            } else {
                aiTransactionDao.insert(aiEntity)
            }
        }
    }

    /**
     * Checks if a transaction is skipped using the persistent DB.
     */
    private suspend fun isSkippedInDb(
        messageId: Long,
        sender: String,
        timestamp: Long,
        rawBody: String
    ): Boolean {
        val skippedList = pSkippedDao.getAll().map { it.toDomain() }
        if (skippedList.isEmpty()) return false

        return skippedList.any { skipped ->
            (messageId != 0L && skipped.originalMessageId == messageId) ||
            (rawBody.isNotBlank() && skipped.rawBody.isNotBlank() && skipped.sender == sender && skipped.rawBody == rawBody) ||
            (skipped.sender == sender && Math.abs(skipped.timestamp - timestamp) < 60000 &&
             rawBody.contains(skipped.amount.toInt().toString()))
        }
    }

    // ── Manual Expenses API ───────────────────────────────────────────────

    suspend fun addManualExpense(expense: ManualExpense) = withContext(Dispatchers.IO) {
        // Write to persistent DB
        pManualDao.upsert(ManualExpenseEntity.fromDomain(expense))
        // Mirror to JSON backup
        exportManualExpensesToJson()
        // Sync to internal Room for queries (both databases: visible in either mode)
        val transaction = expense.toTransaction()
        transactionDao.insert(TransactionEntity.fromDomain(transaction))
        aiTransactionDao.insert(AiTransactionEntity.fromDomain(transaction))
    }

    suspend fun updateManualExpense(expense: ManualExpense) = withContext(Dispatchers.IO) {
        pManualDao.upsert(ManualExpenseEntity.fromDomain(expense))
        exportManualExpensesToJson()
        val existing = transactionDao.getByManualId(expense.id)
        val transaction = expense.toTransaction()
        if (existing != null) {
            val updated = TransactionEntity.fromDomain(transaction).copy(id = existing.id)
            transactionDao.update(updated)
        } else {
            transactionDao.insert(TransactionEntity.fromDomain(transaction))
        }
        val aiExisting = aiTransactionDao.getByManualId(expense.id)
        val aiEntity = AiTransactionEntity.fromDomain(transaction)
        if (aiExisting != null) {
            aiTransactionDao.update(aiEntity.copy(id = aiExisting.id))
        } else {
            aiTransactionDao.insert(aiEntity)
        }
    }

    suspend fun deleteManualExpense(manualId: String) = withContext(Dispatchers.IO) {
        pManualDao.deleteById(manualId)
        exportManualExpensesToJson()
        transactionDao.deleteByManualId(manualId)
        aiTransactionDao.deleteByManualId(manualId)
    }

    suspend fun getManualExpenses(): List<ManualExpense> = withContext(Dispatchers.IO) {
        pManualDao.getAll().map { it.toDomain() }
    }

    // ── Skipped Transactions API ──────────────────────────────────────────

    suspend fun skipTransaction(transaction: Transaction, reason: String = "User skipped") = withContext(Dispatchers.IO) {
        val skipped = SkippedTransaction.fromTransaction(transaction, reason)
        pSkippedDao.insert(SkippedTransactionEntity.fromDomain(skipped))
        exportSkippedToJson()
        _skippedTransactions.value = pSkippedDao.getAll().map { it.toDomain() }

        // Skips apply to both databases: the skip record lives in the shared
        // persistent DB and both sync paths honor it.
        if (transaction.messageId != 0L) {
            transactionDao.deleteByMessageId(transaction.messageId)
            aiTransactionDao.deleteByMessageId(transaction.messageId)
        }
        transactionDao.deleteById(transaction.id)
        aiTransactionDao.deleteById(transaction.id)
    }

    suspend fun unskipTransaction(skipped: SkippedTransaction) = withContext(Dispatchers.IO) {
        // Remove from persistent DB using matching criteria
        if (skipped.originalMessageId != 0L) {
            pSkippedDao.deleteByMessageId(skipped.originalMessageId)
            // Forget its scan verdict too, so the next AI scan re-judges it.
            aiScannedDao.deleteByMessageId(skipped.originalMessageId)
        }
        if (skipped.rawBody.isNotBlank()) {
            pSkippedDao.deleteBySenderAndBody(skipped.sender, skipped.rawBody)
            aiScannedDao.deleteBySenderAndBody(skipped.sender, skipped.rawBody)
        }
        pSkippedDao.deleteBySenderAmountTimestamp(skipped.sender, skipped.amount, skipped.timestamp)

        exportSkippedToJson()
        _skippedTransactions.value = pSkippedDao.getAll().map { it.toDomain() }
        // Re-sync inbox so this transaction is immediately restored to active log
        syncTransactionsFromSms()
    }

    suspend fun getSkippedTransactions(): List<SkippedTransaction> = withContext(Dispatchers.IO) {
        pSkippedDao.getAll().map { it.toDomain() }
    }

    // ── Message Templates API ─────────────────────────────────────────────

    fun getMessageTemplates(): List<MessageTemplate> {
        return _messageTemplates.value.ifEmpty { MessageTemplate.defaultTemplates }
    }

    suspend fun loadMessageTemplates(): List<MessageTemplate> = withContext(Dispatchers.IO) {
        val templates = pTemplateDao.getAll().map { it.toDomain() }
        if (templates.isEmpty()) {
            // Seed defaults if empty
            val defaults = MessageTemplate.defaultTemplates
            pTemplateDao.upsertAll(defaults.map { MessageTemplateEntity.fromDomain(it) })
            _messageTemplates.value = defaults
            exportTemplatesToJson()
            defaults
        } else {
            _messageTemplates.value = templates
            templates
        }
    }

    suspend fun saveMessageTemplate(template: MessageTemplate) = withContext(Dispatchers.IO) {
        pTemplateDao.upsert(MessageTemplateEntity.fromDomain(template))
        exportTemplatesToJson()
        _messageTemplates.value = pTemplateDao.getAll().map { it.toDomain() }
    }

    suspend fun deleteMessageTemplate(id: String) = withContext(Dispatchers.IO) {
        pTemplateDao.deleteById(id)
        exportTemplatesToJson()
        _messageTemplates.value = pTemplateDao.getAll().map { it.toDomain() }
    }

    suspend fun toggleMessageTemplate(id: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        pTemplateDao.toggleEnabled(id, isEnabled)
        exportTemplatesToJson()
        _messageTemplates.value = pTemplateDao.getAll().map { it.toDomain() }
    }

    // ── Monitored Banks API ───────────────────────────────────────────────

    /**
     * Synchronizes monitored banks from persistent DB to internal Room bank_senders table.
     */
    private suspend fun syncMonitoredBanksFromDb() {
        try {
            val persistentBanks = pBankDao.getAll().map { it.toDomain() }
            val persistentSenderIds = persistentBanks.map { it.senderId.lowercase() }.toSet()

            // Remove senders from Room that are no longer in persistent DB
            val allDbSenders = bankSenderDao.getAllSendersSync()
            for (dbSender in allDbSenders) {
                if (!persistentSenderIds.contains(dbSender.senderId.lowercase())) {
                    bankSenderDao.delete(dbSender)
                }
            }

            // Upsert all senders from persistent DB into internal Room
            for (bank in persistentBanks) {
                val existing = bankSenderDao.getBySenderId(bank.senderId)
                if (existing == null) {
                    bankSenderDao.insert(BankSenderEntity.fromDomain(bank))
                } else {
                    bankSenderDao.update(
                        existing.copy(
                            displayName = bank.displayName,
                            isMonitored = bank.isMonitored,
                            customRegex = bank.customRegex
                        )
                    )
                }
            }
        } catch (e: Exception) {
            System.err.println("Error syncing monitored banks from DB: ${e.message}")
        }
    }

    suspend fun getMonitoredBanks(): List<BankSender> = withContext(Dispatchers.IO) {
        val banks = pBankDao.getAll().map { it.toDomain() }
        if (banks.isEmpty()) {
            val defaults = BankSender.defaultSenders
            pBankDao.upsertAll(defaults.map { MonitoredBankEntity.fromDomain(it) })
            exportBanksToJson()
            defaults
        } else {
            banks
        }
    }

    /**
     * Discovers all sender addresses in SMS inbox so user can pick which ones are their banks.
     */
    suspend fun discoverSendersFromInbox(): List<DiscoveredSender> = withContext(Dispatchers.IO) {
        smsReader.discoverSenders()
    }

    /** Full history (newest first) for the assistant's system prompt. Follows the active mode. */
    suspend fun getAllTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        if (_parseMode.value == ParseMode.AI) {
            aiTransactionDao.getAllSync().map { it.toDomain() }
        } else {
            transactionDao.getAllTransactionsFlow().first().map { it.toDomain() }
        }
    }

    /** Filtered log. Follows the active mode: AI database in AI mode, manual otherwise. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFilteredTransactions(
        startTime: Long,
        endTime: Long,
        type: TransactionType? = null,
        sender: String? = null,
        category: String? = null,
        searchQuery: String? = null,
        manualOnly: Boolean = false
    ): Flow<List<Transaction>> {
        val typeName = if (type == TransactionType.UNKNOWN || type == null) null else type.name
        val senderName = if (sender.isNullOrBlank() || sender == "ALL") null else sender
        val categoryName = if (category.isNullOrBlank() || category == "ALL") null else category
        val query = if (searchQuery.isNullOrBlank()) null else searchQuery.trim()
        return parseMode.flatMapLatest { mode ->
            if (mode == ParseMode.AI) {
                aiTransactionDao.getFilteredTransactionsFlow(
                    startTime, endTime, typeName, senderName, categoryName, query, manualOnly
                ).map { list -> list.map { it.toDomain() } }
            } else {
                transactionDao.getFilteredTransactionsFlow(
                    startTime, endTime, typeName, senderName, categoryName, query, manualOnly
                ).map { list -> list.map { it.toDomain() } }
            }
        }
    }

    /** Summary dashboard. Follows the active mode. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSummaryReport(startTime: Long, endTime: Long): Flow<SummaryReport> {
        return parseMode.flatMapLatest { mode ->
            if (mode == ParseMode.AI) {
                combineSummaryReport(
                    aiTransactionDao.getTotalExpenseFlow(startTime, endTime),
                    aiTransactionDao.getTotalIncomeFlow(startTime, endTime),
                    aiTransactionDao.getBankExpenseSummaryFlow(startTime, endTime),
                    aiTransactionDao.getCategoryExpenseSummaryFlow(startTime, endTime),
                    aiTransactionDao.getMonthlyTrendsFlow()
                )
            } else {
                combineSummaryReport(
                    transactionDao.getTotalExpenseFlow(startTime, endTime),
                    transactionDao.getTotalIncomeFlow(startTime, endTime),
                    transactionDao.getBankExpenseSummaryFlow(startTime, endTime),
                    transactionDao.getCategoryExpenseSummaryFlow(startTime, endTime),
                    transactionDao.getMonthlyTrendsFlow()
                )
            }
        }
    }

    private fun combineSummaryReport(
        totalExpenseFlow: Flow<Double>,
        totalIncomeFlow: Flow<Double>,
        bankSummaryFlow: Flow<List<BankExpenseDbSummary>>,
        categorySummaryFlow: Flow<List<CategoryDbSummary>>,
        monthlyTrendFlow: Flow<List<MonthlyDbTrend>>
    ): Flow<SummaryReport> {
        return combine(
            totalExpenseFlow,
            totalIncomeFlow,
            bankSummaryFlow,
            categorySummaryFlow,
            monthlyTrendFlow
        ) { expense, income, banks, categories, trends ->
            val totalExp = expense ?: 0.0
            val totalInc = income ?: 0.0
            val net = totalInc - totalExp

            val bankShares = banks.map {
                BankExpenseShare(
                    sender = it.sender,
                    totalExpense = it.totalExpense ?: 0.0,
                    totalIncome = it.totalIncome ?: 0.0,
                    transactionCount = it.transactionCount
                )
            }

            val categoryShares = categories.map {
                val catAmount = it.totalAmount ?: 0.0
                val pct = if (totalExp > 0) ((catAmount / totalExp) * 100).toFloat() else 0f
                CategoryShare(
                    category = it.category,
                    totalAmount = catAmount,
                    count = it.count,
                    percentage = pct
                )
            }

            val monthlyTrends = trends.map {
                MonthlyTrend(
                    yearMonth = it.yearMonth,
                    monthLabel = formatYearMonth(it.yearMonth),
                    totalExpense = it.totalExpense ?: 0.0,
                    totalIncome = it.totalIncome ?: 0.0
                )
            }

            SummaryReport(
                totalExpense = totalExp,
                totalIncome = totalInc,
                netSavings = net,
                totalTransactions = bankShares.sumOf { it.transactionCount },
                bankShares = bankShares,
                categoryShares = categoryShares,
                monthlyTrends = monthlyTrends
            )
        }
    }

    fun getSendersWithStats(): Flow<List<BankSender>> {
        return bankSenderDao.getSendersWithStatsFlow().map { list ->
            list.map {
                BankSender(
                    senderId = it.senderId,
                    displayName = it.displayName,
                    isMonitored = it.isMonitored,
                    customRegex = it.customRegex,
                    totalTransactionsCount = it.totalTransactionsCount,
                    lastTransactionTime = it.lastTransactionTime
                )
            }
        }
    }

    suspend fun setSenderMonitored(senderId: String, isMonitored: Boolean) = withContext(Dispatchers.IO) {
        bankSenderDao.setMonitored(senderId, isMonitored)
        pBankDao.toggleMonitored(senderId, isMonitored)
        exportBanksToJson()
    }

    suspend fun addCustomSender(sender: BankSender) = withContext(Dispatchers.IO) {
        bankSenderDao.insert(BankSenderEntity.fromDomain(sender))
        pBankDao.upsert(MonitoredBankEntity.fromDomain(sender))
        exportBanksToJson()
    }

    suspend fun updateSender(oldSenderId: String, updated: BankSender) = withContext(Dispatchers.IO) {
        if (!oldSenderId.equals(updated.senderId, ignoreCase = true)) {
            val oldEntity = bankSenderDao.getBySenderId(oldSenderId)
            if (oldEntity != null) {
                bankSenderDao.delete(oldEntity)
            }
            bankSenderDao.insert(BankSenderEntity.fromDomain(updated))
            pBankDao.deleteBySenderId(oldSenderId)
        } else {
            bankSenderDao.update(BankSenderEntity.fromDomain(updated))
        }
        pBankDao.upsert(MonitoredBankEntity.fromDomain(updated))
        exportBanksToJson()
    }

    suspend fun deleteSender(sender: BankSender) = withContext(Dispatchers.IO) {
        val entity = bankSenderDao.getBySenderId(sender.senderId)
        if (entity != null) {
            bankSenderDao.delete(entity)
        }
        pBankDao.deleteBySenderId(sender.senderId)
        exportBanksToJson()
    }

    fun getStorageDirectoryPath(): String = fileManager.getStorageDirectoryPath()

    fun isPublicStorageActive(): Boolean = fileManager.isPublicStorageActive()

    fun getPersistentDbPath(): String {
        return try {
            persistentDb.openHelper.readableDatabase.path ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    suspend fun clearAllPersistenceFiles() = withContext(Dispatchers.IO) {
        // Clear persistent DB tables
        pManualDao.deleteAll()
        pSkippedDao.deleteAll()
        pTemplateDao.deleteAll()
        pBankDao.deleteAll()

        // Clear JSON backup files
        fileManager.clearAllFiles()

        _skippedTransactions.value = emptyList()

        // Re-seed defaults
        val defaultTemplates = MessageTemplate.defaultTemplates
        pTemplateDao.upsertAll(defaultTemplates.map { MessageTemplateEntity.fromDomain(it) })
        _messageTemplates.value = defaultTemplates

        val defaultBanks = BankSender.defaultSenders
        pBankDao.upsertAll(defaultBanks.map { MonitoredBankEntity.fromDomain(it) })

        // Sync defaults to internal Room
        syncMonitoredBanksFromDb()
    }

    /**
     * Refreshes cached state from persistent DB. Called on tab switches, etc.
     */
    suspend fun refreshFromFiles() = withContext(Dispatchers.IO) {
        val templates = pTemplateDao.getAll().map { it.toDomain() }
        _messageTemplates.value = templates.ifEmpty { MessageTemplate.defaultTemplates }
        val skipped = pSkippedDao.getAll().map { it.toDomain() }
        _skippedTransactions.value = skipped
        syncManualExpensesFromDb()
        syncMonitoredBanksFromDb()
    }

    /** Category edits and deletes act on the visible (active-mode) database. */
    suspend fun updateTransactionCategory(transactionId: Long, newCategory: String) {
        if (_parseMode.value == ParseMode.AI) {
            val existing = aiTransactionDao.getById(transactionId) ?: return
            aiTransactionDao.update(existing.copy(category = newCategory))
        } else {
            val existing = transactionDao.getById(transactionId) ?: return
            transactionDao.update(existing.copy(category = newCategory))
        }
    }

    suspend fun deleteTransaction(transactionId: Long) {
        if (_parseMode.value == ParseMode.AI) {
            aiTransactionDao.deleteById(transactionId)
        } else {
            transactionDao.deleteById(transactionId)
        }
    }

    suspend fun deleteAllTransactions() {
        if (_parseMode.value == ParseMode.AI) {
            aiTransactionDao.deleteAll()
        } else {
            transactionDao.deleteAll()
        }
    }

    // ── JSON Backup Export Methods ─────────────────────────────────────────

    private suspend fun exportManualExpensesToJson() {
        try {
            val expenses = pManualDao.getAll().map { it.toDomain() }
            val jsonArray = org.json.JSONArray()
            expenses.forEach { jsonArray.put(it.toJsonObject()) }
            fileManager.writeJsonBackup("manual_expenses.json", jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private suspend fun exportSkippedToJson() {
        try {
            val skipped = pSkippedDao.getAll().map { it.toDomain() }
            val jsonArray = org.json.JSONArray()
            skipped.forEach { jsonArray.put(it.toJsonObject()) }
            fileManager.writeJsonBackup("skipped_transactions.json", jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private suspend fun exportTemplatesToJson() {
        try {
            val templates = pTemplateDao.getAll().map { it.toDomain() }
            val jsonArray = org.json.JSONArray()
            templates.forEach { jsonArray.put(it.toJsonObject()) }
            fileManager.writeJsonBackup("message_templates.json", jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private suspend fun exportBanksToJson() {
        try {
            val banks = pBankDao.getAll().map { it.toDomain() }
            val jsonArray = org.json.JSONArray()
            banks.forEach { jsonArray.put(it.toJsonObject()) }
            fileManager.writeJsonBackup("monitored_banks.json", jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private fun formatYearMonth(ym: String): String {
        return try {
            val parts = ym.split("-")
            val year = parts[0]
            val month = parts[1].toInt()
            val monthName = when (month) {
                1 -> "Jan"
                2 -> "Feb"
                3 -> "Mar"
                4 -> "Apr"
                5 -> "May"
                6 -> "Jun"
                7 -> "Jul"
                8 -> "Aug"
                9 -> "Sep"
                10 -> "Oct"
                11 -> "Nov"
                12 -> "Dec"
                else -> ym
            }
            "$monthName $year"
        } catch (e: Exception) {
            ym
        }
    }
}

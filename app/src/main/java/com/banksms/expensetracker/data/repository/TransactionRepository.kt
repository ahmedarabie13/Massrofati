package com.banksms.expensetracker.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.banksms.expensetracker.data.cloud.CloudDocs
import com.banksms.expensetracker.data.cloud.FirestoreStore
import com.banksms.expensetracker.data.cloud.ScanVerdict
import com.banksms.expensetracker.data.cloud.TxDoc
import com.banksms.expensetracker.data.cloud.TxOrigin
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.llm.ChatEngine
import com.banksms.expensetracker.data.llm.EngineState
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class SyncResult(
    val messagesScanned: Int,
    val transactionsImported: Int,
    val errors: List<String> = emptyList()
)

/**
 * Firestore-backed repository. Public API is unchanged from the Room era,
 * so ViewModels and screens work untouched; all SQL moved to in-memory
 * computation over the snapshot-backed collections in [FirestoreStore].
 *
 * One user = one repository instance ([BankSmsApp.openSession]); [close]
 * detaches all snapshot listeners (call on sign-out / account switch).
 */
class TransactionRepository(
    private val store: FirestoreStore,
    private val smsReader: SmsReader,
    prefs: SharedPreferences,
    /** Owning account: parseMode and all reads/writes are scoped to it. */
    private val uid: String,
    /** Provides the shared on-device engine; null where unavailable (e.g. receiver fallback). */
    private val engineProvider: (() -> ChatEngine)? = null,
    /**
     * Builds a FRESH engine per scan batch (single owner per instance, so a
     * stopped scan can abandon in-flight batches safely). Null where unavailable.
     */
    private val engineFactory: (() -> ChatEngine)? = null
) {

    companion object {
        private const val TAG = "AiScan"
    }

    /** Parse mode is per-user (not per-device): each account keeps its own. */
    private val prefsParseModeKey = "parsing_mode_$uid"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Origins visible in each mode (manual mirrors show in both). */
    private fun visibleOrigins(mode: ParseMode): Set<String> =
        if (mode == ParseMode.AI) setOf(TxOrigin.AI, TxOrigin.MANUAL)
        else setOf(TxOrigin.REGEX, TxOrigin.MANUAL)

    // ── Parsing mode (manual regex vs on-device AI) ──────────────────────

    private val modePrefs = prefs

    private val _parseMode = MutableStateFlow(
        ParseMode.fromStored(
            prefs.getString(prefsParseModeKey, null)
                ?: prefs.getString("parsing_mode", null)?.also {
                    // One-time carry-over from the pre-per-user key.
                    prefs.edit().putString(prefsParseModeKey, it).apply()
                }
        )
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
        modePrefs.edit().putString(prefsParseModeKey, mode.name).apply()
        _parseMode.value = mode
    }

    /** True when the LLM file is present (real engine), so AI parsing can run. */
    fun isAiEngineAvailable(): Boolean =
        try {
            engineProvider?.invoke()?.isDemo == false
        } catch (_: Exception) {
            false
        }
    /** Wipes AI SMS rows + scan verdicts; manuals, skips and regex rows are untouched. */
    suspend fun clearAiTransactions() = withContext(Dispatchers.IO) {
        store.deleteTransactionsByOrigin(TxOrigin.AI)
        store.deleteAllVerdicts()
        _aiScanProgress.value = null
    }

    /** Detaches snapshot listeners and cancels background work. */
    fun close() {
        scope.cancel()
        store.close()
    }

    /** Waits for the first snapshot of every collection (cache counts). */
    suspend fun awaitReady(timeoutMs: Long = 8000): Boolean = store.awaitReady(timeoutMs)

    init {
        scope.launch {
            try {
                store.awaitReady(15000)
                ensureSeeded()
                dedupeSkippedRecords()
                syncManualMirrors()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "repository init refresh failed", e)
            }
        }
    }

    /**
     * One-time self-heal: collapse byte-identical skip records (repeat taps
     * used to pile them up), keeping the newest. Distinct skips are untouched.
     */
    private suspend fun dedupeSkippedRecords() {
        val all = try {
            store.fetchSkippedOnce()
        } catch (_: Exception) {
            return
        }
        if (all.size < 2) return
        val dupIds = all.groupBy { (_, s) ->
            if (s.originalMessageId != 0L) "id:${s.originalMessageId}"
            else "fb:${s.sender}|${s.amount}|${s.timestamp}|${s.rawBody}"
        }.values.filter { it.size > 1 }
            .flatMap { group -> group.sortedByDescending { it.second.skippedAt }.drop(1) }
            .map { it.first }
        if (dupIds.isNotEmpty()) {
            Log.d(TAG, "deduping ${dupIds.size} duplicate skip records")
            runCatching { store.deleteSkippedDocs(dupIds) }
        }
    }

    private suspend fun ensureSeeded() {
        if (store.templates.value.isEmpty()) {
            runCatching { store.setTemplates(MessageTemplate.defaultTemplates) }
        }
        if (store.banks.value.isEmpty()) {
            runCatching { store.setBanks(BankSender.defaultSenders) }
        }
    }

    // ── Single-message AI rescan ──────────────────────────────────────────

    sealed interface RescanResult {
        data class Updated(val transaction: Transaction) : RescanResult
        data object NotTransaction : RescanResult
        data class Failed(val reason: String) : RescanResult
    }

    /**
     * Re-sends ONE stored message to the model and overwrites the row with
     * the fresh parse. AI mode only, SMS rows only. This is an explicit user
     * action, so it always hits the model (bypasses verdict + money-hint
     * gates); the new verdict is recorded for future bulk scans.
     */
    suspend fun rescanTransaction(transactionId: Long): RescanResult =
        withContext(Dispatchers.IO) {
            if (_parseMode.value != ParseMode.AI) {
                return@withContext RescanResult.Failed("Switch to AI parsing mode to re-scan.")
            }
            val existing = store.transactions.value
                .firstOrNull { it.tx.id == transactionId && it.origin == TxOrigin.AI }
                ?.tx ?: return@withContext RescanResult.Failed("Transaction not found.")
            if (existing.isManual) {
                return@withContext RescanResult.Failed("Manual entries have no SMS to re-scan.")
            }
            if (existing.rawBody.isBlank()) {
                return@withContext RescanResult.Failed("No original message stored for this transaction.")
            }
            val engine = engineProvider?.invoke()
            if (engine == null || engine.isDemo) {
                return@withContext RescanResult.Failed(
                    "AI parsing needs the on-device model. Download it from the Assistant tab first."
                )
            }
            try {
                val parsed = AiSmsParser.parseSingle(
                    engine,
                    RawSms(existing.messageId, existing.sender, existing.rawBody, existing.timestamp)
                ) ?: run {
                    store.setVerdict(
                        ScanVerdict(
                            existing.messageId, existing.sender, existing.timestamp,
                            existing.rawBody, false
                        )
                    )
                    return@withContext RescanResult.NotTransaction
                }
                val updated = parsed.copy(
                    id = existing.id,
                    docId = existing.docId,
                    messageId = existing.messageId
                )
                store.setTransaction(updated, TxOrigin.AI)
                store.setVerdict(
                    ScanVerdict(
                        existing.messageId, existing.sender, existing.timestamp,
                        existing.rawBody, true
                    )
                )
                RescanResult.Updated(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RescanResult.Failed(e.message ?: "Re-scan failed.")
            }
        }

    val skippedTransactions: StateFlow<List<SkippedTransaction>> = store.skipped

    val messageTemplates: StateFlow<List<MessageTemplate>> = store.templates

    /**
     * Processes an incoming real-time SMS from BroadcastReceiver with full deduplication and skip filtering.
     * @return true if a new transaction was inserted, false if skipped or already exists.
     */
    suspend fun processIncomingSms(
        sender: String,
        body: String,
        timestamp: Long
    ): Boolean = withContext(Dispatchers.IO) {
        store.awaitReady()
        val monitored = store.banks.value.filter { it.isMonitored }
            .map { it.senderId.lowercase() }
            .toSet()

        val senderLower = sender.lowercase()
        val isMonitored = monitored.any {
            senderLower == it || senderLower.contains(it) || it.contains(senderLower)
        }
        if (!isMonitored) return@withContext false

        // Skip matching needs the parsed amount, so it happens post-parse
        // inside each pipeline (exact messageId matches are re-checked there).
        return@withContext if (_parseMode.value == ParseMode.AI) {
            processIncomingSmsAi(sender, body, timestamp)
        } else {
            val enabledTemplates = store.templates.value.filter { it.isEnabled }
            processIncomingSmsManual(sender, body, timestamp, enabledTemplates)
        }
    }

    /** Regex path: parse + insert into the cloud transactions collection. */
    private suspend fun processIncomingSmsManual(
        sender: String,
        body: String,
        timestamp: Long,
        enabledTemplates: List<MessageTemplate>
    ): Boolean {
        val parsed = BankSmsParser.parse(body, sender, enabledTemplates) ?: return false

        if (isSkipped(store.skipped.value, timestamp, sender, parsed.amount, timestamp, body)) {
            return false
        }

        // Prevent inserting if this transaction is already present
        val docs = docsInOrigins(setOf(TxOrigin.REGEX, TxOrigin.MANUAL))
        val existing = findDuplicate(
            docs,
            sender = sender,
            amount = parsed.amount,
            type = parsed.type.name,
            rawBody = body,
            timestamp = timestamp
        )
        if (existing != null) {
            return false
        }

        val docId = CloudDocs.regexDocId(timestamp)
        val transaction = parsed.toTransaction(
            messageId = timestamp,
            sender = sender,
            timestamp = timestamp,
            rawBody = body
        ).copy(docId = docId, id = CloudDocs.stableId(docId))
        store.setTransaction(transaction, TxOrigin.REGEX)
        return true
    }

    /**
     * AI path: single message to the on-device model, result into the cloud.
     * No model (or no engine) means the SMS is dropped — it must NOT fall
     * back to manual parsing, or the two pipelines would mix.
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

        if (isSkipped(store.skipped.value, parsed.messageId, sender, parsed.amount, timestamp, body)) {
            return false
        }

        val docs = docsInOrigins(setOf(TxOrigin.AI, TxOrigin.MANUAL))
        val existing = findDuplicate(
            docs,
            sender = sender,
            amount = parsed.amount,
            type = parsed.type.name,
            rawBody = body,
            timestamp = timestamp
        )
        if (existing != null) return false

        val docId = CloudDocs.aiDocId(parsed.messageId)
        store.setTransaction(
            parsed.copy(docId = docId, id = CloudDocs.stableId(docId)),
            TxOrigin.AI
        )
        return true
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
     * Scans both SMS messages and the cloud manual expenses.
     * Deduplicates against existing transactions and respects skipped transaction records.
     */
    private suspend fun doSyncTransactionsFromSms(): SyncResult = withContext(Dispatchers.IO) {
        try {
            store.awaitReady()

            // 1. Clean up any historical duplicate transactions (visible set)
            deleteDuplicates(docsInOrigins(visibleOrigins(_parseMode.value)))

            // 2. Templates (seeded at init; refresh the cached flow)
            val templates = store.templates.value.ifEmpty {
                ensureSeeded()
                MessageTemplate.defaultTemplates
            }

            // 3. Enforce skipped transactions: remove matching rows from the log
            val skippedList = store.skipped.value
            for (skipped in skippedList) {
                ensureActive() // Stop takes effect between rows.
                if (skipped.originalMessageId != 0L) {
                    runCatching {
                        store.deleteTransactions(
                            listOf(
                                CloudDocs.regexDocId(skipped.originalMessageId),
                                CloudDocs.aiDocId(skipped.originalMessageId)
                            )
                        )
                    }
                }
                val dup = findDuplicate(
                    store.transactions.value,
                    sender = skipped.sender,
                    amount = skipped.amount,
                    type = skipped.type.name,
                    rawBody = skipped.rawBody,
                    timestamp = skipped.timestamp
                )
                if (dup != null) {
                    runCatching { store.deleteTransaction(dup.tx.docId) }
                }
            }

            // 4. Mirror manual expenses into the log
            syncManualMirrors()

            // 5. Monitored banks (single source now — nothing to synchronize)
            val monitored = store.banks.value
            if (monitored.none { it.isMonitored }) {
                return@withContext SyncResult(0, 0, listOf("No monitored bank senders configured. Please enable banks in Settings."))
            }

            val monitoredSenderIds = monitored.filter { it.isMonitored }
                .map { it.senderId }.toSet()

            // AI mode never touches the regex pipeline: raw inbox rows go to
            // the model pool in batches of 20 and land in the cloud.
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
                if (isSkipped(
                        store.skipped.value,
                        transaction.messageId, transaction.sender,
                        transaction.amount, transaction.timestamp, transaction.rawBody
                    )
                ) {
                    continue
                }

                val docId = CloudDocs.regexDocId(transaction.messageId)
                // Check 1: Already exists with this exact messageId?
                if (store.transactions.value.any { it.tx.docId == docId }) {
                    continue
                }

                // Check 2: Was it already inserted by real-time receiver (which used timestamp as messageId)?
                // Receiver rows win: an inbox row matching one is the same SMS.
                val existingDuplicate = findDuplicate(
                    docsInOrigins(setOf(TxOrigin.REGEX, TxOrigin.MANUAL)),
                    sender = transaction.sender,
                    amount = transaction.amount,
                    type = transaction.type.name,
                    rawBody = transaction.rawBody,
                    timestamp = transaction.timestamp
                )
                if (existingDuplicate == null) {
                    store.setTransaction(
                        transaction.copy(docId = docId, id = CloudDocs.stableId(docId)),
                        TxOrigin.REGEX
                    )
                    newlyImported++
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
     * own private engine) -> cloud. Rows already stored or skipped are
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
            deleteDuplicates(docsInOrigins(setOf(TxOrigin.AI, TxOrigin.MANUAL)))

            // Monitored senders only (readRawBankMessages filters by them).
            val raw = smsReader.readRawBankMessages(monitoredSenderIds)
            if (raw.isEmpty()) return SyncResult(0, 0)
            tracker.setTotals(raw)
            _aiScanProgress.value = tracker.snapshot()

            // Attribute every row up front: no-currency / duplicate / skipped /
            // already-judged never cost inference; only the rest goes to the model.
            val storedIds = store.transactions.value.map { it.tx.messageId }.toSet()
            val skippedNow = store.skipped.value
            val verdictsNow = store.verdicts.value
            val fresh = mutableListOf<RawSms>()
            for (sms in raw) {
                when {
                    !AiSmsParser.hasMoneyHint(sms.body) -> tracker.markNoCurrency(sms)
                    sms.messageId in storedIds -> tracker.markDuplicate(sms)
                    isSkipped(
                        skippedNow, sms.messageId, sms.sender,
                        Double.NaN, sms.timestamp, sms.body
                    ) ->
                        tracker.markSkipped(sms)
                    verdictsNow.containsKey(sms.messageId) -> tracker.markAlreadyScanned(sms)
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
            val skippedNow = store.skipped.value
            val inserted = mutableListOf<Transaction>()
            val parsedIds = parsed.map { it.messageId }.toSet()
            val verdictBatch = mutableListOf<ScanVerdict>()
            for (tx in parsed) {
                if (
                    isSkipped(
                        skippedNow, tx.messageId, tx.sender,
                        tx.amount, tx.timestamp, tx.rawBody
                    )
                ) {
                    tracker.markSkipped(
                        RawSms(tx.messageId, tx.sender, tx.rawBody, tx.timestamp)
                    )
                } else {
                    val dup = findDuplicate(
                        docsInOrigins(setOf(TxOrigin.AI, TxOrigin.MANUAL)),
                        sender = tx.sender,
                        amount = tx.amount,
                        type = tx.type.name,
                        rawBody = tx.rawBody,
                        timestamp = tx.timestamp
                    )
                    if (dup == null) {
                        val docId = CloudDocs.aiDocId(tx.messageId)
                        inserted.add(tx.copy(docId = docId, id = CloudDocs.stableId(docId)))
                    }
                }
                // Verdict recorded per row AFTER handling, so every row is
                // consistent (both verdict+insert present, or neither).
                verdictBatch.add(
                    ScanVerdict(tx.messageId, tx.sender, tx.timestamp, tx.rawBody, true)
                )
            }
            batch.filter { it.messageId !in parsedIds }.forEach { row ->
                verdictBatch.add(
                    ScanVerdict(row.messageId, row.sender, row.timestamp, row.body, false)
                )
            }
            // One batched write per batch: kinds to Firestore write quotas.
            if (inserted.isNotEmpty()) store.setTransactions(inserted, TxOrigin.AI)
            if (verdictBatch.isNotEmpty()) store.setVerdicts(verdictBatch)
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
     * Mirrors cloud manual expenses into the log (origin=manual, visible in
     * both modes). Skipped manuals stay skipped: the ManualExpense record is
     * kept (so unskip restores it) but no mirror doc is written.
     */
    private suspend fun syncManualMirrors() {
        // Never prune on cold snapshots: an empty manuals snapshot before
        // the first server read would look like "everything deleted".
        store.awaitReady()
        val manualExpenses = store.manualExpenses.value
        val currentIds = manualExpenses.map { it.id }.toSet()
        val skippedNow = store.skipped.value

        // Remove mirrors whose manual source is gone.
        val staleMirrors = store.transactions.value
            .filter { it.origin == TxOrigin.MANUAL && it.tx.manualId !in currentIds }
            .map { it.tx.docId }
        if (staleMirrors.isNotEmpty()) {
            runCatching { store.deleteTransactions(staleMirrors) }
        }

        for (expense in manualExpenses) {
            // Stop takes effect between rows (also aborts stale refreshes).
            currentCoroutineContext().ensureActive()
            val base = expense.toTransaction()
            val docId = CloudDocs.manualDocId(expense.id)
            val transaction = base.copy(docId = docId, id = CloudDocs.stableId(docId))
            if (isSkipped(
                    skippedNow,
                    transaction.messageId, transaction.sender,
                    transaction.amount, transaction.timestamp, transaction.rawBody
                )
            ) {
                runCatching { store.deleteTransaction(docId) }
                continue
            }
            runCatching { store.setTransaction(transaction, TxOrigin.MANUAL) }
        }
    }

    // ── In-memory query engine (replaces the Room SQL) ────────────────────

    private fun docsInOrigins(origins: Set<String>): List<TxDoc> =
        store.transactions.value.filter { it.origin in origins }

    /**
     * Same predicate as the old findDuplicate query: within ±2 min AND
     * (same body OR (same amount AND same type)).
     */
    private fun findDuplicate(
        docs: List<TxDoc>,
        sender: String,
        amount: Double,
        type: String,
        rawBody: String,
        timestamp: Long
    ): TxDoc? = docs.firstOrNull { doc ->
        val tx = doc.tx
        tx.sender == sender &&
            kotlin.math.abs(tx.timestamp - timestamp) < 120000 &&
            (tx.rawBody == rawBody || (tx.amount == amount && tx.type.name == type))
    }

    /**
     * Same grouping as the old deleteDuplicates: sender + amount + body +
     * minute bucket. Keeps the lowest id, deletes the rest.
     */
    private suspend fun deleteDuplicates(docs: List<TxDoc>) {
        val dupIds = docs.groupBy {
            "${it.tx.sender}|${it.tx.amount}|${it.tx.rawBody}|${it.tx.timestamp / 60000}"
        }.values.filter { it.size > 1 }
            .flatMap { group -> group.sortedBy { it.tx.id }.drop(1) }
            .map { it.tx.docId }
        if (dupIds.isNotEmpty()) {
            runCatching { store.deleteTransactions(dupIds) }
        }
    }

    /**
     * Skip matching. Amount equality is REQUIRED in every fuzzy rule: the
     * same transaction always carries the same amount, while different rows
     * can share sender + body (manual entries without a note all read
     * "Manual entry recorded on app"). Without this, skipping one manual
     * hid every manual with the same payment method.
     */
    private fun isSkipped(
        skippedList: List<SkippedTransaction>,
        messageId: Long,
        sender: String,
        amount: Double,
        timestamp: Long,
        rawBody: String
    ): Boolean {
        if (skippedList.isEmpty()) return false
        return skippedList.any { skipped ->
            (messageId != 0L && skipped.originalMessageId == messageId) ||
            (rawBody.isNotBlank() && skipped.rawBody.isNotBlank() && skipped.sender == sender &&
                skipped.amount == amount && skipped.rawBody == rawBody &&
                kotlin.math.abs(skipped.timestamp - timestamp) < 60000) ||
            (skipped.sender == sender && skipped.amount == amount &&
                kotlin.math.abs(skipped.timestamp - timestamp) < 60000 &&
                rawBody.contains(skipped.amount.toInt().toString()))
        }
    }

    private fun TxDoc.isVisible(mode: ParseMode): Boolean =
        origin in visibleOrigins(mode)

    // ── Manual Expenses API ───────────────────────────────────────────────

    suspend fun addManualExpense(expense: ManualExpense) = withContext(Dispatchers.IO) {
        store.setManual(expense)
        val docId = CloudDocs.manualDocId(expense.id)
        val transaction = expense.toTransaction()
            .copy(docId = docId, id = CloudDocs.stableId(docId))
        store.setTransaction(transaction, TxOrigin.MANUAL)
    }

    suspend fun updateManualExpense(expense: ManualExpense) = withContext(Dispatchers.IO) {
        store.setManual(expense)
        val docId = CloudDocs.manualDocId(expense.id)
        val transaction = expense.toTransaction()
            .copy(docId = docId, id = CloudDocs.stableId(docId))
        store.setTransaction(transaction, TxOrigin.MANUAL)
    }

    suspend fun deleteManualExpense(manualId: String) = withContext(Dispatchers.IO) {
        store.deleteManual(manualId)
        runCatching { store.deleteTransaction(CloudDocs.manualDocId(manualId)) }
    }

    suspend fun getManualExpenses(): List<ManualExpense> = withContext(Dispatchers.IO) {
        store.awaitReady()
        store.manualExpenses.value
    }

    // ── Skipped Transactions API ──────────────────────────────────────────

    suspend fun skipTransaction(transaction: Transaction, reason: String = "User skipped") =
        withContext(Dispatchers.IO) {
            val skipped = SkippedTransaction.fromTransaction(transaction, reason)
            // No duplicate skip records: repeat taps must not pile up rows
            // that later over-match (same sender+body) other transactions.
            val already = store.skipped.value.any { s ->
                (transaction.messageId != 0L && s.originalMessageId == transaction.messageId) ||
                (s.sender == transaction.sender && s.amount == transaction.amount &&
                    s.timestamp == transaction.timestamp && s.rawBody == transaction.rawBody)
            }
            if (!already) {
                store.addSkipped(skipped)
            }
            // Single collection now: deleting the doc removes it everywhere.
            if (transaction.docId.isNotBlank()) {
                runCatching { store.deleteTransaction(transaction.docId) }
            } else {
                runCatching {
                    store.deleteTransactions(
                        listOf(
                            CloudDocs.regexDocId(transaction.messageId),
                            CloudDocs.aiDocId(transaction.messageId)
                        )
                    )
                }
            }
        }

    suspend fun unskipTransaction(skipped: SkippedTransaction) = withContext(Dispatchers.IO) {
        // Same tight matching as isSkipped: unskipping one default-body
        // manual must not remove other manuals' skip records.
        store.deleteSkippedWhere { s ->
            (skipped.originalMessageId != 0L && s.originalMessageId == skipped.originalMessageId) ||
            (skipped.rawBody.isNotBlank() && s.sender == skipped.sender &&
                s.amount == skipped.amount && s.rawBody == skipped.rawBody &&
                kotlin.math.abs(s.timestamp - skipped.timestamp) < 60000) ||
            (s.sender == skipped.sender && s.amount == skipped.amount && s.timestamp == skipped.timestamp)
        }
        // Forget its scan verdict too, so the next AI scan re-judges it.
        if (skipped.originalMessageId != 0L) {
            runCatching { store.deleteVerdict(skipped.originalMessageId) }
        }
        if (skipped.rawBody.isNotBlank()) {
            runCatching { store.deleteVerdictsBySenderAndBody(skipped.sender, skipped.rawBody) }
        }
        // Re-sync inbox so this transaction is immediately restored to active log
        syncTransactionsFromSms()
    }

    suspend fun getSkippedTransactions(): List<SkippedTransaction> = withContext(Dispatchers.IO) {
        store.awaitReady()
        store.skipped.value
    }

    // ── Message Templates API ─────────────────────────────────────────────

    fun getMessageTemplates(): List<MessageTemplate> {
        return store.templates.value.ifEmpty { MessageTemplate.defaultTemplates }
    }

    suspend fun loadMessageTemplates(): List<MessageTemplate> = withContext(Dispatchers.IO) {
        store.awaitReady()
        val templates = store.templates.value
        if (templates.isEmpty()) {
            val defaults = MessageTemplate.defaultTemplates
            runCatching { store.setTemplates(defaults) }
            defaults
        } else {
            templates
        }
    }

    suspend fun saveMessageTemplate(template: MessageTemplate) = withContext(Dispatchers.IO) {
        store.setTemplate(template)
    }

    suspend fun deleteMessageTemplate(id: String) = withContext(Dispatchers.IO) {
        store.deleteTemplate(id)
    }

    suspend fun toggleMessageTemplate(id: String, isEnabled: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = store.templates.value.firstOrNull { it.id == id } ?: return@withContext
            store.setTemplate(existing.copy(isEnabled = isEnabled))
        }

    // ── Monitored Banks API ───────────────────────────────────────────────

    suspend fun getMonitoredBanks(): List<BankSender> = withContext(Dispatchers.IO) {
        store.awaitReady()
        val banks = store.banks.value
        if (banks.isEmpty()) {
            val defaults = BankSender.defaultSenders
            runCatching { store.setBanks(defaults) }
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
        val mode = _parseMode.value
        store.transactions.value
            .filter { it.isVisible(mode) }
            .sortedByDescending { it.tx.timestamp }
            .map { it.tx }
    }

    /** Filtered log. Follows the active mode. */
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
        val wantExpenseOnly = type == TransactionType.EXPENSE
        val wantIncomeOnly = type == TransactionType.INCOME
        val senderName = if (sender.isNullOrBlank() || sender == "ALL") null else sender
        val categoryName = if (category.isNullOrBlank() || category == "ALL") null else category
        val query = if (searchQuery.isNullOrBlank()) null else searchQuery.trim()
        return combine(parseMode, store.transactions) { mode, docs ->
            docs.asSequence()
                .filter { it.isVisible(mode) }
                .map { it.tx }
                .filter { it.timestamp in startTime..endTime }
                .filter {
                    when {
                        wantExpenseOnly -> it.type == TransactionType.EXPENSE
                        wantIncomeOnly -> it.type == TransactionType.INCOME
                        type == null || type == TransactionType.UNKNOWN -> true
                        else -> it.type == type
                    }
                }
                .filter { senderName == null || it.sender == senderName }
                .filter { categoryName == null || it.category == categoryName }
                .filter { tx ->
                    query == null || (tx.merchant?.contains(query, ignoreCase = true) == true) ||
                        tx.rawBody.contains(query, ignoreCase = true)
                }
                .filter { !manualOnly || it.isManual }
                .sortedByDescending { it.timestamp }
                .toList()
        }
    }

    /** Summary dashboard. Follows the active mode. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSummaryReport(startTime: Long, endTime: Long): Flow<SummaryReport> {
        return combine(parseMode, store.transactions) { mode, docs ->
            val visible = docs.filter { it.isVisible(mode) }.map { it.tx }
            val ranged = visible.filter { it.timestamp in startTime..endTime }
            val totalExp = ranged.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val totalInc = ranged.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

            val bankShares = ranged.groupBy { it.sender }.map { (sender, txs) ->
                BankExpenseShare(
                    sender = sender,
                    totalExpense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                    totalIncome = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                    transactionCount = txs.size
                )
            }.sortedByDescending { it.totalExpense }

            val categoryShares = ranged.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category }.map { (category, txs) ->
                    val catAmount = txs.sumOf { it.amount }
                    val pct = if (totalExp > 0) ((catAmount / totalExp) * 100).toFloat() else 0f
                    CategoryShare(
                        category = category,
                        totalAmount = catAmount,
                        count = txs.size,
                        percentage = pct
                    )
                }.sortedByDescending { it.totalAmount }

            val monthlyTrends = visible.groupBy { yearMonthOf(it.timestamp) }
                .map { (ym, txs) ->
                    MonthlyTrend(
                        yearMonth = ym,
                        monthLabel = formatYearMonth(ym),
                        totalExpense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                        totalIncome = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    )
                }.sortedBy { it.yearMonth }

            SummaryReport(
                totalExpense = totalExp,
                totalIncome = totalInc,
                netSavings = totalInc - totalExp,
                totalTransactions = bankShares.sumOf { it.transactionCount },
                bankShares = bankShares,
                categoryShares = categoryShares,
                monthlyTrends = monthlyTrends
            )
        }
    }

    fun getSendersWithStats(): Flow<List<BankSender>> {
        return combine(parseMode, store.banks, store.transactions) { mode, banks, docs ->
            val visible = docs.filter { it.isVisible(mode) }.map { it.tx }
            banks.map { bank ->
                val mine = visible.filter { it.sender == bank.senderId }
                bank.copy(
                    totalTransactionsCount = mine.size,
                    lastTransactionTime = mine.maxOfOrNull { it.timestamp }
                )
            }.sortedBy { it.senderId }
        }
    }

    suspend fun setSenderMonitored(senderId: String, isMonitored: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = store.banks.value.firstOrNull { it.senderId == senderId }
                ?: return@withContext
            store.setBank(existing.copy(isMonitored = isMonitored))
        }

    suspend fun addCustomSender(sender: BankSender) = withContext(Dispatchers.IO) {
        store.setBank(sender)
    }

    suspend fun updateSender(oldSenderId: String, updated: BankSender) =
        withContext(Dispatchers.IO) {
            if (!oldSenderId.equals(updated.senderId, ignoreCase = true)) {
                runCatching { store.deleteBank(oldSenderId) }
            }
            store.setBank(updated)
        }

    suspend fun deleteSender(sender: BankSender) = withContext(Dispatchers.IO) {
        runCatching { store.deleteBank(sender.senderId) }
    }

    // ── Legacy file info (local Masari folder stays on disk until cleanup) ──

    fun getStorageDirectoryPath(): String = ""
    fun isPublicStorageActive(): Boolean = false
    fun getPersistentDbPath(): String = "Cloud Firestore"

    /**
     * Full account reset: wipes every cloud collection for this user and
     * re-seeds templates + banks. Mirrors the old "clear files" behavior.
     */
    suspend fun clearAllPersistenceFiles() = withContext(Dispatchers.IO) {
        runCatching { store.deleteAllTransactions() }
        runCatching { store.deleteAllManuals() }
        runCatching { store.deleteAllSkipped() }
        runCatching { store.deleteAllTemplates() }
        runCatching { store.deleteAllBanks() }
        runCatching { store.deleteAllVerdicts() }
        _aiScanProgress.value = null
        ensureSeeded()
    }

    /**
     * Refreshes derived state. Snapshots update live; this re-seeds empty
     * collections and re-mirrors manuals (tab switches, etc.).
     */
    suspend fun refreshFromFiles() = withContext(Dispatchers.IO) {
        ensureSeeded()
        syncManualMirrors()
    }

    /** Category edits and deletes act on the visible (active-mode) database. */
    suspend fun updateTransactionCategory(transactionId: Long, newCategory: String) {
        val doc = store.transactions.value
            .firstOrNull { it.tx.id == transactionId && it.isVisible(_parseMode.value) }
            ?: return
        runCatching { store.setTransaction(doc.tx.copy(category = newCategory), doc.origin) }
    }

    suspend fun deleteTransaction(transactionId: Long) {
        val doc = store.transactions.value
            .firstOrNull { it.tx.id == transactionId && it.isVisible(_parseMode.value) }
            ?: return
        runCatching { store.deleteTransaction(doc.tx.docId) }
    }

    suspend fun deleteAllTransactions() {
        val origins = visibleOrigins(_parseMode.value)
        val ids = store.transactions.value
            .filter { it.origin in origins }
            .map { it.tx.docId }
        if (ids.isNotEmpty()) {
            runCatching { store.deleteTransactions(ids) }
        }
    }

    private fun yearMonthOf(timestamp: Long): String {
        return try {
            val dt = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
            "%04d-%02d".format(dt.year, dt.monthValue)
        } catch (_: Exception) {
            "1970-01"
        }
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

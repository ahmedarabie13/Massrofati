package com.banksms.expensetracker.data.cloud

import android.util.Log
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Which pipeline produced a transaction document. Views filter on this. */
object TxOrigin {
    const val REGEX = "regex"   // manual regex pipeline SMS rows
    const val AI = "ai"         // on-device model SMS rows
    const val MANUAL = "manual" // user-entered mirrors (visible in both modes)
}

object CloudDocs {
    /**
     * Pipeline-scoped doc IDs: the same inbox SMS parsed by both pipelines
     * yields DIFFERENT docs (mirrors the old two-table world). Sharing one
     * "sms_" namespace let the AI upload overwrite regex rows.
     */
    fun regexDocId(messageId: Long): String = "r_$messageId"
    fun aiDocId(messageId: Long): String = "a_$messageId"
    fun manualDocId(manualId: String): String = "manual_$manualId"

    /**
     * Deterministic numeric id from the doc id, so every device agrees on
     * [Transaction.id] without a central allocator. Never 0 (0 means "unset").
     */
    fun stableId(docId: String): Long {
        val h = docId.hashCode().toLong()
        return if (h == 0L) -1L else h
    }
}

/** A transaction document plus the pipeline that produced it (views filter on this). */
data class TxDoc(
    val tx: Transaction,
    val origin: String
)

data class ScanVerdict(
    val messageId: Long,
    val sender: String = "",
    val timestamp: Long = 0L,
    val body: String = "",
    val isTransaction: Boolean = false
)

/**
 * Firestore is the source of truth. One snapshot listener per collection
 * keeps a hot StateFlow current (server when online, local cache when
 * offline — the SDK handles both). All writes are plain sets/deletes;
 * bulk paths use WriteBatch (500 ops max, we chunk at 400).
 */
class FirestoreStore(
    uid: String,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "FirestoreStore"
        private const val BATCH_CHUNK = 400
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<ListenerRegistration>()

    private val root = db.collection("users").document(uid)
    private val txCol = root.collection("transactions")
    private val manualsCol = root.collection("manualExpenses")
    private val skippedCol = root.collection("skipped")
    private val templatesCol = root.collection("templates")
    private val banksCol = root.collection("banks")
    private val verdictsCol = root.collection("verdicts")
    private val settingsDoc = root.collection("meta").document("settings")

    // ── Snapshot-backed state ────────────────────────────────────────────

    private val loadedFlags = MutableStateFlow(0)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val transactions: StateFlow<List<TxDoc>> =
        listenList("transactions", txCol, ::txDocFromDoc, flag = 1)
    val manualExpenses: StateFlow<List<ManualExpense>> =
        listenList("manualExpenses", manualsCol, ::manualFromDoc, flag = 2)
    val skipped: StateFlow<List<SkippedTransaction>> =
        listenList("skipped", skippedCol, ::skippedFromDoc, flag = 4)
    val templates: StateFlow<List<MessageTemplate>> =
        listenList("templates", templatesCol, ::templateFromDoc, flag = 8)
    val banks: StateFlow<List<BankSender>> =
        listenList("banks", banksCol, ::bankFromDoc, flag = 16)
    val verdicts: StateFlow<Map<Long, ScanVerdict>> =
        listenMap("verdicts", verdictsCol, ::verdictFromDoc, flag = 32)

    private fun <T> listenList(
        name: String,
        col: com.google.firebase.firestore.CollectionReference,
        map: (DocumentSnapshot) -> T?,
        flag: Int
    ): StateFlow<List<T>> {
        val flow = MutableStateFlow<List<T>>(emptyList())
        listeners += col.addSnapshotListener { snap: QuerySnapshot?, err ->
            if (err != null) {
                Log.w(TAG, "$name listener error (serving last state)", err)
                markLoaded(flag)
                return@addSnapshotListener
            }
            if (snap != null) {
                flow.value = snap.documents.mapNotNull {
                    runCatching { map(it) }.getOrNull()
                }
            }
            markLoaded(flag)
        }
        return flow.asStateFlow()
    }

    private fun <T : Any> listenMap(
        name: String,
        col: com.google.firebase.firestore.CollectionReference,
        map: (DocumentSnapshot) -> Pair<Long, T>?,
        flag: Int
    ): StateFlow<Map<Long, T>> {
        val flow = MutableStateFlow<Map<Long, T>>(emptyMap())
        listeners += col.addSnapshotListener { snap: QuerySnapshot?, err ->
            if (err != null) {
                Log.w(TAG, "$name listener error (serving last state)", err)
                markLoaded(flag)
                return@addSnapshotListener
            }
            if (snap != null) {
                flow.value = snap.documents.mapNotNull {
                    runCatching { map(it) }.getOrNull()
                }.toMap()
            }
            markLoaded(flag)
        }
        return flow.asStateFlow()
    }

    private fun markLoaded(flag: Int) {
        val next = loadedFlags.value or flag
        loadedFlags.value = next
        if (next == 63) _ready.value = true
    }

    /** Waits for every collection's first snapshot (cache counts). False on timeout. */
    suspend fun awaitReady(timeoutMs: Long = 8000): Boolean =
        withTimeoutOrNull(timeoutMs) { ready.first { it } } ?: false

    /**
     * Write confirmation with a ceiling: Firestore write Tasks only complete
     * on server ack, so offline they would hang forever. The local snapshot
     * already updated optimistically — null just means "syncing later".
     */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitWrite(): T? {
        val done = withTimeoutOrNull(20_000) { await() }
        if (done == null) Log.d(TAG, "write pending (offline?) — will sync later")
        return done
    }

    // ── Transactions ─────────────────────────────────────────────────────

    /** Origin for a doc when the caller doesn't state the pipeline explicitly. */
    fun originFor(tx: Transaction): String =
        if (tx.docId.startsWith("manual_") || tx.isManual) TxOrigin.MANUAL else TxOrigin.REGEX

    suspend fun setTransaction(tx: Transaction, origin: String = originFor(tx)) =
        withContext(Dispatchers.IO) {
            require(tx.docId.isNotBlank()) { "Transaction needs a docId" }
            txCol.document(tx.docId).set(txToMap(tx, origin)).awaitWrite()
        }

    suspend fun setTransactions(txs: List<Transaction>, origin: String? = null) =
        withContext(Dispatchers.IO) {
            txs.chunked(BATCH_CHUNK).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { tx ->
                    require(tx.docId.isNotBlank()) { "Transaction needs a docId" }
                    batch.set(txCol.document(tx.docId), txToMap(tx, origin ?: originFor(tx)))
                }
            batch.commit().awaitWrite()
        }
    }

    suspend fun deleteTransaction(docId: String) = withContext(Dispatchers.IO) {
        txCol.document(docId).delete().awaitWrite()
    }

    suspend fun deleteTransactions(docIds: Collection<String>) = withContext(Dispatchers.IO) {
        docIds.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(txCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    /** Wipes one pipeline's SMS rows (used for "clear AI data"). */
    suspend fun deleteTransactionsByOrigin(origin: String) = withContext(Dispatchers.IO) {
        val docs = txCol.whereEqualTo("origin", origin).get().await()
        docs.documents.map { it.id }.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(txCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    suspend fun deleteAllTransactions() = withContext(Dispatchers.IO) {
        val docs = txCol.get().await()
        docs.documents.map { it.id }.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(txCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    // ── Manual expenses (source) ─────────────────────────────────────────

    suspend fun setManual(expense: ManualExpense) = withContext(Dispatchers.IO) {
        manualsCol.document(expense.id).set(manualToMap(expense)).awaitWrite()
    }

    suspend fun setManuals(expenses: List<ManualExpense>) = withContext(Dispatchers.IO) {
        expenses.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.set(manualsCol.document(it.id), manualToMap(it)) }
            batch.commit().awaitWrite()
        }
    }

    suspend fun deleteManual(manualId: String) = withContext(Dispatchers.IO) {
        manualsCol.document(manualId).delete().awaitWrite()
    }

    suspend fun deleteAllManuals() = withContext(Dispatchers.IO) {
        val docs = manualsCol.get().await()
        docs.documents.map { it.id }.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(manualsCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    // ── Skipped ──────────────────────────────────────────────────────────

    suspend fun addSkipped(skipped: SkippedTransaction) = withContext(Dispatchers.IO) {
        skippedCol.add(skippedToMap(skipped)).awaitWrite()
    }

    suspend fun deleteSkippedWhere(predicate: (SkippedTransaction) -> Boolean) =
        withContext(Dispatchers.IO) {
            val docs = skippedCol.get().await()
            val ids = docs.documents.mapNotNull { doc ->
                runCatching { skippedFromDoc(doc) }.getOrNull()
                    ?.takeIf(predicate)?.let { doc.id }
            }
            ids.chunked(BATCH_CHUNK).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(skippedCol.document(it)) }
                batch.commit().awaitWrite()
            }
        }

    /** Doc-ID-aware skip read (for targeted cleanup of duplicate records). */
    suspend fun fetchSkippedOnce(): List<Pair<String, SkippedTransaction>> =
        withContext(Dispatchers.IO) {
            skippedCol.get().await().documents.mapNotNull { doc ->
                runCatching { skippedFromDoc(doc) }.getOrNull()?.let { doc.id to it }
            }
        }

    suspend fun deleteSkippedDocs(ids: Collection<String>) = withContext(Dispatchers.IO) {
        ids.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(skippedCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    suspend fun deleteAllSkipped() = withContext(Dispatchers.IO) {
        val docs = skippedCol.get().await()
        docs.documents.map { it.id }.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(skippedCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    // ── Templates / banks ────────────────────────────────────────────────

    suspend fun setTemplate(template: MessageTemplate) = withContext(Dispatchers.IO) {
        templatesCol.document(template.id).set(templateToMap(template)).awaitWrite()
    }

    suspend fun setTemplates(all: List<MessageTemplate>) = withContext(Dispatchers.IO) {
        all.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.set(templatesCol.document(it.id), templateToMap(it)) }
            batch.commit().awaitWrite()
        }
    }

    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.IO) {
        templatesCol.document(id).delete().awaitWrite()
    }

    suspend fun deleteAllTemplates() = withContext(Dispatchers.IO) {
        val docs = templatesCol.get().await()
        docs.documents.map { it.id }.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(templatesCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    suspend fun setBank(bank: BankSender) = withContext(Dispatchers.IO) {
        banksCol.document(bank.senderId).set(bankToMap(bank)).awaitWrite()
    }

    suspend fun setBanks(all: List<BankSender>) = withContext(Dispatchers.IO) {
        all.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.set(banksCol.document(it.senderId), bankToMap(it)) }
            batch.commit().awaitWrite()
        }
    }

    suspend fun deleteBank(senderId: String) = withContext(Dispatchers.IO) {
        banksCol.document(senderId).delete().awaitWrite()
    }

    suspend fun deleteAllBanks() = withContext(Dispatchers.IO) {
        val docs = banksCol.get().await()
        docs.documents.map { it.id }.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(banksCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    // ── Verdicts ─────────────────────────────────────────────────────────

    suspend fun setVerdict(verdict: ScanVerdict) = withContext(Dispatchers.IO) {
        verdictsCol.document(verdict.messageId.toString())
            .set(verdictToMap(verdict)).awaitWrite()
    }

    suspend fun setVerdicts(all: List<ScanVerdict>) = withContext(Dispatchers.IO) {
        all.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach {
                batch.set(verdictsCol.document(it.messageId.toString()), verdictToMap(it))
            }
            batch.commit().awaitWrite()
        }
    }

    suspend fun deleteVerdict(messageId: Long) = withContext(Dispatchers.IO) {
        verdictsCol.document(messageId.toString()).delete().awaitWrite()
    }

    suspend fun deleteVerdictsBySenderAndBody(sender: String, body: String) =
        withContext(Dispatchers.IO) {
            val docs = verdictsCol.whereEqualTo("sender", sender).get().await()
            val ids = docs.documents.mapNotNull { doc ->
                runCatching { verdictFromDoc(doc) }.getOrNull()
                    ?.takeIf { it.second.body == body }?.let { doc.id }
            }
            ids.chunked(BATCH_CHUNK).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(verdictsCol.document(it)) }
                batch.commit().awaitWrite()
            }
        }

    suspend fun deleteAllVerdicts() = withContext(Dispatchers.IO) {
        val docs = verdictsCol.get().await()
        docs.documents.map { it.id }.chunked(BATCH_CHUNK).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(verdictsCol.document(it)) }
            batch.commit().awaitWrite()
        }
    }

    // ── Migration flag (server-side truth; survives reinstalls) ──────────

    /** Applied migration version (0 = never). Survives reinstalls. */
    suspend fun migrationVersion(): Int = withContext(Dispatchers.IO) {
        try {
            val snap = withTimeoutOrNull(25_000) { settingsDoc.get().await() }
            if (snap == null) {
                Log.w(TAG, "settings read timed out (backend unreachable?)")
                return@withContext 0
            }
            snap.getLong("migrationVersion")?.toInt()
                ?: if (snap.getBoolean("migrationCompleted") == true) 1 else 0
        } catch (e: Exception) {
            Log.w(TAG, "settings read failed", e)
            0
        }
    }

    suspend fun setMigrationVersion(version: Int) = withContext(Dispatchers.IO) {
        try {
            settingsDoc.set(
                mapOf("migrationVersion" to version, "migrationCompleted" to true)
            ).awaitWrite()
        } catch (e: Exception) {
            Log.w(TAG, "setMigrationVersion failed", e)
        }
    }

    /** One-shot point read of the transactions collection (migration dedup). */
    suspend fun fetchAllTransactionsOnce(): List<TxDoc> = withContext(Dispatchers.IO) {
        val snap = withTimeoutOrNull(40_000) { txCol.get().await() }
        if (snap == null) {
            Log.w(TAG, "transactions fetch timed out (backend unreachable?)")
            return@withContext emptyList()
        }
        Log.d(TAG, "transactions fetch: ${snap.size()} docs, fromCache=${snap.metadata.isFromCache}")
        snap.documents.mapNotNull {
            runCatching { txDocFromDoc(it) }.getOrNull()
        }
    }

    fun close() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
        scope.cancel()
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private fun txToMap(tx: Transaction, origin: String): Map<String, Any?> = mapOf(
        "messageId" to tx.messageId,
        "sender" to tx.sender,
        "type" to tx.type.name,
        "amount" to tx.amount,
        "currency" to tx.currency,
        "merchant" to tx.merchant,
        "accountOrCard" to tx.accountOrCard,
        "availableBalance" to tx.availableBalance,
        "category" to tx.category,
        "timestamp" to tx.timestamp,
        "rawBody" to tx.rawBody,
        "isManual" to tx.isManual,
        "manualId" to tx.manualId,
        "origin" to origin
    )

    private fun txFromDoc(doc: DocumentSnapshot): Transaction? {
        val docId = doc.id
        return Transaction(
            id = CloudDocs.stableId(docId),
            docId = docId,
            messageId = doc.getLong("messageId") ?: 0L,
            sender = doc.getString("sender") ?: "",
            type = doc.getString("type")?.let {
                runCatching { TransactionType.valueOf(it) }.getOrNull()
            } ?: TransactionType.UNKNOWN,
            amount = doc.getDouble("amount") ?: 0.0,
            currency = doc.getString("currency") ?: "SAR",
            merchant = doc.getString("merchant"),
            accountOrCard = doc.getString("accountOrCard"),
            availableBalance = doc.getDouble("availableBalance"),
            category = doc.getString("category") ?: "General",
            timestamp = doc.getLong("timestamp") ?: 0L,
            rawBody = doc.getString("rawBody") ?: "",
            isManual = doc.getBoolean("isManual") ?: false,
            manualId = doc.getString("manualId")
        )
    }

    private fun txDocFromDoc(doc: DocumentSnapshot): TxDoc? {
        val tx = txFromDoc(doc) ?: return null
        val origin = doc.getString("origin") ?: originFor(tx)
        return TxDoc(tx, origin)
    }

    private fun manualToMap(e: ManualExpense): Map<String, Any?> = mapOf(
        "amount" to e.amount,
        "currency" to e.currency,
        "type" to e.type.name,
        "category" to e.category,
        "merchant" to e.merchant,
        "paymentMethod" to e.paymentMethod,
        "timestamp" to e.timestamp,
        "note" to e.note,
        "createdAt" to e.createdAt,
        "updatedAt" to e.updatedAt
    )

    private fun manualFromDoc(doc: DocumentSnapshot): ManualExpense? = ManualExpense(
        id = doc.id,
        amount = doc.getDouble("amount") ?: 0.0,
        currency = doc.getString("currency") ?: "SAR",
        type = doc.getString("type")?.let {
            runCatching { TransactionType.valueOf(it) }.getOrNull()
        } ?: TransactionType.EXPENSE,
        category = doc.getString("category") ?: "General",
        merchant = doc.getString("merchant") ?: "",
        paymentMethod = doc.getString("paymentMethod") ?: "Cash",
        timestamp = doc.getLong("timestamp") ?: 0L,
        note = doc.getString("note") ?: "",
        createdAt = doc.getLong("createdAt") ?: 0L,
        updatedAt = doc.getLong("updatedAt") ?: 0L
    )

    private fun skippedToMap(s: SkippedTransaction): Map<String, Any?> = mapOf(
        "originalMessageId" to s.originalMessageId,
        "sender" to s.sender,
        "amount" to s.amount,
        "currency" to s.currency,
        "type" to s.type.name,
        "merchant" to s.merchant,
        "category" to s.category,
        "rawBody" to s.rawBody,
        "timestamp" to s.timestamp,
        "skippedAt" to s.skippedAt,
        "reason" to s.reason
    )

    private fun skippedFromDoc(doc: DocumentSnapshot): SkippedTransaction? = SkippedTransaction(
        originalMessageId = doc.getLong("originalMessageId") ?: 0L,
        sender = doc.getString("sender") ?: "",
        amount = doc.getDouble("amount") ?: 0.0,
        currency = doc.getString("currency") ?: "SAR",
        type = doc.getString("type")?.let {
            runCatching { TransactionType.valueOf(it) }.getOrNull()
        } ?: TransactionType.EXPENSE,
        merchant = doc.getString("merchant"),
        category = doc.getString("category") ?: "General",
        rawBody = doc.getString("rawBody") ?: "",
        timestamp = doc.getLong("timestamp") ?: 0L,
        skippedAt = doc.getLong("skippedAt") ?: 0L,
        reason = doc.getString("reason") ?: "User skipped"
    )

    private fun templateToMap(t: MessageTemplate): Map<String, Any?> = mapOf(
        "name" to t.name,
        "sender" to t.sender,
        "pattern" to t.pattern,
        "defaultType" to t.defaultType.name,
        "defaultCurrency" to t.defaultCurrency,
        "defaultCategory" to t.defaultCategory,
        "isEnabled" to t.isEnabled,
        "createdAt" to t.createdAt,
        "updatedAt" to t.updatedAt
    )

    private fun templateFromDoc(doc: DocumentSnapshot): MessageTemplate? = MessageTemplate(
        id = doc.id,
        name = doc.getString("name") ?: "",
        sender = doc.getString("sender") ?: "",
        pattern = doc.getString("pattern") ?: "",
        defaultType = doc.getString("defaultType")?.let {
            runCatching { TransactionType.valueOf(it) }.getOrNull()
        } ?: TransactionType.EXPENSE,
        defaultCurrency = doc.getString("defaultCurrency") ?: "SAR",
        defaultCategory = doc.getString("defaultCategory") ?: "General",
        isEnabled = doc.getBoolean("isEnabled") ?: true,
        createdAt = doc.getLong("createdAt") ?: 0L,
        updatedAt = doc.getLong("updatedAt") ?: 0L
    )

    private fun bankToMap(b: BankSender): Map<String, Any?> = mapOf(
        "displayName" to b.displayName,
        "isMonitored" to b.isMonitored,
        "customRegex" to b.customRegex
    )

    private fun bankFromDoc(doc: DocumentSnapshot): BankSender? = BankSender(
        senderId = doc.id,
        displayName = doc.getString("displayName") ?: doc.id,
        isMonitored = doc.getBoolean("isMonitored") ?: true,
        customRegex = doc.getString("customRegex")
    )

    private fun verdictToMap(v: ScanVerdict): Map<String, Any?> = mapOf(
        "sender" to v.sender,
        "timestamp" to v.timestamp,
        "body" to v.body,
        "isTransaction" to v.isTransaction
    )

    private fun verdictFromDoc(doc: DocumentSnapshot): Pair<Long, ScanVerdict>? {
        val messageId = doc.id.toLongOrNull() ?: return null
        return messageId to ScanVerdict(
            messageId = messageId,
            sender = doc.getString("sender") ?: "",
            timestamp = doc.getLong("timestamp") ?: 0L,
            body = doc.getString("body") ?: "",
            isTransaction = doc.getBoolean("isTransaction") ?: false
        )
    }
}

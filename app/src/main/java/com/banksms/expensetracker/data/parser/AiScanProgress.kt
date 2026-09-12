package com.banksms.expensetracker.data.parser

import com.banksms.expensetracker.data.model.Transaction
import java.util.concurrent.atomic.AtomicInteger

/** Per-chat (sender) scan state shown in the Parsing Mode card. */
data class SenderScanStatus(
    val sender: String,
    val total: Int,
    val excluded: Int,
    val scanned: Int,
    val imported: Int
) {
    /** Messages that still need (or are undergoing) inference. */
    val toScan: Int get() = (total - excluded).coerceAtLeast(0)
    val remaining: Int get() = (toScan - scanned).coerceAtLeast(0)
    val isDone: Boolean get() = scanned >= toScan
    val isExcluded: Boolean get() = toScan == 0
}

/** Immutable snapshot of an AI rescan, published after every batch. */
data class AiScanProgress(
    val senders: List<SenderScanStatus> = emptyList(),
    val batchesTotal: Int = 0,
    val batchesDone: Int = 0,
    /** Model calls currently generating (proves true overlap on screen). */
    val activeCalls: Int = 0,
    val excludedNoCurrency: Int = 0,
    val excludedDuplicate: Int = 0,
    val excludedSkipped: Int = 0,
    /** Judged in an earlier scan — never sent to the model again. */
    val excludedScanned: Int = 0,
    val finished: Boolean = false,
    val errors: List<String> = emptyList()
) {
    val totalMessages: Int get() = senders.sumOf { it.total }
    val excluded: Int get() = senders.sumOf { it.excluded }
    val scanned: Int get() = senders.sumOf { it.scanned }
    val imported: Int get() = senders.sumOf { it.imported }
    val remaining: Int get() = senders.sumOf { it.remaining }
}

/**
 * Thread-safe accumulator for [AiScanProgress]. The rescan runs up to 5
 * model calls concurrently, so every mutation is synchronized and the UI
 * only ever sees immutable snapshots.
 */
class AiScanTracker {

    private data class MutableSender(
        var total: Int = 0,
        var excluded: Int = 0,
        var scanned: Int = 0,
        var imported: Int = 0
    )

    private val senders = mutableMapOf<String, MutableSender>()
    private val active = AtomicInteger(0)
    private var batchesTotal: Int = 0
    private var batchesDone: Int = 0
    private var excludedNoCurrency: Int = 0
    private var excludedDuplicate: Int = 0
    private var excludedSkipped: Int = 0
    private var excludedScanned: Int = 0
    private val errors = mutableListOf<String>()

    @Synchronized
    fun setTotals(raw: List<RawSms>) {
        senders.clear()
        raw.groupingBy { it.sender }.eachCount().forEach { (sender, count) ->
            senders[sender] = MutableSender(total = count)
        }
    }

    @Synchronized
    fun setBatchesTotal(n: Int) {
        batchesTotal = n
    }

    @Synchronized
    fun markNoCurrency(sms: RawSms) {
        senders.getOrPut(sms.sender) { MutableSender() }.excluded++
        excludedNoCurrency++
    }

    @Synchronized
    fun markDuplicate(sms: RawSms) {
        senders.getOrPut(sms.sender) { MutableSender() }.excluded++
        excludedDuplicate++
    }

    @Synchronized
    fun markSkipped(sms: RawSms) {
        senders.getOrPut(sms.sender) { MutableSender() }.excluded++
        excludedSkipped++
    }

    @Synchronized
    fun markAlreadyScanned(sms: RawSms) {
        senders.getOrPut(sms.sender) { MutableSender() }.excluded++
        excludedScanned++
    }

    /** A batch started generating; returns the current in-flight count. */
    fun batchStarted(): Int = active.incrementAndGet()

    /** A batch stopped generating (done or failed); returns the in-flight count. */
    fun batchFinished(): Int = active.decrementAndGet()

    /** One finished batch: every row counts as scanned, imports counted per chat. */
    @Synchronized
    fun markBatchDone(batch: List<RawSms>, imported: List<Transaction>) {
        batch.groupingBy { it.sender }.eachCount().forEach { (sender, count) ->
            senders.getOrPut(sender) { MutableSender() }.scanned += count
        }
        imported.groupingBy { it.sender }.eachCount().forEach { (sender, count) ->
            senders.getOrPut(sender) { MutableSender() }.imported += count
        }
        batchesDone++
    }

    @Synchronized
    fun addError(message: String) {
        if (errors.size < 5) errors.add(message)
    }

    @Synchronized
    fun snapshot(finished: Boolean = false): AiScanProgress =
        AiScanProgress(
            senders = senders.entries
                .map { (sender, m) ->
                    SenderScanStatus(sender, m.total, m.excluded, m.scanned, m.imported)
                }
                .sortedWith(compareBy({ it.isDone }, { it.sender })),
            batchesTotal = batchesTotal,
            batchesDone = batchesDone,
            activeCalls = active.get(),
            excludedNoCurrency = excludedNoCurrency,
            excludedDuplicate = excludedDuplicate,
            excludedSkipped = excludedSkipped,
            excludedScanned = excludedScanned,
            finished = finished,
            errors = errors.toList()
        )
}

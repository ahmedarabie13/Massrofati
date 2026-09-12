package com.banksms.expensetracker

import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.data.parser.AiScanTracker
import com.banksms.expensetracker.data.parser.RawSms
import org.junit.Assert.*
import org.junit.Test

class AiScanTrackerTest {

    private fun raw(id: Long, sender: String) =
        RawSms(messageId = id, sender = sender, body = "SAR 10", timestamp = id)

    private fun tx(id: Long, sender: String) = Transaction(
        messageId = id,
        sender = sender,
        type = TransactionType.EXPENSE,
        amount = 10.0
    )

    @Test
    fun totalsAndExclusionsAddUp() {
        val tracker = AiScanTracker()
        val rows = listOf(raw(1, "Alinma"), raw(2, "Alinma"), raw(3, "SNB"), raw(4, "SNB"))
        tracker.setTotals(rows)
        tracker.markNoCurrency(rows[0])
        tracker.markDuplicate(rows[1])
        tracker.markSkipped(rows[2])
        tracker.markAlreadyScanned(rows[3])

        val snap = tracker.snapshot()
        assertEquals(4, snap.totalMessages)
        assertEquals(4, snap.excluded)
        assertEquals(1, snap.excludedNoCurrency)
        assertEquals(1, snap.excludedDuplicate)
        assertEquals(1, snap.excludedSkipped)
        assertEquals(1, snap.excludedScanned)
        assertEquals(0, snap.remaining)
        // Fully excluded chats read as done/excluded.
        assertTrue(snap.senders.all { it.isDone && it.isExcluded })
    }

    @Test
    fun batchDoneAdvancesPerChatCounts() {
        val tracker = AiScanTracker()
        val rows = listOf(raw(1, "Alinma"), raw(2, "Alinma"), raw(3, "SNB"))
        tracker.setTotals(rows)
        tracker.setBatchesTotal(2)

        tracker.markBatchDone(listOf(rows[0], rows[1]), listOf(tx(1, "Alinma")))
        var snap = tracker.snapshot()
        assertEquals(2, snap.scanned)
        assertEquals(1, snap.imported)
        assertEquals(1, snap.remaining)
        assertEquals(1, snap.batchesDone)

        val alinma = snap.senders.first { it.sender == "Alinma" }
        val snb = snap.senders.first { it.sender == "SNB" }
        assertTrue(alinma.isDone)
        assertEquals(1, alinma.imported)
        assertFalse(snb.isDone)
        assertEquals("0/1", "${snb.scanned}/${snb.toScan}")

        tracker.markBatchDone(listOf(rows[2]), emptyList())
        snap = tracker.snapshot(finished = true)
        assertTrue(snap.finished)
        assertTrue(snap.senders.all { it.isDone })
        assertEquals(0, snap.remaining)
    }

    @Test
    fun unfinishedChatsSortFirst() {
        val tracker = AiScanTracker()
        val rows = listOf(raw(1, "Zebra"), raw(2, "Alpha"))
        tracker.setTotals(rows)
        tracker.markBatchDone(listOf(rows[0]), emptyList())
        val snap = tracker.snapshot()
        // Alpha still has work left, so it sorts before done Zebra.
        assertEquals(listOf("Alpha", "Zebra"), snap.senders.map { it.sender })
    }

    @Test
    fun errorsCappedAtFive() {
        val tracker = AiScanTracker()
        repeat(8) { tracker.addError("Batch ${it + 1}: boom") }
        assertEquals(5, tracker.snapshot().errors.size)
    }

    @Test
    fun activeCallsTracked() {
        val tracker = AiScanTracker()
        assertEquals(0, tracker.snapshot().activeCalls)
        assertEquals(1, tracker.batchStarted())
        assertEquals(2, tracker.batchStarted())
        assertEquals(2, tracker.snapshot().activeCalls)
        assertEquals(1, tracker.batchFinished())
        assertEquals(1, tracker.snapshot().activeCalls)
    }
}

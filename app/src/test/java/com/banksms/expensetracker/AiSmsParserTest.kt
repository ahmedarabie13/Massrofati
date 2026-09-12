package com.banksms.expensetracker

import com.banksms.expensetracker.data.llm.FakeChatEngine
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.data.parser.AiSmsParser
import com.banksms.expensetracker.data.parser.RawSms
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiSmsParserTest {

    private fun raw(id: Long, body: String = "body $id") =
        RawSms(messageId = id, sender = "Alinma", body = body, timestamp = 1_700_000_000_000L + id)

    @Test
    fun chunksTwentyByTwenty() {
        assertEquals(20, AiSmsParser.BATCH_SIZE)
        val all = (1L..45L).map { raw(it) }
        val chunks = AiSmsParser.chunkMessages(all)
        assertEquals(3, chunks.size)
        assertEquals(listOf(20, 20, 5), chunks.map { it.size })
        // Order preserved end to end.
        assertEquals((1L..45L).toList(), chunks.flatten().map { it.messageId })
    }

    @Test
    fun moneyHintMatchesCurrencies() {
        assertTrue(AiSmsParser.hasMoneyHint("Purchase Amount:SAR 9.50 To:Someone"))
        assertTrue(AiSmsParser.hasMoneyHint("شراء بـSR 412.1 عبر7871"))
        assertTrue(AiSmsParser.hasMoneyHint("Amount: 100 USD at store"))
        assertTrue(AiSmsParser.hasMoneyHint("تم تحويل 500 جنيه إلى حسابك"))
        assertTrue(AiSmsParser.hasMoneyHint("رصيدك الحالي 5000 ريال"))
        assertTrue(AiSmsParser.hasMoneyHint("You paid 20 دولار"))
        assertTrue(AiSmsParser.hasMoneyHint("حوالة واردة بـEGP 21737"))
    }

    @Test
    fun moneyHintRejectsNonMoneyTexts() {
        assertFalse(AiSmsParser.hasMoneyHint("Your verification code is 123456"))
        assertFalse(AiSmsParser.hasMoneyHint("Special offer: 50% off this weekend!"))
        assertFalse(AiSmsParser.hasMoneyHint("Outgoing transfer completed"))
        // Word boundaries: no false hit on "sr" inside other words.
        assertFalse(AiSmsParser.hasMoneyHint("OSRAM lighting festival"))
        assertFalse(AiSmsParser.hasMoneyHint(""))
    }

    @Test
    fun batchPromptCarriesIdsSendersAndBodies() {
        val batch = listOf(raw(11, "purchase 50 SAR"), raw(22, "OTP 1234"))
        val prompt = AiSmsParser.buildBatchPrompt(batch)
        assertTrue(prompt.contains("[id=11 sender=Alinma] purchase 50 SAR"))
        assertTrue(prompt.contains("[id=22 sender=Alinma] OTP 1234"))
    }

    @Test
    fun responseMapsTransactionAndSkipsNonTransaction() {
        val batch = listOf(raw(1), raw(2))
        val json = """
            [{"id":1,"isTransaction":true,"type":"EXPENSE","amount":74.4,
              "currency":"SAR","merchant":"Noon","accountOrCard":"**7871",
              "availableBalance":48564.91,"category":"Shopping & Groceries"},
             {"id":2,"isTransaction":false}]
        """.trimIndent()
        val out = AiSmsParser.parseResponse(json, batch)
        assertEquals(1, out.size)
        val tx = out[0]
        assertEquals(1L, tx.messageId)
        assertEquals("Alinma", tx.sender)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(74.4, tx.amount, 0.0)
        assertEquals("SAR", tx.currency)
        assertEquals("Noon", tx.merchant)
        assertEquals("**7871", tx.accountOrCard)
        assertEquals(48564.91, tx.availableBalance!!, 0.0)
        assertEquals("Shopping & Groceries", tx.category)
        assertEquals("body 1", tx.rawBody)
    }

    @Test
    fun responseToleratesFencesAndProse() {
        val batch = listOf(raw(7))
        val json = "Here you go:\n```json\n[{\"id\":7,\"isTransaction\":true," +
            "\"type\":\"INCOME\",\"amount\":100.0}]\n```\nDone."
        val out = AiSmsParser.parseResponse(json, batch)
        assertEquals(1, out.size)
        assertEquals(TransactionType.INCOME, out[0].type)
        // Defaults kick in for missing optional fields.
        assertEquals("SAR", out[0].currency)
        assertEquals("General", out[0].category)
        assertNull(out[0].merchant)
    }

    @Test
    fun responseDropsBadRows() {
        val batch = listOf(raw(1), raw(2), raw(3), raw(4))
        val json = """
            [{"id":1,"isTransaction":true,"type":"EXPENSE","amount":0},
             {"id":2,"isTransaction":true,"type":"TRANSFER","amount":50.0},
             {"id":3,"isTransaction":true,"type":"BOGUS","amount":50.0},
             {"id":999,"isTransaction":true,"type":"EXPENSE","amount":50.0}]
        """.trimIndent()
        assertTrue(AiSmsParser.parseResponse(json, batch).isEmpty())
    }

    @Test
    fun responseEmptyOnGarbage() {
        assertTrue(AiSmsParser.parseResponse("not json at all", listOf(raw(1))).isEmpty())
        assertTrue(AiSmsParser.parseResponse("", listOf(raw(1))).isEmpty())
    }

    @Test
    fun parseBatchRefusesDemoEngine() {
        try {
            runBlocking { AiSmsParser.parseBatch(FakeChatEngine(), listOf(raw(1))) }
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not downloaded"))
        }
    }

    @Test
    fun parseBatchEmptyInEmptyOut() {
        val out = runBlocking { AiSmsParser.parseBatch(FakeChatEngine(), emptyList()) }
        assertTrue(out.isEmpty())
    }
}

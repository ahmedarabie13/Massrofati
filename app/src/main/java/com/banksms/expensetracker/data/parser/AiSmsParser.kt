package com.banksms.expensetracker.data.parser

import com.banksms.expensetracker.data.llm.ChatEngine
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import org.json.JSONArray

/** One unparsed inbox row handed to the LLM extractor. */
data class RawSms(
    val messageId: Long,
    val sender: String,
    val body: String,
    val timestamp: Long
)

/**
 * LLM-powered SMS parsing. Messages go to the on-device model in batches of
 * [BATCH_SIZE]; the model returns one JSON DTO per message, which becomes a
 * [Transaction] stored in the separate AI database (never the manual one).
 */
object AiSmsParser {

    /** Messages per model call. 20 prompts + JSON answers still fit the 4k window. */
    const val BATCH_SIZE = 20

    /**
     * Concurrent batches during a rescan — each batch gets its own private
     * engine (single owner, safe to abandon on stop). 3 balances speed
     * against per-engine runtime/KV RAM on a phone; more slots help only
     * while the CPU scheduler and memory bandwidth have headroom.
     */
    const val PARALLEL_ENGINES = 3

    /**
     * Cheap prefilter: only texts mentioning a currency can be transactions.
     * Runs BEFORE any inference so OTPs/ads never cost model time. Latin
     * codes use word boundaries (so "transfer"/"OSRAM" don't match "SR").
     */
    private val MoneyHint = Regex("\\b(SAR|USD|SR|EGP)\\b", RegexOption.IGNORE_CASE)

    fun hasMoneyHint(body: String): Boolean =
        MoneyHint.containsMatchIn(body) ||
            body.contains("ريال") || body.contains("جنيه") || body.contains("دولار")

    const val SYSTEM_INSTRUCTION =
        "You are an SMS bank-transaction extractor. The user message lists SMS " +
            "texts as [id=.. sender=..] followed by the body. For each one decide " +
            "whether it reports a money movement (purchase, payment, transfer, " +
            "deposit, refund, withdrawal). Ignore OTPs, ads, and balance-only " +
            "alerts with no movement. Reply with ONLY a JSON array — no markdown, " +
            "no prose, no code fences. One element per input id, in the same order, " +
            "shaped exactly like: " +
            "{\"id\":123,\"isTransaction\":true,\"type\":\"EXPENSE\",\"amount\":74.4," +
            "\"currency\":\"SAR\",\"merchant\":\"Noon\",\"accountOrCard\":\"**7871\"," +
            "\"availableBalance\":48564.91,\"category\":\"Shopping & Groceries\"}. " +
            "Rules: type is EXPENSE for money out, INCOME for money in " +
            "(TRANSFER is not allowed, pick a side). amount is a plain number, " +
            "null when unknown. currency defaults to SAR. merchant is the " +
            "shop or person name, null when absent. accountOrCard keeps only a " +
            "masked ** + last digits form, null when absent. availableBalance is " +
            "a plain number or null. category is exactly one of: Food & Dining, " +
            "Transportation, Shopping & Groceries, Entertainment & Subscriptions, " +
            "Bills & Utilities, ATM / Cash, Health & Medical, Transfers, " +
            "Travel & Flights, Income / Deposits, General. " +
            "For non-transactions emit {\"id\":<same id>,\"isTransaction\":false} " +
            "with no other fields."

    fun chunkMessages(all: List<RawSms>): List<List<RawSms>> = all.chunked(BATCH_SIZE)

    /** User-side text for one model call. The spec lives in the system prompt. */
    fun buildBatchPrompt(batch: List<RawSms>): String = buildString {
        append("Extract transactions from these ${batch.size} SMS:\n")
        batch.forEach { sms ->
            append("[id=${sms.messageId} sender=${sms.sender}] ${sms.body}\n")
        }
    }

    /**
     * Pure JSON -> DTO mapping, unit-tested. Tolerates prose/fences around the
     * array; drops rows with unknown ids, non-transactions, bad types and
     * non-positive amounts.
     */
    fun parseResponse(raw: String, batch: List<RawSms>): List<Transaction> {
        val byId = batch.associateBy { it.messageId }
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end <= start) return emptyList()
        val array = try {
            JSONArray(raw.substring(start, end + 1))
        } catch (_: Exception) {
            return emptyList()
        }
        val out = mutableListOf<Transaction>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val sms = byId[obj.optLong("id", Long.MIN_VALUE)] ?: continue
            if (!obj.optBoolean("isTransaction", false)) continue
            val type = try {
                TransactionType.valueOf(obj.optString("type", "").uppercase())
            } catch (_: Exception) {
                null
            }
            if (type != TransactionType.EXPENSE && type != TransactionType.INCOME) continue
            val amount = obj.optDouble("amount", Double.NaN)
            if (amount.isNaN() || amount <= 0.0) continue
            out.add(
                Transaction(
                    messageId = sms.messageId,
                    sender = sms.sender,
                    type = type,
                    amount = amount,
                    currency = obj.optString("currency", "SAR").ifBlank { "SAR" },
                    merchant = obj.optNullableString("merchant"),
                    accountOrCard = obj.optNullableString("accountOrCard"),
                    availableBalance = if (obj.isNull("availableBalance")) null
                    else obj.optDouble("availableBalance", Double.NaN).takeIf { !it.isNaN() },
                    category = obj.optString("category", "General").ifBlank { "General" },
                    timestamp = sms.timestamp,
                    rawBody = sms.body
                )
            )
        }
        return out
    }

    private fun org.json.JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key, "").takeIf { it.isNotBlank() && it != "null" }
    }

    /** One model round-trip for a batch (any size 1..BATCH_SIZE). */
    suspend fun parseBatch(engine: ChatEngine, batch: List<RawSms>): List<Transaction> {
        if (batch.isEmpty()) return emptyList()
        check(!engine.isDemo) { "AI model not downloaded" }
        engine.ensureLoaded()
        val reply = engine.generateText(buildBatchPrompt(batch))
        return parseResponse(reply, batch)
    }

    /** Single-message path used by the live SMS receiver. */
    suspend fun parseSingle(engine: ChatEngine, sms: RawSms): Transaction? =
        parseBatch(engine, listOf(sms)).firstOrNull()
}

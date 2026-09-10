package com.banksms.expensetracker.data.parser

import com.banksms.expensetracker.data.model.TransactionType
import java.util.regex.Pattern

/**
 * Bank-specific SMS parser tuned for:
 *  1. Alinma (بنك الإنماء)
 *  2. AlinmaPay
 *  3. AlRajhiBank (مصرف الراجحي)
 *
 * Each bank has its own structured multi-line SMS format.
 * The parser auto-detects which bank format a message belongs to
 * based on content patterns, and falls back to a generic parser otherwise.
 */
object BankSmsParser {

    // ── public entry point ──────────────────────────────────────────────

    /**
     * Parse a bank SMS body into a [ParsedTransaction].
     * @param smsBody  the raw SMS text
     * @param sender   the SMS sender address (e.g. "Alinma", "AlRajhiBank", "AlinmaPay", "SNB", etc.)
     * @param customTemplates  list of user-configured message templates from file system
     * @return a parsed transaction or `null` if the message is not a financial transaction
     */
    fun parse(
        smsBody: String,
        sender: String = "",
        customTemplates: List<com.banksms.expensetracker.data.model.MessageTemplate> = emptyList()
    ): ParsedTransaction? {
        if (smsBody.isBlank()) return null
        val body = normalizeDigits(smsBody.trim())

        // 1. Check custom templates first
        for (template in customTemplates) {
            if (template.isEnabled) {
                val matched = TemplateMatcher.match(smsBody, sender, template)
                if (matched != null) {
                    return matched
                }
            }
        }

        val senderLower = sender.lowercase()

        // Exclude credit card bill payments / settlements across all banks
        if (isCreditCardPayment(body)) return null

        // 2. Route to bank-specific built-in parser based on sender name or content detection
        return when {
            // AlinmaPay must be checked BEFORE Alinma (substring match)
            senderLower.contains("alinmapay") || isAlinmaPayMessage(body) ->
                parseAlinmaPay(body)

            senderLower.contains("alinma") || isAlinmaMessage(body) ->
                parseAlinma(body)

            senderLower.contains("alrajhi") || senderLower.contains("rajhi") || isAlRajhiMessage(body) ->
                parseAlRajhi(body)

            else -> null // Only parse the 3 configured default banks
        }
    }

    // ── content-based bank detection ────────────────────────────────────

    private fun isAlinmaPayMessage(body: String): Boolean {
        val lower = body.lowercase()
        return (lower.contains("debit via") || lower.contains("credit via") ||
                lower.contains("outgoing funds transfer") || lower.contains("purchase card") ||
                lower.contains("debit  international transfer") || lower.contains("via alinma") ||
                lower.contains("alinmapay") || lower.contains("remaining balance"))
    }

    private fun isRefundText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("استرجاع") || lower.contains("استرداد") ||
                lower.contains("عكس") || lower.contains("مرتجع") ||
                lower.contains("مسترجع") || lower.contains("إرجاع") ||
                lower.contains("رد مبلغ") || lower.contains("رد عملية") ||
                lower.contains("refund") || lower.contains("reversal") ||
                lower.contains("reversed") || lower.contains("returned")
    }

    private fun isAlinmaMessage(body: String): Boolean {
        return (body.contains("بطاقة ائتمانية") || body.contains("البطاقة الائتمانية")) &&
                (body.contains("رصيد") || body.contains("الرصيد") ||
                 body.contains("رقم حساب") || body.contains("شراء") || isRefundText(body))
    }

    private fun isAlRajhiMessage(body: String): Boolean {
        // AlRajhi uses patterns like "بـSR", "عبر\d+;فيزا", "بطاقة:\d+", "حوالة"
        return (body.contains(";فيزا") || body.contains(";ماستر") || body.contains(";مدى") ||
                body.contains("بطاقة:") || body.contains("حوالة"))
    }

    /**
     * Detects credit card bill payment / settlement messages that should be excluded.
     * These are payments TO the credit card (paying off the balance), not actual purchases.
     * Examples:
     *   - "بطاقة فيزا:سداد بـSR 1431.09"  (AlRajhi)
     *   - "سداد بطاقة ائتمانية"            (Alinma)
     *   - "Card Payment" / "Bill Payment"   (AlinmaPay)
     */
    private fun isCreditCardPayment(body: String): Boolean {
        val lower = body.lowercase()
        // Arabic: سداد = settlement/payment, تسديد = paying off
        if (body.contains("سداد") || body.contains("تسديد")) return true
        // English: card payment, bill payment, payment to card
        if (lower.contains("card payment") || lower.contains("bill payment") ||
            lower.contains("credit card payment") || lower.contains("payment to card")) return true
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  1. ALINMA  (بنك الإنماء)
    // ══════════════════════════════════════════════════════════════════════
    /*
     * Known formats:
     *   a) شراء عبر: POS / البطاقة الائتمانية: **7639 / مبلغ: SAR 4 / لدى: merchant / الرصيد: X ريال
     *   b) شراء نقاط بيع 26.05 SAR - Samsung PAY / بطاقة ائتمانية **7639 / من merchant / رصيد X SAR
     *   c) شراء عبر الإنترنت / بطاقة ائتمانية **7639 / مبلغ SAR 1,064 / من merchant / رصيد X SAR
     *   d) شراء دولي إنترنت SAR 156 / بطاقة ائتمانية **7639 / من merchant / المبلغ المستحق SAR 159.59 / رصيد SAR X
     *   e) استرجاع عملية شراء / لبطاقة ائتمانية: *7639 / مبلغ: 38.76 SAR / رقم حساب: **0000 / في: merchant / في: country / في: timestamp
     */
    private fun parseAlinma(body: String): ParsedTransaction? {
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val firstLine = lines[0]

        // Check if message is a refund or purchase
        val isRefund = isRefundText(firstLine) || isRefundText(body)

        // Must be a purchase (شراء) or a refund (استرجاع / استرداد / عكس / مرتجع)
        if (!firstLine.contains("شراء") && !isRefund) return null

        val type = if (isRefund) TransactionType.INCOME else TransactionType.EXPENSE
        var amount: Double? = null
        var card: String? = null
        var merchant: String? = null
        var balance: Double? = null

        // ── Try inline amount on first line ──
        // "شراء نقاط بيع 26.05 SAR" or "شراء دولي إنترنت SAR 156"
        amount = extractSarAmount(firstLine)

        for (line in lines) {
            // Card: "البطاقة الائتمانية: **7639" or "بطاقة ائتمانية **7639" or "لبطاقة ائتمانية: *7639"
            if (card == null && (line.contains("بطاقة") || line.contains("البطاقة"))) {
                card = extractStarredId(line)
            }

            // Fallback account: "رقم حساب: **0000" or "حساب **0000"
            if (card == null && (line.contains("حساب") || line.contains("الحساب"))) {
                card = extractStarredId(line)
            }

            // Amount: "مبلغ: SAR 4" or "مبلغ SAR 1,064" or "مبلغ: 38.76 SAR" (only if not already found)
            if (amount == null && line.contains("مبلغ")) {
                amount = extractSarAmount(line)
            }

            // Total due (international, overrides base amount): "المبلغ المستحق SAR 159.59"
            if (line.contains("المبلغ المستحق")) {
                val totalDue = extractSarAmount(line)
                if (totalDue != null) amount = totalDue
            }

            // Merchant: "لدى: 170672 riyadh metro" or "من Riyadh Air" or "في: TEMU.COM"
            if (merchant == null) {
                if (line.startsWith("لدى")) {
                    merchant = line.removePrefix("لدى").removePrefix(":").trim()
                } else if (line.startsWith("من ") || line.startsWith("من\t")) {
                    merchant = line.removePrefix("من").trim()
                } else if (line.startsWith("في:") || line.startsWith("في ")) {
                    val candidate = line.removePrefix("في:").removePrefix("في").trim()
                    val isDateTime = Regex("""\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}:\d{2}""").containsMatchIn(candidate)
                    if (!isDateTime && candidate.isNotBlank()) {
                        merchant = candidate
                    }
                }
            }

            // Balance: "الرصيد: 26,236.61 ريال" or "رصيد 26,240.61 SAR" or "رصيد SAR 25979.55"
            if (balance == null && (line.contains("رصيد") || line.contains("الرصيد"))) {
                // Don't match "المبلغ المستحق" line accidentally
                if (!line.contains("المبلغ المستحق")) {
                    balance = extractSarAmount(line) ?: extractAmountBeforeRiyal(line)
                }
            }
        }

        if (amount == null || amount <= 0.0) return null

        return ParsedTransaction(
            type = type,
            amount = amount,
            currency = "SAR",
            merchant = merchant?.takeIf { it.isNotBlank() },
            accountOrCard = card,
            availableBalance = balance,
            category = inferCategory(merchant, body, type)
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. ALRAJHI  (مصرف الراجحي)
    // ══════════════════════════════════════════════════════════════════════
    /*
     * Known formats:
     *   a) شراء إنترنت بـSR 412.1 / عبر7871;فيزا / لـnoon.com / رصيد:48152.81 SR
     *   b) شراء عبر نقاط البيع / بطاقة:7871 ;فيزا / لدى:Noon / مبلغ:74.4 SAR / رصيد:48564.91 SAR
     *   c) حوالة داخلية صادرة بـSR 63 / من4941 / لـ6657;ذياب الصيري
     *   d) حوالة محلية واردة بـSR 21737 / لـ4941 / من5702;شركة دار البلد
     *   e) شراء إنترنت بـSR 92 / عبر7871;فيزا-سامسونج باي / لـananinja.
     *   f) شراء عبر نقاط البيع / بطاقة:7871 ;فيزا-سامسونج باي / لدى:DAWAHI AL / مبلغ:7 SAR
     */
    private fun parseAlRajhi(body: String): ParsedTransaction? {
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val firstLine = lines[0]
        var type = TransactionType.EXPENSE
        var amount: Double? = null
        var card: String? = null
        var merchant: String? = null
        var balance: Double? = null
        var fromAccount: String? = null

        // ── Determine transaction type from first line ──
        when {
            isRefundText(firstLine) || isRefundText(body) -> type = TransactionType.INCOME
            firstLine.contains("واردة") -> type = TransactionType.INCOME     // حوالة واردة = incoming
            firstLine.contains("صادرة") -> type = TransactionType.EXPENSE    // حوالة صادرة = outgoing
            firstLine.contains("شراء") -> type = TransactionType.EXPENSE     // purchase
            else -> {} // default EXPENSE
        }

        // ── Try inline amount from first line: "بـSR 412.1" or "بـSAR 74.4" ──
        val inlinePattern = Regex("""بـ\s*(?:SR|SAR)\s*([0-9,]+(?:\.[0-9]+)?)""")
        inlinePattern.find(firstLine)?.let {
            amount = parseAmountStr(it.groupValues[1])
        }

        for (line in lines) {
            // Card via: "عبر7871;فيزا" or "عبر7871;فيزا-سامسونج باي"
            if (card == null && line.startsWith("عبر")) {
                val m = Regex("""عبر\s*(\d{4})""").find(line)
                if (m != null) card = "**${m.groupValues[1]}"
            }

            // Card: "بطاقة:7871 ;فيزا" or "بطاقة:7871 ;فيزا-سامسونج باي"
            if (card == null && line.startsWith("بطاقة")) {
                val m = Regex("""بطاقة\s*:\s*(\d{4})""").find(line)
                if (m != null) card = "**${m.groupValues[1]}"
            }

            // Amount: "مبلغ:74.4 SAR" or "مبلغ:7 SAR"
            if (amount == null && line.contains("مبلغ")) {
                val m = Regex("""مبلغ\s*:\s*([0-9,]+(?:\.[0-9]+)?)\s*(?:SAR|SR)""").find(line)
                if (m != null) amount = parseAmountStr(m.groupValues[1])
            }

            // Merchant: "لـnoon.com" or "لدى:Noon" or "لدى:DAWAHI AL"
            if (merchant == null) {
                if (line.startsWith("لدى")) {
                    merchant = line.removePrefix("لدى").removePrefix(":").trim()
                } else if (line.startsWith("لـ") && !line.matches(Regex("""^لـ\d+.*"""))) {
                    // "لـnoon.com" but NOT "لـ4941" (which is an account number)
                    merchant = line.removePrefix("لـ").trim()
                }
            }

            // For transfers: extract recipient from "لـ6657;ذياب الصيري"
            if (merchant == null && line.startsWith("لـ")) {
                val transferTo = Regex("""^لـ\d+\s*;\s*(.+)$""").find(line)
                if (transferTo != null) {
                    merchant = transferTo.groupValues[1].trim()
                }
            }

            // For incoming transfers: extract sender from "من5702;شركة دار البلد"
            if (merchant == null && line.startsWith("من") && line.contains(";")) {
                val transferFrom = Regex("""^من\d+\s*;\s*(.+)$""").find(line)
                if (transferFrom != null) {
                    merchant = transferFrom.groupValues[1].trim()
                }
            }

            // Account: "من4941" (for transfers)
            if (fromAccount == null && line.startsWith("من") && !line.contains(";")) {
                val m = Regex("""^من\s*(\d{4})$""").find(line)
                if (m != null) fromAccount = "**${m.groupValues[1]}"
            }

            // Balance: "رصيد:48152.81 SR" or "رصيد:48564.91 SAR"
            if (balance == null && line.startsWith("رصيد")) {
                val m = Regex("""رصيد\s*:\s*([0-9,]+(?:\.[0-9]+)?)\s*(?:SAR|SR)""").find(line)
                if (m != null) balance = parseAmountStr(m.groupValues[1])
            }
        }

        if (amount == null || amount!! <= 0.0) return null

        val category = when {
            type == TransactionType.INCOME -> "Income / Deposits"
            firstLine.contains("حوالة") -> "Transfers"
            else -> inferCategory(merchant, body, type)
        }

        return ParsedTransaction(
            type = type,
            amount = amount!!,
            currency = "SAR",
            merchant = merchant?.takeIf { it.isNotBlank() },
            accountOrCard = card ?: fromAccount,
            availableBalance = balance,
            category = category
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. ALINMA PAY
    // ══════════════════════════════════════════════════════════════════════
    /*
     * Known formats (English):
     *   a) Debit via local transfer / Amount:SAR 9.50 / To:NAME / Account:**9695
     *   b) Credit via local transfer / Amount:SAR 1.00 / From:NAME / Account:**1673
     *   c) Outgoing Funds Transfer Approved / Debited from wallet: **4303 / Amount: 1,000.60 SAR
     *   d) Purchase Card / Card: **9860 MADA / Amount: 129.11 SAR / At: merchant / Remaining Balance: X SAR
     *   e) Debit  International Transfer / Amount: 14,705 SAR / To: NAME / Sender Account: **4303
     */
    private fun parseAlinmaPay(body: String): ParsedTransaction? {
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val firstLine = lines[0]
        val firstLineLower = firstLine.lowercase()

        // ── Determine transaction type ──
        val type = when {
            firstLineLower.startsWith("credit") -> TransactionType.INCOME
            firstLineLower.startsWith("debit") -> TransactionType.EXPENSE
            firstLineLower.startsWith("outgoing") -> TransactionType.EXPENSE
            firstLineLower.startsWith("purchase") -> TransactionType.EXPENSE
            else -> TransactionType.UNKNOWN
        }
        if (type == TransactionType.UNKNOWN) return null

        var amount: Double? = null
        var card: String? = null
        var merchant: String? = null
        var balance: Double? = null
        var account: String? = null

        for (line in lines) {
            val lineLower = line.lowercase()

            // Amount: "Amount:SAR 9.50" or "Amount: 1,000.60 SAR" or "Amount: 14,705 SAR"
            if (amount == null && lineLower.startsWith("amount")) {
                amount = extractSarAmount(line)
            }

            // Card: "Card: **9860 MADA"
            if (card == null && lineLower.startsWith("card:")) {
                card = extractStarredId(line)
            }

            // Account: "Account:**9695" or "Sender Account: **4303"
            if (account == null && lineLower.contains("account")) {
                account = extractStarredId(line)
            }

            // Debited from wallet: "Debited from wallet: **4303"
            if (account == null && lineLower.contains("debited from wallet")) {
                account = extractStarredId(line)
            }

            // Merchant / To: "To:AMER ABDELNABY" or "To: Ahmed Arabie" or "At: shawerma house"
            if (merchant == null) {
                when {
                    lineLower.startsWith("to:") || lineLower.startsWith("to :") ->
                        merchant = line.substringAfter(":").trim()
                    lineLower.startsWith("at:") || lineLower.startsWith("at :") ->
                        merchant = line.substringAfter(":").trim()
                }
            }

            // From (for credit/income): "From:FAWAZ MASHI"
            if (merchant == null && type == TransactionType.INCOME && lineLower.startsWith("from:")) {
                merchant = line.substringAfter(":").trim()
            }

            // Balance: "Remaining Balance: 2,786.59 SAR"
            if (balance == null && lineLower.contains("remaining balance")) {
                balance = extractSarAmount(line)
            }
        }

        if (amount == null || amount!! <= 0.0) return null

        val category = when {
            type == TransactionType.INCOME -> "Income / Deposits"
            firstLineLower.contains("transfer") -> "Transfers"
            firstLineLower.contains("purchase") -> inferCategory(merchant, body, type)
            else -> "Transfers"
        }

        return ParsedTransaction(
            type = type,
            amount = amount!!,
            currency = "SAR",
            merchant = merchant?.takeIf { it.isNotBlank() },
            accountOrCard = card ?: account,
            availableBalance = balance,
            category = category
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /** Extract an amount next to SAR/SR in either order: "SAR 1,064" or "26.05 SAR" */
    private fun extractSarAmount(text: String): Double? {
        // "SAR 1,064" or "SR 412.1" (currency before amount)
        val pre = Regex("""(?:SAR|SR|ريال)\s+([0-9,]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(text)
        if (pre != null) return parseAmountStr(pre.groupValues[1])

        // "26.05 SAR" or "48152.81 SR" (amount before currency)
        val post = Regex("""([0-9,]+(?:\.[0-9]+)?)\s+(?:SAR|SR|ريال)""", RegexOption.IGNORE_CASE).find(text)
        if (post != null) return parseAmountStr(post.groupValues[1])

        // "مبلغ: SAR 4" or ":SAR 9.50" (after colon, tightly bound)
        val colon = Regex(""":\s*(?:SAR|SR)\s+([0-9,]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(text)
        if (colon != null) return parseAmountStr(colon.groupValues[1])

        // ":74.4 SAR" (amount after colon, currency after)
        val colonPost = Regex(""":\s*([0-9,]+(?:\.[0-9]+)?)\s+(?:SAR|SR)""", RegexOption.IGNORE_CASE).find(text)
        if (colonPost != null) return parseAmountStr(colonPost.groupValues[1])

        return null
    }

    /** Extract amount before "ريال": "26,236.61 ريال" */
    private fun extractAmountBeforeRiyal(text: String): Double? {
        val m = Regex("""([0-9,]+(?:\.[0-9]+)?)\s*ريال""").find(text)
        return m?.let { parseAmountStr(it.groupValues[1]) }
    }

    /** Extract a *XXXX or **XXXX pattern from text and format as **XXXX */
    private fun extractStarredId(text: String): String? {
        val m = Regex("""\*+\s*(\d+)""").find(text)
        return m?.let {
            val digits = it.groupValues[1]
            if (digits.length >= 4) "**${digits.takeLast(4)}" else "**$digits"
        }
    }

    /** Parse a numeric string like "1,250.00" or "412.1" into a Double */
    private fun parseAmountStr(str: String?): Double? {
        if (str.isNullOrBlank()) return null
        return try {
            str.replace(",", "").trim().toDouble()
        } catch (e: Exception) {
            null
        }
    }

    /** Normalize Eastern Arabic digits (٠١٢...) to Western (012...) */
    private val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    private fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = ARABIC_DIGITS.indexOf(ch)
            if (idx != -1) sb.append(('0'.code + idx).toChar()) else sb.append(ch)
        }
        return sb.toString()
    }

    /** Simple category inference based on merchant name / message content */
    private fun inferCategory(merchant: String?, fullText: String, type: TransactionType): String {
        if (type == TransactionType.INCOME) return "Income / Deposits"
        val combined = "${merchant ?: ""} $fullText".lowercase()
        return when {
            combined.contains("uber") || combined.contains("careem") || combined.contains("metro") ||
            combined.contains("taxi") || combined.contains("petrol") || combined.contains("fuel") ||
            combined.contains("بنزين") || combined.contains("مواصلات") ->
                "Transportation"
            combined.contains("starbucks") || combined.contains("costa") || combined.contains("mcdonald") ||
            combined.contains("kfc") || combined.contains("restaurant") || combined.contains("cafe") ||
            combined.contains("coffee") || combined.contains("pizza") || combined.contains("shawerma") ||
            combined.contains("hungersta") || combined.contains("مطعم") || combined.contains("كافيه") ||
            combined.contains("مأكولات") ->
                "Food & Dining"
            combined.contains("amazon") || combined.contains("noon") || combined.contains("jumia") ||
            combined.contains("zara") || combined.contains("h&m") || combined.contains("carrefour") ||
            combined.contains("lulu") || combined.contains("tamimi") || combined.contains("panda") ||
            combined.contains("supermarket") || combined.contains("hypermarket") || combined.contains("سوبرماركت") ->
                "Shopping & Groceries"
            combined.contains("netflix") || combined.contains("spotify") || combined.contains("cinema") ||
            combined.contains("steam") || combined.contains("playstation") || combined.contains("apple.com") ||
            combined.contains("msarweb") ->
                "Entertainment & Subscriptions"
            combined.contains("bill") || combined.contains("fawry") || combined.contains("stc") ||
            combined.contains("mobily") || combined.contains("zain") || combined.contains("electricity") ||
            combined.contains("water") || combined.contains("كهرباء") || combined.contains("مياه") ||
            combined.contains("فاتورة") ->
                "Bills & Utilities"
            combined.contains("atm") || combined.contains("cash withdrawal") || combined.contains("سحب نقدي") ||
            combined.contains("صراف") ->
                "ATM / Cash"
            combined.contains("pharmacy") || combined.contains("hospital") || combined.contains("clinic") ||
            combined.contains("صيدلية") || combined.contains("مستشفى") ->
                "Health & Medical"
            combined.contains("حوالة") || combined.contains("transfer") || combined.contains("تحويل") ->
                "Transfers"
            combined.contains("riyadh air") || combined.contains("saudia") || combined.contains("flynas") ||
            combined.contains("airline") || combined.contains("booking") || combined.contains("travel") ||
            combined.contains("طيران") ->
                "Travel & Flights"
            else -> "General"
        }
    }
}

package com.banksms.expensetracker.data.parser

import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.model.TransactionType
import java.util.regex.Pattern

data class TemplateTestResult(
    val isMatch: Boolean,
    val parsedTransaction: ParsedTransaction? = null,
    val capturedFields: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

object TemplateMatcher {

    private val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /**
     * Converts Eastern Arabic digits (٠١٢...) to Western ASCII digits (012...).
     */
    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = ARABIC_DIGITS.indexOf(ch)
            if (idx != -1) sb.append(('0'.code + idx).toChar()) else sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * Matches [smsBody] against [template].
     * If the template matches and contains a valid amount, returns [ParsedTransaction], or null otherwise.
     */
    fun match(smsBody: String, sender: String, template: MessageTemplate): ParsedTransaction? {
        if (!template.isEnabled || template.pattern.isBlank()) return null

        // Check sender filter if specified
        if (!isSenderMatch(sender, template.sender)) {
            return null
        }

        val normalizedBody = normalizeDigits(smsBody.trim())
        val regex = compileTemplateToRegex(template.pattern) ?: return null

        val matcher = regex.matcher(normalizedBody)
        if (!matcher.find()) return null

        val captured = extractCapturedGroups(matcher)
        val amountStr = captured["amount"] ?: return null
        val amount = parseAmountStr(amountStr) ?: return null
        if (amount <= 0.0) return null

        val rawCurrency = captured["currency"]?.trim() ?: template.defaultCurrency
        val currency = normalizeCurrency(rawCurrency)

        val merchant = captured["merchant"]?.trim()?.takeIf { it.isNotBlank() }

        val rawCard = captured["card"] ?: captured["account"]
        val card = rawCard?.let { formatCardNumber(it) }

        val balanceStr = captured["balance"]
        val balance = balanceStr?.let { parseAmountStr(it) }

        val type = determineTransactionType(
            typeText = captured["type"],
            smsBody = normalizedBody,
            pattern = template.pattern,
            defaultType = template.defaultType
        )
        val category = captured["category"]?.trim()
            ?: inferCategory(merchant, smsBody, type, template.defaultCategory)

        return ParsedTransaction(
            type = type,
            amount = amount,
            currency = currency,
            merchant = merchant,
            accountOrCard = card,
            availableBalance = balance,
            category = category
        )
    }

    /**
     * Interactive test function for UI previews and sandbox debugging.
     */
    fun test(template: MessageTemplate, sampleSms: String): TemplateTestResult {
        if (template.pattern.isBlank()) {
            return TemplateTestResult(isMatch = false, errorMessage = "Template pattern cannot be empty")
        }
        if (!template.pattern.contains("{amount}")) {
            return TemplateTestResult(
                isMatch = false,
                errorMessage = "Template must include {amount} placeholder (e.g. مبلغ: {amount})"
            )
        }

        val normalizedBody = normalizeDigits(sampleSms.trim())
        val (regex, compileError) = compileTemplateWithDiagnostics(template.pattern)
        if (regex == null) {
            return TemplateTestResult(
                isMatch = false,
                errorMessage = "Failed to compile template pattern into regex: ${compileError ?: "unknown error"}"
            )
        }

        val matcher = regex.matcher(normalizedBody)
        if (!matcher.find()) {
            return TemplateTestResult(
                isMatch = false,
                errorMessage = "Pattern does not match the provided sample message"
            )
        }

        val captured = extractCapturedGroups(matcher)
        val amountStr = captured["amount"]
        val amount = amountStr?.let { parseAmountStr(it) }
        if (amount == null || amount <= 0.0) {
            return TemplateTestResult(
                isMatch = false,
                capturedFields = captured,
                errorMessage = "Failed to parse a valid numerical amount from {amount}: '$amountStr'"
            )
        }

        val rawCurrency = captured["currency"]?.trim() ?: template.defaultCurrency
        val currency = normalizeCurrency(rawCurrency)
        val merchant = captured["merchant"]?.trim()?.takeIf { it.isNotBlank() }
        val rawCard = captured["card"] ?: captured["account"]
        val card = rawCard?.let { formatCardNumber(it) }
        val balanceStr = captured["balance"]
        val balance = balanceStr?.let { parseAmountStr(it) }
        val type = determineTransactionType(
            typeText = captured["type"],
            smsBody = normalizedBody,
            pattern = template.pattern,
            defaultType = template.defaultType
        )
        val category = captured["category"]?.trim()
            ?: inferCategory(merchant, sampleSms, type, template.defaultCategory)

        val parsed = ParsedTransaction(
            type = type,
            amount = amount,
            currency = currency,
            merchant = merchant,
            accountOrCard = card,
            availableBalance = balance,
            category = category
        )

        return TemplateTestResult(
            isMatch = true,
            parsedTransaction = parsed,
            capturedFields = captured
        )
    }

    private fun isSenderMatch(messageSender: String, templateSender: String): Boolean {
        if (templateSender.isBlank() || templateSender == "*") return true
        val msgLower = messageSender.lowercase().trim()
        val tplLower = templateSender.lowercase().trim()
        return msgLower == tplLower || msgLower.contains(tplLower) || tplLower.contains(msgLower)
    }

    /**
     * Compiles a human-readable template string into a regex Pattern.
     */
    private fun compileTemplateToRegex(patternString: String): Pattern? {
        val (pattern, _) = compileTemplateWithDiagnostics(patternString)
        return pattern
    }

    private fun compileTemplateWithDiagnostics(patternString: String): Pair<Pattern?, String?> {
        return try {
            val normalized = normalizeDigits(patternString.trim())
            // Regex to find {placeholder} or * or {...}
            val tokenRegex = Regex("""\{([a-zA-Z0-9_.]+)\}|\*""")

            val regexBuilder = StringBuilder()
            var lastIndex = 0
            val usedGroups = mutableSetOf<String>()

            val matches = tokenRegex.findAll(normalized)
            for (match in matches) {
                // Literal string between last token and current token
                val literal = normalized.substring(lastIndex, match.range.first)
                appendLiteralToRegex(regexBuilder, literal)

                val placeholder = match.groupValues.getOrNull(1)
                if (placeholder != null && placeholder.isNotEmpty()) {
                    appendPlaceholderToRegex(regexBuilder, placeholder.lowercase(), usedGroups)
                } else {
                    // Wildcard '*'
                    regexBuilder.append("""(?:.*?)""")
                }
                lastIndex = match.range.last + 1
            }

            // Trailing literal text
            if (lastIndex < normalized.length) {
                val trailing = normalized.substring(lastIndex)
                appendLiteralToRegex(regexBuilder, trailing)
            }

            val compiled = Pattern.compile(
                regexBuilder.toString(),
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL or Pattern.MULTILINE
            )
            Pair(compiled, null)
        } catch (e: Exception) {
            Pair(null, e.message)
        }
    }

    private fun appendLiteralToRegex(builder: StringBuilder, literal: String) {
        if (literal.isEmpty()) return

        // Normalize spaces and linebreaks: sequence of whitespace -> [\s\r\n]+
        val parts = literal.split(Regex("""\s+"""))
        for (i in parts.indices) {
            val part = parts[i]
            if (part.isNotEmpty()) {
                // Check if part contains colons or punctuation
                if (part.contains(":")) {
                    val subparts = part.split(":")
                    for (j in subparts.indices) {
                        if (subparts[j].isNotEmpty()) {
                            builder.append(Pattern.quote(subparts[j]))
                        }
                        if (j < subparts.size - 1) {
                            builder.append(""":\s*""")
                        }
                    }
                } else {
                    builder.append(Pattern.quote(part))
                }
            }
            if (i < parts.size - 1) {
                builder.append("""[\s\r\n]+""")
            }
        }
    }

    private fun appendPlaceholderToRegex(
        builder: StringBuilder,
        placeholder: String,
        usedGroups: MutableSet<String>
    ) {
        val isFirst = usedGroups.add(placeholder)
        when (placeholder) {
            "amount" -> if (isFirst) builder.append("""(?<amount>[0-9,]+(?:\.[0-9]+)?)""") else builder.append("""(?:[0-9,]+(?:\.[0-9]+)?)""")
            "currency" -> if (isFirst) builder.append("""(?<currency>[A-Za-z]{2,5}|(?:ريال|جنيه|درهم|د\.ك|SR|SAR|EGP|USD|EUR))""") else builder.append("""(?:[A-Za-z]{2,5}|(?:ريال|جنيه|درهم|د\.ك|SR|SAR|EGP|USD|EUR))""")
            "merchant" -> if (isFirst) builder.append("""(?<merchant>[^\r\n;]+)""") else builder.append("""(?:[^\r\n;]+)""")
            "card" -> if (isFirst) builder.append("""(?<card>(?:\*+\s*\d+|\d{4,}))""") else builder.append("""(?:\*+\s*\d+|\d{4,})""")
            "account" -> if (isFirst) builder.append("""(?<account>(?:\*+\s*\d+|\d{4,}))""") else builder.append("""(?:\*+\s*\d+|\d{4,})""")
            "balance" -> if (isFirst) builder.append("""(?<balance>[0-9,]+(?:\.[0-9]+)?)""") else builder.append("""(?:[0-9,]+(?:\.[0-9]+)?)""")
            "type" -> if (isFirst) builder.append("""(?<type>[^\r\n;:]+)""") else builder.append("""(?:[^\r\n;:]+)""")
            "category" -> if (isFirst) builder.append("""(?<category>[^\r\n;]+)""") else builder.append("""(?:[^\r\n;]+)""")
            "date" -> if (isFirst) builder.append("""(?<date>\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})""") else builder.append("""(?:\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})""")
            "time" -> if (isFirst) builder.append("""(?<time>\d{1,2}:\d{2}(?::\d{2})?)""") else builder.append("""(?:\d{1,2}:\d{2}(?::\d{2})?)""")
            "skip", "...", "channel" -> builder.append("""(?:.*?)""")
            else -> {
                // Any custom placeholder treated as non-newline capture
                val cleanKey = placeholder.replace(Regex("[^a-zA-Z0-9]"), "")
                if (cleanKey.isNotEmpty()) {
                    if (isFirst) builder.append("""(?<${cleanKey}>[^\r\n]+)""") else builder.append("""(?:[^\r\n]+)""")
                } else {
                    builder.append("""(?:.*?)""")
                }
            }
        }
    }

    private fun extractCapturedGroups(matcher: java.util.regex.Matcher): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val groupNames = listOf("amount", "currency", "merchant", "card", "account", "balance", "type", "category", "date", "time")
        for (name in groupNames) {
            try {
                val value = matcher.group(name)
                if (!value.isNullOrBlank()) {
                    result[name] = value.trim()
                }
            } catch (_: IllegalArgumentException) {
                // Group was not in the compiled pattern
            }
        }
        return result
    }

    private fun parseAmountStr(str: String?): Double? {
        if (str.isNullOrBlank()) return null
        return try {
            str.replace(",", "").trim().toDouble()
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeCurrency(currency: String): String {
        return when (currency.trim().uppercase()) {
            "SR", "ريال" -> "SAR"
            "جنيه", "LE" -> "EGP"
            "درهم", "AED" -> "AED"
            else -> currency.trim().uppercase()
        }
    }

    private fun formatCardNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (raw.startsWith("**")) {
            raw.replace(" ", "")
        } else if (digits.length >= 4) {
            "**${digits.takeLast(4)}"
        } else {
            raw.trim()
        }
    }

    fun isRefundKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("استرجاع") || lower.contains("استرداد") ||
                lower.contains("عكس") || lower.contains("مرتجع") ||
                lower.contains("مسترجع") || lower.contains("إرجاع") ||
                lower.contains("رد مبلغ") || lower.contains("رد عملية") ||
                lower.contains("refund") || lower.contains("reversal") ||
                lower.contains("reversed") || lower.contains("returned")
    }

    private fun isIncomeKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("وارد") || lower.contains("credit") ||
                lower.contains("deposit") || lower.contains("إيداع") ||
                lower.contains("اضافة") || lower.contains("إضافة") ||
                lower.contains("salary") || lower.contains("راتب")
    }

    private fun isExpenseKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("صادر") || lower.contains("debit") ||
                lower.contains("شراء") || lower.contains("خصم") ||
                lower.contains("سحب") || lower.contains("purchase") ||
                lower.contains("payment") || lower.contains("مدفوع")
    }

    fun determineTransactionType(
        typeText: String?,
        smsBody: String = "",
        pattern: String = "",
        defaultType: TransactionType = TransactionType.EXPENSE
    ): TransactionType {
        // 1. If explicit typeText was captured, examine it first.
        // Refund/reversal keywords take strict precedence over expense keywords (e.g. "استرجاع عملية شراء")
        if (!typeText.isNullOrBlank()) {
            val lower = typeText.lowercase()
            if (isRefundKeyword(lower)) return TransactionType.INCOME
            if (isIncomeKeyword(lower)) return TransactionType.INCOME
            if (isExpenseKeyword(lower)) return TransactionType.EXPENSE
        }

        // 2. Check if the SMS body or template pattern itself explicitly signifies a refund/reversal
        if (isRefundKeyword(smsBody) || isRefundKeyword(pattern)) {
            return TransactionType.INCOME
        }

        // 3. Check if template pattern explicitly signifies income (e.g. "حوالة واردة")
        if (isIncomeKeyword(pattern)) {
            return TransactionType.INCOME
        }

        // 4. Fallback to defaultType
        return defaultType
    }

    private fun inferCategory(
        merchant: String?,
        fullText: String,
        type: TransactionType,
        defaultCategory: String
    ): String {
        if (type == TransactionType.INCOME) return "Income / Deposits"
        val combined = "${merchant ?: ""} $fullText".lowercase()
        return when {
            combined.contains("uber") || combined.contains("careem") || combined.contains("metro") ||
            combined.contains("taxi") || combined.contains("petrol") || combined.contains("fuel") ||
            combined.contains("بنزين") || combined.contains("مواصلات") ->
                "Transportation"
            combined.contains("starbucks") || combined.contains("costa") || combined.contains("mcdonald") ||
            combined.contains("kfc") || combined.contains("restaurant") || combined.contains("cafe") ||
            combined.contains("coffee") || combined.contains("pizza") || combined.contains("مطعم") ||
            combined.contains("كافيه") ->
                "Food & Dining"
            combined.contains("amazon") || combined.contains("noon") || combined.contains("jumia") ||
            combined.contains("carrefour") || combined.contains("lulu") || combined.contains("tamimi") ||
            combined.contains("panda") || combined.contains("سوبرماركت") ->
                "Shopping & Groceries"
            combined.contains("netflix") || combined.contains("spotify") || combined.contains("cinema") ||
            combined.contains("apple.com") ->
                "Entertainment & Subscriptions"
            combined.contains("bill") || combined.contains("stc") || combined.contains("mobily") ||
            combined.contains("zain") || combined.contains("كهرباء") || combined.contains("مياه") ||
            combined.contains("فاتورة") ->
                "Bills & Utilities"
            combined.contains("atm") || combined.contains("cash withdrawal") || combined.contains("صراف") ->
                "ATM / Cash"
            combined.contains("pharmacy") || combined.contains("hospital") || combined.contains("صيدلية") ||
            combined.contains("مستشفى") ->
                "Health & Medical"
            combined.contains("حوالة") || combined.contains("transfer") || combined.contains("تحويل") ->
                "Transfers"
            combined.contains("riyadh air") || combined.contains("saudia") || combined.contains("flynas") ||
            combined.contains("طيران") ->
                "Travel & Flights"
            else -> defaultCategory
        }
    }
}

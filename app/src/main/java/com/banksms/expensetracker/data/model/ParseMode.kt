package com.banksms.expensetracker.data.model

/**
 * Which pipeline produced (and serves) the SMS transactions the app displays.
 * MANUAL = BankSmsParser regex engine -> AppDatabase.
 * AI     = on-device LLM extractor -> AiAppDatabase (separate DB file).
 * Manual expenses live in the shared persistent DB and are mirrored into
 * whichever database is active so they stay visible in both modes.
 */
enum class ParseMode {
    MANUAL,
    AI;

    companion object {
        fun fromStored(value: String?): ParseMode =
            try {
                valueOf(value ?: MANUAL.name)
            } catch (_: Exception) {
                MANUAL
            }
    }
}

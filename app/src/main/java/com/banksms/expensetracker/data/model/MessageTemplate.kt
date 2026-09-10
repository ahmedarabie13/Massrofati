package com.banksms.expensetracker.data.model

import org.json.JSONObject
import java.util.UUID

/**
 * User-defined message template configuration for SMS parsing.
 *
 * Placeholders allowed in [pattern]:
 *   {amount}    - Transaction amount (e.g. 100, 1,250.50) [REQUIRED]
 *   {currency}  - Currency code (e.g. SAR, SR, EGP, USD, ريال)
 *   {merchant}  - Merchant name or payee
 *   {card}      - Card number / masked card (e.g. **7639, 7639)
 *   {account}   - Account number (e.g. **0000)
 *   {balance}   - Available / remaining balance
 *   {type}      - Transaction type keyword (شراء, debit, credit)
 *   {category}  - Category
 *   {date}      - Date string
 *   {time}      - Time string
 *   * or {skip} - Wildcard matching any characters
 */
data class MessageTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sender: String = "", // specific sender (e.g. "SNB", "Alinma") or "*" / blank for all
    val pattern: String,
    val defaultType: TransactionType = TransactionType.EXPENSE,
    val defaultCurrency: String = "SAR",
    val defaultCategory: String = "General",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("sender", sender)
        obj.put("pattern", pattern)
        obj.put("defaultType", defaultType.name)
        obj.put("defaultCurrency", defaultCurrency)
        obj.put("defaultCategory", defaultCategory)
        obj.put("isEnabled", isEnabled)
        obj.put("createdAt", createdAt)
        obj.put("updatedAt", updatedAt)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): MessageTemplate {
            return MessageTemplate(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Untitled Template"),
                sender = obj.optString("sender", ""),
                pattern = obj.optString("pattern", ""),
                defaultType = try {
                    TransactionType.valueOf(obj.optString("defaultType", "EXPENSE"))
                } catch (_: Exception) {
                    TransactionType.EXPENSE
                },
                defaultCurrency = obj.optString("defaultCurrency", "SAR"),
                defaultCategory = obj.optString("defaultCategory", "General"),
                isEnabled = obj.optBoolean("isEnabled", true),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        /**
         * Default seed templates provided out of the box.
         */
        val defaultTemplates = listOf(
            MessageTemplate(
                id = "default_tpl_alinma_pos",
                name = "Alinma POS Purchase",
                sender = "alinma",
                pattern = "شراء عبر: POS\nالبطاقة الائتمانية: {card}\nمبلغ: {currency} {amount}\nلدى: {merchant}\nفي: {time} {date}\nالرصيد: {balance} ريال",
                defaultType = TransactionType.EXPENSE,
                defaultCurrency = "SAR",
                defaultCategory = "General",
                isEnabled = true
            ),
            MessageTemplate(
                id = "default_tpl_alrajhi_online",
                name = "Al Rajhi Internet Purchase",
                sender = "alrajhibank",
                pattern = "شراء إنترنت بـ{currency} {amount}\nعبر{card};فيزا\nلـ{merchant}\nرصيد:{balance} SR",
                defaultType = TransactionType.EXPENSE,
                defaultCurrency = "SAR",
                defaultCategory = "Shopping & Groceries",
                isEnabled = true
            ),
            MessageTemplate(
                id = "default_tpl_snb_purchase",
                name = "SNB Card Purchase",
                sender = "SNB",
                pattern = "Purchase with card {card} of {currency} {amount} at {merchant}. Available Balance: {balance}",
                defaultType = TransactionType.EXPENSE,
                defaultCurrency = "SAR",
                defaultCategory = "General",
                isEnabled = true
            ),
            MessageTemplate(
                id = "default_tpl_incoming_transfer",
                name = "حوالة واردة (Incoming Transfer)",
                sender = "*",
                pattern = "حوالة واردة {amount} {currency}\nمن {merchant}; {account}\nفي {time} {date}",
                defaultType = TransactionType.INCOME,
                defaultCurrency = "SAR",
                defaultCategory = "Income / Deposits",
                isEnabled = true
            ),
            MessageTemplate(
                id = "default_tpl_alinma_refund",
                name = "Alinma Card Purchase Refund",
                sender = "alinma",
                pattern = "استرجاع عملية شراء\nلبطاقة ائتمانية: {card}\nمبلغ: {amount} {currency}\nرقم حساب: {account}\nفي: {merchant}\n*",
                defaultType = TransactionType.INCOME,
                defaultCurrency = "SAR",
                defaultCategory = "Income / Deposits",
                isEnabled = true
            )
        )
    }
}

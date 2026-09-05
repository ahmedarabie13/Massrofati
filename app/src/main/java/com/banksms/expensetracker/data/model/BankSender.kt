package com.banksms.expensetracker.data.model

import org.json.JSONObject

data class BankSender(
    val senderId: String,
    val displayName: String,
    val isMonitored: Boolean = true,
    val customRegex: String? = null,
    val totalTransactionsCount: Int = 0,
    val lastTransactionTime: Long? = null
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("senderId", senderId)
        obj.put("displayName", displayName)
        obj.put("isMonitored", isMonitored)
        if (customRegex != null) obj.put("customRegex", customRegex)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): BankSender {
            return BankSender(
                senderId = obj.optString("senderId", ""),
                displayName = obj.optString("displayName", ""),
                isMonitored = obj.optBoolean("isMonitored", true),
                customRegex = obj.optString("customRegex").takeIf { !it.isNullOrBlank() }
            )
        }

        val defaultSenders = listOf(
            BankSender(senderId = "alinma", displayName = "Alinma Bank", isMonitored = true),
            BankSender(senderId = "alrajhibank", displayName = "Al Rajhi Bank", isMonitored = true),
            BankSender(senderId = "alinmapay", displayName = "AlinmaPay", isMonitored = true)
        )
    }
}

package com.banksms.expensetracker.data.model

data class Transaction(
    val id: Long = 0,
    /**
     * Stable cross-device identity: the Firestore document ID
     * ("sms_<messageId>" or "manual_<manualId>"). [id] is derived from it
     * ([docId].hashCode) so every device agrees on numeric ids too.
     */
    val docId: String = "",
    val messageId: Long = 0,
    val sender: String,
    val type: TransactionType,
    val amount: Double,
    val currency: String = "SAR",
    val merchant: String? = null,
    val accountOrCard: String? = null,
    val availableBalance: Double? = null,
    val category: String = "General",
    val timestamp: Long = System.currentTimeMillis(),
    val rawBody: String = "",
    val isManual: Boolean = false,
    val manualId: String? = null
)

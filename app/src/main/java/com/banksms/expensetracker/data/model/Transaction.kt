package com.banksms.expensetracker.data.model

data class Transaction(
    val id: Long = 0,
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
    val rawBody: String = ""
)

package com.banksms.expensetracker.data.model

data class BankSender(
    val senderId: String,
    val displayName: String,
    val isMonitored: Boolean = true,
    val customRegex: String? = null,
    val totalTransactionsCount: Int = 0,
    val lastTransactionTime: Long? = null
)

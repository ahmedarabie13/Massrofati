package com.banksms.expensetracker.data.parser

import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType

data class ParsedTransaction(
    val type: TransactionType,
    val amount: Double,
    val currency: String,
    val merchant: String? = null,
    val accountOrCard: String? = null,
    val availableBalance: Double? = null,
    val category: String = "General"
) {
    fun toTransaction(
        messageId: Long,
        sender: String,
        timestamp: Long,
        rawBody: String
    ): Transaction = Transaction(
        messageId = messageId,
        sender = sender,
        type = type,
        amount = amount,
        currency = currency,
        merchant = merchant,
        accountOrCard = accountOrCard,
        availableBalance = availableBalance,
        category = category,
        timestamp = timestamp,
        rawBody = rawBody
    )
}

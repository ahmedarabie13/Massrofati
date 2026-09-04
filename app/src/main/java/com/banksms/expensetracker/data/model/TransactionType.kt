package com.banksms.expensetracker.data.model

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            EXPENSE -> "Expense (Debit)"
            INCOME -> "Credit (Income)"
            TRANSFER -> "Transfer"
            UNKNOWN -> "Uncategorized"
        }
}

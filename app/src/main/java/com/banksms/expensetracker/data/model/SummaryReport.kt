package com.banksms.expensetracker.data.model

data class BankExpenseShare(
    val sender: String,
    val totalExpense: Double,
    val totalIncome: Double,
    val transactionCount: Int
)

data class CategoryShare(
    val category: String,
    val totalAmount: Double,
    val count: Int,
    val percentage: Float
)

data class MonthlyTrend(
    val yearMonth: String, // e.g. "2026-08"
    val monthLabel: String, // e.g. "Aug 2026"
    val totalExpense: Double,
    val totalIncome: Double
)

data class SummaryReport(
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val netSavings: Double = 0.0,
    val totalTransactions: Int = 0,
    val currency: String = "SAR",
    val bankShares: List<BankExpenseShare> = emptyList(),
    val categoryShares: List<CategoryShare> = emptyList(),
    val monthlyTrends: List<MonthlyTrend> = emptyList()
)

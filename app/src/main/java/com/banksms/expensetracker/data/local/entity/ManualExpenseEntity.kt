package com.banksms.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.model.TransactionType

@Entity(tableName = "manual_expenses")
data class ManualExpenseEntity(
    @PrimaryKey
    val id: String,
    val amount: Double,
    val currency: String = "SAR",
    val type: String = "EXPENSE",
    val category: String = "General",
    val merchant: String = "",
    val paymentMethod: String = "Cash",
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): ManualExpense = ManualExpense(
        id = id,
        amount = amount,
        currency = currency,
        type = try { TransactionType.valueOf(type) } catch (_: Exception) { TransactionType.EXPENSE },
        category = category,
        merchant = merchant,
        paymentMethod = paymentMethod,
        timestamp = timestamp,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(expense: ManualExpense): ManualExpenseEntity = ManualExpenseEntity(
            id = expense.id,
            amount = expense.amount,
            currency = expense.currency,
            type = expense.type.name,
            category = expense.category,
            merchant = expense.merchant,
            paymentMethod = expense.paymentMethod,
            timestamp = expense.timestamp,
            note = expense.note,
            createdAt = expense.createdAt,
            updatedAt = expense.updatedAt
        )
    }
}

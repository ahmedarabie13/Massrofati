package com.banksms.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["messageId"], unique = true),
        Index(value = ["sender"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: Long,
    val sender: String,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER", "UNKNOWN"
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val accountOrCard: String?,
    val availableBalance: Double?,
    val category: String,
    val timestamp: Long,
    val rawBody: String
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        messageId = messageId,
        sender = sender,
        type = try { TransactionType.valueOf(type) } catch (e: Exception) { TransactionType.UNKNOWN },
        amount = amount,
        currency = currency,
        merchant = merchant,
        accountOrCard = accountOrCard,
        availableBalance = availableBalance,
        category = category,
        timestamp = timestamp,
        rawBody = rawBody
    )

    companion object {
        fun fromDomain(transaction: Transaction): TransactionEntity = TransactionEntity(
            id = transaction.id,
            messageId = transaction.messageId,
            sender = transaction.sender,
            type = transaction.type.name,
            amount = transaction.amount,
            currency = transaction.currency,
            merchant = transaction.merchant,
            accountOrCard = transaction.accountOrCard,
            availableBalance = transaction.availableBalance,
            category = transaction.category,
            timestamp = transaction.timestamp,
            rawBody = transaction.rawBody
        )
    }
}

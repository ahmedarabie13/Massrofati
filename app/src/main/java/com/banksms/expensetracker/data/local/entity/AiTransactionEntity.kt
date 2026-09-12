package com.banksms.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType

/**
 * Same shape as [TransactionEntity] but stored in the separate AI database
 * ([AiAppDatabase]) so LLM-extracted transactions never mix with the
 * regex-parser ones. The app reads from one or the other based on ParseMode.
 */
@Entity(
    tableName = "ai_transactions",
    indices = [
        Index(value = ["messageId"], unique = true),
        Index(value = ["sender"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"]),
        Index(value = ["manualId"])
    ]
)
data class AiTransactionEntity(
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
    val rawBody: String,
    val isManual: Boolean = false,
    val manualId: String? = null
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
        rawBody = rawBody,
        isManual = isManual,
        manualId = manualId
    )

    companion object {
        fun fromDomain(transaction: Transaction): AiTransactionEntity = AiTransactionEntity(
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
            rawBody = transaction.rawBody,
            isManual = transaction.isManual,
            manualId = transaction.manualId
        )
    }
}

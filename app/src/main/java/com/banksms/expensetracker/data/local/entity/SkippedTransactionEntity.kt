package com.banksms.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.model.TransactionType

@Entity(
    tableName = "skipped_transactions",
    indices = [
        Index(value = ["originalMessageId"]),
        Index(value = ["sender"]),
        Index(value = ["sender", "rawBody"])
    ]
)
data class SkippedTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalMessageId: Long,
    val sender: String,
    val amount: Double,
    val currency: String = "SAR",
    val type: String = "EXPENSE",
    val merchant: String? = null,
    val category: String = "General",
    val rawBody: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val skippedAt: Long = System.currentTimeMillis(),
    val reason: String = "User skipped"
) {
    fun toDomain(): SkippedTransaction = SkippedTransaction(
        originalMessageId = originalMessageId,
        sender = sender,
        amount = amount,
        currency = currency,
        type = try { TransactionType.valueOf(type) } catch (_: Exception) { TransactionType.EXPENSE },
        merchant = merchant,
        category = category,
        rawBody = rawBody,
        timestamp = timestamp,
        skippedAt = skippedAt,
        reason = reason
    )

    companion object {
        fun fromDomain(skipped: SkippedTransaction): SkippedTransactionEntity = SkippedTransactionEntity(
            originalMessageId = skipped.originalMessageId,
            sender = skipped.sender,
            amount = skipped.amount,
            currency = skipped.currency,
            type = skipped.type.name,
            merchant = skipped.merchant,
            category = skipped.category,
            rawBody = skipped.rawBody,
            timestamp = skipped.timestamp,
            skippedAt = skipped.skippedAt,
            reason = skipped.reason
        )
    }
}

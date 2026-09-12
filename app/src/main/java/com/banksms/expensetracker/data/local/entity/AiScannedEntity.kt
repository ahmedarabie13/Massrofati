package com.banksms.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One inbox message the model has already judged. Non-transactions leave no
 * row in ai_transactions, so without this record every rescan would pay
 * inference for them again. Verdicts never change (message content is
 * immutable), so rows are write-once.
 */
@Entity(
    tableName = "ai_scanned",
    indices = [Index(value = ["sender"])]
)
data class AiScannedEntity(
    @PrimaryKey
    val messageId: Long,
    val sender: String,
    val timestamp: Long,
    val rawBody: String,
    val isTransaction: Boolean
)

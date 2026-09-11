package com.banksms.expensetracker.data.local.dao

import androidx.room.*
import com.banksms.expensetracker.data.local.entity.SkippedTransactionEntity

@Dao
interface SkippedTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skipped: SkippedTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(skipped: List<SkippedTransactionEntity>)

    @Query("DELETE FROM skipped_transactions WHERE originalMessageId = :messageId AND originalMessageId != 0")
    suspend fun deleteByMessageId(messageId: Long): Int

    @Query("""
        DELETE FROM skipped_transactions 
        WHERE sender = :sender AND rawBody = :rawBody AND rawBody != ''
    """)
    suspend fun deleteBySenderAndBody(sender: String, rawBody: String): Int

    @Query("""
        DELETE FROM skipped_transactions
        WHERE sender = :sender AND amount = :amount AND ABS(timestamp - :timestamp) < 60000
    """)
    suspend fun deleteBySenderAmountTimestamp(sender: String, amount: Double, timestamp: Long): Int

    @Query("DELETE FROM skipped_transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM skipped_transactions ORDER BY skippedAt DESC")
    suspend fun getAll(): List<SkippedTransactionEntity>

    /**
     * Looks up an existing skipped transaction by its natural key so migration
     * can be re-run without creating duplicate rows.
     */
    @Query("""
        SELECT * FROM skipped_transactions
        WHERE (:originalMessageId != 0 AND originalMessageId = :originalMessageId)
           OR (rawBody != '' AND sender = :sender AND rawBody = :rawBody)
           OR (sender = :sender AND ABS(timestamp - :timestamp) < 60000
               AND ABS(amount - :amount) < 0.001)
        LIMIT 1
    """)
    suspend fun findExisting(
        originalMessageId: Long,
        sender: String,
        rawBody: String,
        amount: Double,
        timestamp: Long
    ): SkippedTransactionEntity?

    @Query("""
        SELECT COUNT(*) FROM skipped_transactions 
        WHERE (originalMessageId = :messageId AND :messageId != 0)
        OR (sender = :sender AND rawBody = :rawBody AND :rawBody != '' AND rawBody != '')
        OR (sender = :sender AND ABS(timestamp - :timestamp) < 60000 AND rawBody LIKE '%' || CAST(CAST(:amount AS INTEGER) AS TEXT) || '%')
    """)
    suspend fun countMatches(messageId: Long, sender: String, timestamp: Long, rawBody: String, amount: Double): Int

    @Query("SELECT COUNT(*) FROM skipped_transactions")
    suspend fun count(): Int
}

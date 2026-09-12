package com.banksms.expensetracker.data.local.dao

import androidx.room.*
import com.banksms.expensetracker.data.local.entity.AiTransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Mirrors [TransactionDao] against the `ai_transactions` table in the
 * separate AI database. Kept as a duplicate (rather than a generic DAO)
 * because Room generates implementations per entity type.
 */
@Dao
interface AiTransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: AiTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<AiTransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: AiTransactionEntity)

    @Delete
    suspend fun delete(transaction: AiTransactionEntity)

    @Query("DELETE FROM ai_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM ai_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AiTransactionEntity?

    @Query("SELECT * FROM ai_transactions WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: Long): AiTransactionEntity?

    @Query("DELETE FROM ai_transactions WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: Long)

    @Query("SELECT * FROM ai_transactions WHERE manualId = :manualId LIMIT 1")
    suspend fun getByManualId(manualId: String): AiTransactionEntity?

    @Query("DELETE FROM ai_transactions WHERE manualId = :manualId")
    suspend fun deleteByManualId(manualId: String)

    @Query("SELECT manualId FROM ai_transactions WHERE isManual = 1 AND manualId IS NOT NULL")
    suspend fun getAllManualIds(): List<String>

    @Query("""
        SELECT * FROM ai_transactions 
        WHERE sender = :sender 
        AND ABS(timestamp - :timestamp) < 120000
        AND (rawBody = :rawBody OR (amount = :amount AND type = :type))
        ORDER BY id ASC 
        LIMIT 1
    """)
    suspend fun findDuplicate(
        sender: String,
        amount: Double,
        type: String,
        rawBody: String,
        timestamp: Long
    ): AiTransactionEntity?

    @Query("""
        DELETE FROM ai_transactions 
        WHERE id NOT IN (
            SELECT MIN(id) 
            FROM ai_transactions 
            GROUP BY sender, amount, rawBody, (timestamp / 60000)
        )
    """)
    suspend fun deleteDuplicates()

    @Query("SELECT * FROM ai_transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<AiTransactionEntity>>

    @Query("SELECT * FROM ai_transactions ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<AiTransactionEntity>

    @Query("""
        SELECT * FROM ai_transactions 
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    fun getTransactionsInRangeFlow(startTime: Long, endTime: Long): Flow<List<AiTransactionEntity>>

    @Query("""
        SELECT * FROM ai_transactions 
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        AND (:type IS NULL OR type = :type)
        AND (:sender IS NULL OR sender = :sender)
        AND (:category IS NULL OR category = :category)
        AND (:searchQuery IS NULL OR merchant LIKE '%' || :searchQuery || '%' OR rawBody LIKE '%' || :searchQuery || '%')
        AND (:manualOnly = 0 OR isManual = 1)
        ORDER BY timestamp DESC
    """)
    fun getFilteredTransactionsFlow(
        startTime: Long,
        endTime: Long,
        type: String? = null,
        sender: String? = null,
        category: String? = null,
        searchQuery: String? = null,
        manualOnly: Boolean = false
    ): Flow<List<AiTransactionEntity>>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM ai_transactions 
        WHERE type = 'EXPENSE' AND timestamp >= :startTime AND timestamp <= :endTime
    """)
    fun getTotalExpenseFlow(startTime: Long, endTime: Long): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM ai_transactions 
        WHERE type = 'INCOME' AND timestamp >= :startTime AND timestamp <= :endTime
    """)
    fun getTotalIncomeFlow(startTime: Long, endTime: Long): Flow<Double>

    @Query("""
        SELECT 
            sender,
            SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS totalExpense,
            SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS totalIncome,
            COUNT(*) AS transactionCount
        FROM ai_transactions
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        GROUP BY sender
        ORDER BY totalExpense DESC
    """)
    fun getBankExpenseSummaryFlow(startTime: Long, endTime: Long): Flow<List<BankExpenseDbSummary>>

    @Query("""
        SELECT 
            category,
            SUM(amount) AS totalAmount,
            COUNT(*) AS count
        FROM ai_transactions
        WHERE type = 'EXPENSE' AND timestamp >= :startTime AND timestamp <= :endTime
        GROUP BY category
        ORDER BY totalAmount DESC
    """)
    fun getCategoryExpenseSummaryFlow(startTime: Long, endTime: Long): Flow<List<CategoryDbSummary>>

    @Query("""
        SELECT 
            strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch')) AS yearMonth,
            SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS totalExpense,
            SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS totalIncome
        FROM ai_transactions
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlyTrendsFlow(): Flow<List<MonthlyDbTrend>>

    @Query("SELECT COUNT(*) FROM ai_transactions")
    suspend fun countTransactions(): Int

    @Query("SELECT MAX(timestamp) FROM ai_transactions WHERE sender = :sender")
    suspend fun getLatestTimestampForSender(sender: String): Long?
}

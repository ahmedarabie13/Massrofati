package com.banksms.expensetracker.data.local.dao

import androidx.room.*
import com.banksms.expensetracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class BankExpenseDbSummary(
    val sender: String,
    val totalExpense: Double?,
    val totalIncome: Double?,
    val transactionCount: Int
)

data class CategoryDbSummary(
    val category: String,
    val totalAmount: Double?,
    val count: Int
)

data class MonthlyDbTrend(
    val yearMonth: String,
    val totalExpense: Double?,
    val totalIncome: Double?
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: Long)

    @Query("SELECT * FROM transactions WHERE manualId = :manualId LIMIT 1")
    suspend fun getByManualId(manualId: String): TransactionEntity?

    @Query("DELETE FROM transactions WHERE manualId = :manualId")
    suspend fun deleteByManualId(manualId: String)

    @Query("SELECT manualId FROM transactions WHERE isManual = 1 AND manualId IS NOT NULL")
    suspend fun getAllManualIds(): List<String>

    @Query("""
        SELECT * FROM transactions 
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
    ): TransactionEntity?

    @Query("""
        DELETE FROM transactions 
        WHERE id NOT IN (
            SELECT MIN(id) 
            FROM transactions 
            GROUP BY sender, amount, rawBody, (timestamp / 60000)
        )
    """)
    suspend fun deleteDuplicates()

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    fun getTransactionsInRangeFlow(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        AND (:type IS NULL OR type = :type)
        AND (:sender IS NULL OR sender = :sender)
        AND (:category IS NULL OR category = :category)
        AND (:searchQuery IS NULL OR merchant LIKE '%' || :searchQuery || '%' OR rawBody LIKE '%' || :searchQuery || '%')
        ORDER BY timestamp DESC
    """)
    fun getFilteredTransactionsFlow(
        startTime: Long,
        endTime: Long,
        type: String? = null,
        sender: String? = null,
        category: String? = null,
        searchQuery: String? = null
    ): Flow<List<TransactionEntity>>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions 
        WHERE type = 'EXPENSE' AND timestamp >= :startTime AND timestamp <= :endTime
    """)
    fun getTotalExpenseFlow(startTime: Long, endTime: Long): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions 
        WHERE type = 'INCOME' AND timestamp >= :startTime AND timestamp <= :endTime
    """)
    fun getTotalIncomeFlow(startTime: Long, endTime: Long): Flow<Double>

    @Query("""
        SELECT 
            sender,
            SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS totalExpense,
            SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS totalIncome,
            COUNT(*) AS transactionCount
        FROM transactions
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
        FROM transactions
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
        FROM transactions
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlyTrendsFlow(): Flow<List<MonthlyDbTrend>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countTransactions(): Int

    @Query("SELECT MAX(timestamp) FROM transactions WHERE sender = :sender")
    suspend fun getLatestTimestampForSender(sender: String): Long?
}

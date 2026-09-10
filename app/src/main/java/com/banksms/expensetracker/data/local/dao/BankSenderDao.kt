package com.banksms.expensetracker.data.local.dao

import androidx.room.*
import com.banksms.expensetracker.data.local.entity.BankSenderEntity
import kotlinx.coroutines.flow.Flow

data class BankSenderWithStats(
    val senderId: String,
    val displayName: String,
    val isMonitored: Boolean,
    val customRegex: String?,
    val totalTransactionsCount: Int,
    val lastTransactionTime: Long?
)

@Dao
interface BankSenderDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sender: BankSenderEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(senders: List<BankSenderEntity>)

    @Update
    suspend fun update(sender: BankSenderEntity)

    @Delete
    suspend fun delete(sender: BankSenderEntity)

    @Query("SELECT * FROM bank_senders ORDER BY displayName ASC")
    fun getAllSendersFlow(): Flow<List<BankSenderEntity>>

    @Query("SELECT * FROM bank_senders WHERE isMonitored = 1")
    fun getMonitoredSendersFlow(): Flow<List<BankSenderEntity>>

    @Query("SELECT * FROM bank_senders WHERE isMonitored = 1")
    suspend fun getMonitoredSendersSync(): List<BankSenderEntity>

    @Query("SELECT * FROM bank_senders")
    suspend fun getAllSendersSync(): List<BankSenderEntity>

    @Query("""
        SELECT 
            b.senderId,
            b.displayName,
            b.isMonitored,
            b.customRegex,
            COUNT(t.id) AS totalTransactionsCount,
            MAX(t.timestamp) AS lastTransactionTime
        FROM bank_senders b
        LEFT JOIN transactions t ON b.senderId = t.sender
        GROUP BY b.senderId
        ORDER BY b.isMonitored DESC, totalTransactionsCount DESC, b.displayName ASC
    """)
    fun getSendersWithStatsFlow(): Flow<List<BankSenderWithStats>>

    @Query("SELECT * FROM bank_senders WHERE senderId = :senderId LIMIT 1")
    suspend fun getBySenderId(senderId: String): BankSenderEntity?

    @Query("UPDATE bank_senders SET isMonitored = :isMonitored WHERE senderId = :senderId")
    suspend fun setMonitored(senderId: String, isMonitored: Boolean)
}

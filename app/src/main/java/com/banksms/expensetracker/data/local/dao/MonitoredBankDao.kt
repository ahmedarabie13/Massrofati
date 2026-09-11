package com.banksms.expensetracker.data.local.dao

import androidx.room.*
import com.banksms.expensetracker.data.local.entity.MonitoredBankEntity

@Dao
interface MonitoredBankDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bank: MonitoredBankEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(banks: List<MonitoredBankEntity>)

    @Update
    suspend fun update(bank: MonitoredBankEntity)

    @Query("DELETE FROM monitored_banks WHERE senderId = :senderId")
    suspend fun deleteBySenderId(senderId: String): Int

    @Query("DELETE FROM monitored_banks")
    suspend fun deleteAll()

    @Query("SELECT * FROM monitored_banks ORDER BY displayName ASC")
    suspend fun getAll(): List<MonitoredBankEntity>

    @Query("SELECT * FROM monitored_banks WHERE isMonitored = 1")
    suspend fun getMonitored(): List<MonitoredBankEntity>

    @Query("SELECT * FROM monitored_banks WHERE senderId = :senderId LIMIT 1")
    suspend fun getBySenderId(senderId: String): MonitoredBankEntity?

    @Query("UPDATE monitored_banks SET isMonitored = :isMonitored WHERE senderId = :senderId")
    suspend fun toggleMonitored(senderId: String, isMonitored: Boolean)

    @Query("SELECT COUNT(*) FROM monitored_banks")
    suspend fun count(): Int
}

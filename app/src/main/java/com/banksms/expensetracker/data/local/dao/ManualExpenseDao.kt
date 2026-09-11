package com.banksms.expensetracker.data.local.dao

import androidx.room.*
import com.banksms.expensetracker.data.local.entity.ManualExpenseEntity

@Dao
interface ManualExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ManualExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ManualExpenseEntity>)

    @Update
    suspend fun update(expense: ManualExpenseEntity)

    @Query("DELETE FROM manual_expenses WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM manual_expenses")
    suspend fun deleteAll()

    @Query("SELECT * FROM manual_expenses ORDER BY timestamp DESC")
    suspend fun getAll(): List<ManualExpenseEntity>

    @Query("SELECT * FROM manual_expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ManualExpenseEntity?

    @Query("SELECT COUNT(*) FROM manual_expenses")
    suspend fun count(): Int
}

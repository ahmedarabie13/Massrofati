package com.banksms.expensetracker.data.local.dao

import androidx.room.*
import com.banksms.expensetracker.data.local.entity.MessageTemplateEntity

@Dao
interface MessageTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: MessageTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(templates: List<MessageTemplateEntity>)

    @Update
    suspend fun update(template: MessageTemplateEntity)

    @Query("DELETE FROM message_templates WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM message_templates")
    suspend fun deleteAll()

    @Query("SELECT * FROM message_templates ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MessageTemplateEntity>

    @Query("SELECT * FROM message_templates WHERE isEnabled = 1 ORDER BY updatedAt DESC")
    suspend fun getEnabled(): List<MessageTemplateEntity>

    @Query("SELECT * FROM message_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageTemplateEntity?

    @Query("UPDATE message_templates SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleEnabled(id: String, isEnabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM message_templates")
    suspend fun count(): Int
}

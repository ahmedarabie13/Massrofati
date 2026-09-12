package com.banksms.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.banksms.expensetracker.data.local.entity.AiScannedEntity

@Dao
interface AiScannedDao {

    /** Verdicts are write-once: first judgment wins. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsert(record: AiScannedEntity)

    @Query("SELECT * FROM ai_scanned WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: Long): AiScannedEntity?

    @Query("DELETE FROM ai_scanned WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: Long)

    @Query("DELETE FROM ai_scanned WHERE sender = :sender AND rawBody = :rawBody")
    suspend fun deleteBySenderAndBody(sender: String, rawBody: String)

    @Query("DELETE FROM ai_scanned")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ai_scanned")
    suspend fun count(): Int
}

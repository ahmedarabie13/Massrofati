package com.banksms.expensetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.banksms.expensetracker.data.local.dao.AiScannedDao
import com.banksms.expensetracker.data.local.dao.AiTransactionDao
import com.banksms.expensetracker.data.local.entity.AiScannedEntity
import com.banksms.expensetracker.data.local.entity.AiTransactionEntity

/**
 * Separate Room database file holding ONLY LLM-extracted transactions
 * (plus per-message scan verdicts so judged messages are never resent).
 * The regex-parser data lives in [AppDatabase]; the app reads from one or
 * the other depending on the active [ParseMode][com.banksms.expensetracker.data.model.ParseMode].
 */
@Database(
    entities = [
        AiTransactionEntity::class,
        AiScannedEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AiAppDatabase : RoomDatabase() {

    abstract fun aiTransactionDao(): AiTransactionDao
    abstract fun aiScannedDao(): AiScannedDao

    companion object {
        @Volatile
        private var INSTANCE: AiAppDatabase? = null

        /** v1 -> v2: scan-verdict table. Explicit (not destructive) so existing AI data survives. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_scanned` (" +
                        "`messageId` INTEGER NOT NULL, " +
                        "`sender` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`rawBody` TEXT NOT NULL, " +
                        "`isTransaction` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`messageId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_scanned_sender` ON `ai_scanned` (`sender`)")
            }
        }

        fun getInstance(context: Context): AiAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiAppDatabase::class.java,
                    "bank_sms_tracker_ai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

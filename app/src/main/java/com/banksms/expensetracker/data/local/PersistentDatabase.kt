package com.banksms.expensetracker.data.local

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.banksms.expensetracker.data.file.ExpenseFileManager
import com.banksms.expensetracker.data.local.dao.ManualExpenseDao
import com.banksms.expensetracker.data.local.dao.MessageTemplateDao
import com.banksms.expensetracker.data.local.dao.MonitoredBankDao
import com.banksms.expensetracker.data.local.dao.SkippedTransactionDao
import com.banksms.expensetracker.data.local.entity.ManualExpenseEntity
import com.banksms.expensetracker.data.local.entity.MessageTemplateEntity
import com.banksms.expensetracker.data.local.entity.MonitoredBankEntity
import com.banksms.expensetracker.data.local.entity.SkippedTransactionEntity
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.MessageTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Persistent Room database stored OUTSIDE the app's internal data directory
 * (at Documents/Masari/masari_persistent.db) so that data survives app data
 * clearing and even app uninstallation.
 *
 * Contains all user configuration data:
 * - Message templates (including defaults)
 * - Manual expenses
 * - Skipped transactions
 * - Monitored banks
 */
@Database(
    entities = [
        ManualExpenseEntity::class,
        SkippedTransactionEntity::class,
        MessageTemplateEntity::class,
        MonitoredBankEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PersistentDatabase : RoomDatabase() {

    abstract fun manualExpenseDao(): ManualExpenseDao
    abstract fun skippedTransactionDao(): SkippedTransactionDao
    abstract fun messageTemplateDao(): MessageTemplateDao
    abstract fun monitoredBankDao(): MonitoredBankDao

    companion object {
        private const val TAG = "PersistentDatabase"
        private const val DB_NAME = "masari_persistent.db"
        private const val PREF_KEY_MIGRATED = "masari_db_migrated_v1"

        @Volatile
        private var INSTANCE: PersistentDatabase? = null

        /**
         * Resolves the best external directory for storing the persistent database.
         * Priority: Documents/Masari > Downloads/Masari > app external files > app internal
         */
        private fun resolveDbPath(context: Context): String {
            val candidateDirs = listOf(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Masari"),
                File(Environment.getExternalStorageDirectory(), "Documents/Masari"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Masari"),
                File(Environment.getExternalStorageDirectory(), "Download/Masari")
            )

            // 1. If DB already exists in any candidate dir, prefer it!
            for (dir in candidateDirs) {
                try {
                    val dbFile = File(dir, DB_NAME)
                    if (dbFile.exists() && dbFile.length() > 0L) {
                        Log.d(TAG, "Found existing DB at: ${dbFile.absolutePath}")
                        return dbFile.absolutePath
                    }
                } catch (_: Throwable) {}
            }

            // 2. Otherwise pick the first writable external candidate dir
            for (dir in candidateDirs) {
                try {
                    if (!dir.exists()) dir.mkdirs()
                    val testFile = File(dir, ".db_perm_test_${System.currentTimeMillis()}")
                    testFile.writeText("ok")
                    if (testFile.readText() == "ok") {
                        testFile.delete()
                        val dbFile = File(dir, DB_NAME)
                        Log.d(TAG, "Using writable external dir for DB: ${dbFile.absolutePath}")
                        return dbFile.absolutePath
                    }
                    testFile.delete()
                } catch (e: Throwable) {
                    Log.w(TAG, "Candidate dir ${dir.absolutePath} not writable: ${e.message}")
                }
            }

            // 3. Try app external files dir (survives data clear, not uninstall)
            try {
                val extDir = context.getExternalFilesDir(null)?.let { File(it, "Masari") }
                if (extDir != null) {
                    if (!extDir.exists()) extDir.mkdirs()
                    val dbFile = File(extDir, DB_NAME)
                    Log.d(TAG, "Using app external files dir for DB: ${dbFile.absolutePath}")
                    return dbFile.absolutePath
                }
            } catch (e: Throwable) {
                Log.w(TAG, "App external dir not available: ${e.message}")
            }

            // 4. Fallback: app internal files dir
            val internalDir = File(context.filesDir, "Masari")
            if (!internalDir.exists()) internalDir.mkdirs()
            val dbFile = File(internalDir, DB_NAME)
            Log.w(TAG, "Falling back to internal storage for DB: ${dbFile.absolutePath}")
            return dbFile.absolutePath
        }

        fun getInstance(context: Context, fileManager: ExpenseFileManager? = null): PersistentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, fileManager).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, fileManager: ExpenseFileManager?): PersistentDatabase {
            val dbPath = resolveDbPath(context)
            Log.d(TAG, "Building PersistentDatabase at: $dbPath")

            return Room.databaseBuilder(
                context.applicationContext,
                PersistentDatabase::class.java,
                dbPath
            )
                .addCallback(SeedCallback(context, fileManager))
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Callback that seeds default data on first creation and migrates JSON file data.
         */
        private class SeedCallback(
            private val context: Context,
            private val fileManager: ExpenseFileManager?
        ) : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d(TAG, "PersistentDatabase created — seeding defaults")
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedDefaults(database)
                        migrateFromJsonFiles(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Migrate from JSON files if not already done (covers the case where
                // the DB existed but JSON migration hadn't happened yet)
                val prefs = context.getSharedPreferences("masari_prefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean(PREF_KEY_MIGRATED, false)) {
                    INSTANCE?.let { database ->
                        CoroutineScope(Dispatchers.IO).launch {
                            migrateFromJsonFiles(database)
                            prefs.edit().putBoolean(PREF_KEY_MIGRATED, true).apply()
                        }
                    }
                }
            }

            private suspend fun seedDefaults(database: PersistentDatabase) {
                try {
                    // Seed default templates
                    val templateDao = database.messageTemplateDao()
                    if (templateDao.count() == 0) {
                        val defaultEntities = MessageTemplate.defaultTemplates.map {
                            MessageTemplateEntity.fromDomain(it)
                        }
                        templateDao.upsertAll(defaultEntities)
                        Log.d(TAG, "Seeded ${defaultEntities.size} default templates")
                    }

                    // Seed default monitored banks
                    val bankDao = database.monitoredBankDao()
                    if (bankDao.count() == 0) {
                        val defaultEntities = BankSender.defaultSenders.map {
                            MonitoredBankEntity.fromDomain(it)
                        }
                        bankDao.upsertAll(defaultEntities)
                        Log.d(TAG, "Seeded ${defaultEntities.size} default banks")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error seeding defaults: ${e.message}")
                }
            }

            private suspend fun migrateFromJsonFiles(database: PersistentDatabase) {
                val fm = fileManager ?: return
                try {
                    // Migrate templates from JSON (merge — don't overwrite existing)
                    val jsonTemplates = fm.getMessageTemplates()
                    if (jsonTemplates.isNotEmpty()) {
                        val templateDao = database.messageTemplateDao()
                        for (template in jsonTemplates) {
                            val existing = templateDao.getById(template.id)
                            if (existing == null) {
                                templateDao.upsert(MessageTemplateEntity.fromDomain(template))
                            }
                        }
                        Log.d(TAG, "Migrated ${jsonTemplates.size} templates from JSON")
                    }

                    // Migrate manual expenses
                    val jsonExpenses = fm.getManualExpenses()
                    if (jsonExpenses.isNotEmpty()) {
                        val expenseDao = database.manualExpenseDao()
                        for (expense in jsonExpenses) {
                            val existing = expenseDao.getById(expense.id)
                            if (existing == null) {
                                expenseDao.upsert(ManualExpenseEntity.fromDomain(expense))
                            }
                        }
                        Log.d(TAG, "Migrated ${jsonExpenses.size} manual expenses from JSON")
                    }

                    // Migrate skipped transactions
                    val jsonSkipped = fm.getSkippedTransactions()
                    if (jsonSkipped.isNotEmpty()) {
                        val skippedDao = database.skippedTransactionDao()
                        val entities = jsonSkipped.map { SkippedTransactionEntity.fromDomain(it) }
                        skippedDao.insertAll(entities)
                        Log.d(TAG, "Migrated ${jsonSkipped.size} skipped transactions from JSON")
                    }

                    // Migrate monitored banks
                    val jsonBanks = fm.getMonitoredBanks()
                    if (jsonBanks.isNotEmpty()) {
                        val bankDao = database.monitoredBankDao()
                        for (bank in jsonBanks) {
                            val existing = bankDao.getBySenderId(bank.senderId)
                            if (existing == null) {
                                bankDao.upsert(MonitoredBankEntity.fromDomain(bank))
                            }
                        }
                        Log.d(TAG, "Migrated ${jsonBanks.size} monitored banks from JSON")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error migrating from JSON files: ${e.message}")
                }
            }
        }

        fun getDatabasePath(context: Context): String = resolveDbPath(context)
    }
}

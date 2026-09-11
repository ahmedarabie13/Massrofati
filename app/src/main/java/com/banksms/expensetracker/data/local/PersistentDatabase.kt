package com.banksms.expensetracker.data.local

import android.content.Context
import android.content.SharedPreferences
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
        private const val PERSISTENT_PREFS_NAME = "masari_persistent_prefs"
        private const val PREF_DB_PATH = "masari_db_path_v1"

        @Volatile
        private var INSTANCE: PersistentDatabase? = null

        /** File manager used to read the legacy JSON backup files. */
        @Volatile
        private var cachedFileManager: ExpenseFileManager? = null

        /**
         * In-process guard so JSON -> DB migration runs at most once per
         * process. It is deliberately NOT persisted to disk: migration is
         * idempotent (see [PersistentDataMigrator]) and must be re-attempted
         * on a later open if it failed or if it ran before the legacy JSON
         * files were readable (e.g. before the user granted All Files Access).
         */
        @Volatile
        private var migratedInProcess = false

        /**
         * Resolves the best directory for storing the persistent database.
         * Priority: Documents/Masari > Downloads/Masari > app external files > app internal.
         *
         * The resolved path is persisted so the app always keeps using the
         * same database file across sessions — even when the storage
         * permission state changes between launches (otherwise the app could
         * create a second, empty database and appear to "lose" data).
         */
        private fun resolveDbPath(context: Context, prefs: SharedPreferences): String {
            val candidateDirs = listOf(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Masari"),
                File(Environment.getExternalStorageDirectory(), "Documents/Masari"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Masari"),
                File(Environment.getExternalStorageDirectory(), "Download/Masari")
            )

            // 0. Honor a previously resolved path (sticky), so the DB never silently moves.
            val stored = prefs.getString(PREF_DB_PATH, null)
            if (stored != null) {
                val storedFile = File(stored)
                if (storedFile.exists() && storedFile.length() > 0L && isWritable(storedFile.parentFile)) {
                    Log.d(TAG, "Using stored DB path: ${storedFile.absolutePath}")
                    return storedFile.absolutePath
                }
                Log.w(TAG, "Stored DB path no longer usable, re-resolving: $stored")
            }

            // 1. If the DB already exists in any candidate public dir, reuse it.
            for (dir in candidateDirs) {
                try {
                    val dbFile = File(dir, DB_NAME)
                    if (dbFile.exists() && dbFile.length() > 0L) {
                        Log.d(TAG, "Found existing DB at: ${dbFile.absolutePath}")
                        prefs.edit().putString(PREF_DB_PATH, dbFile.absolutePath).apply()
                        return dbFile.absolutePath
                    }
                } catch (_: Throwable) {}
            }

            // 1b. Reuse a DB previously created in app-external or app-internal
            //     storage (covers reinstalls / sessions that ran before the
            //     All Files Access permission was granted).
            for (dir in appFallbackDirs(context)) {
                try {
                    val dbFile = File(dir, DB_NAME)
                    if (dbFile.exists() && dbFile.length() > 0L) {
                        Log.d(TAG, "Reusing existing DB at: ${dbFile.absolutePath}")
                        prefs.edit().putString(PREF_DB_PATH, dbFile.absolutePath).apply()
                        return dbFile.absolutePath
                    }
                } catch (_: Throwable) {}
            }

            // 2. Otherwise pick the first writable external candidate dir.
            for (dir in candidateDirs) {
                try {
                    if (!dir.exists()) dir.mkdirs()
                    if (isWritable(dir)) {
                        val dbFile = File(dir, DB_NAME)
                        Log.d(TAG, "Using writable external dir for DB: ${dbFile.absolutePath}")
                        prefs.edit().putString(PREF_DB_PATH, dbFile.absolutePath).apply()
                        return dbFile.absolutePath
                    }
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
                    prefs.edit().putString(PREF_DB_PATH, dbFile.absolutePath).apply()
                    return dbFile.absolutePath
                }
            } catch (e: Throwable) {
                Log.w(TAG, "App external dir not available: ${e.message}")
            }

            // 4. Fallback: app internal files dir
            for (dir in appFallbackDirs(context)) {
                try {
                    if (isWritable(dir)) {
                        val dbFile = File(dir, DB_NAME)
                        Log.e(TAG, "Falling back to internal storage for DB: ${dbFile.absolutePath}")
                        prefs.edit().putString(PREF_DB_PATH, dbFile.absolutePath).apply()
                        return dbFile.absolutePath
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Internal candidate dir ${dir.absolutePath} not writable: ${e.message}")
                }
            }
            val lastResort = File(context.filesDir, "Masari").apply { mkdirs() }
            val dbFile = File(lastResort, DB_NAME)
            Log.e(TAG, "Emergency fallback for DB: ${dbFile.absolutePath}")
            prefs.edit().putString(PREF_DB_PATH, dbFile.absolutePath).apply()
            return dbFile.absolutePath
        }

        private fun appFallbackDirs(context: Context): List<File> {
            val dirs = mutableListOf<File>()
            try {
                context.getExternalFilesDir(null)?.let { dirs.add(File(it, "Masari")) }
            } catch (_: Throwable) {}
            try {
                dirs.add(File(context.filesDir, "Masari"))
            } catch (_: Throwable) {}
            return dirs
        }

        private fun isWritable(dir: File?): Boolean {
            if (dir == null) return false
            return try {
                if (!dir.exists() && !dir.mkdirs()) return false
                val testFile = File(dir, ".db_perm_test_${System.currentTimeMillis()}")
                testFile.writeText("ok")
                val ok = testFile.readText() == "ok"
                testFile.delete()
                ok
            } catch (_: Throwable) {
                false
            }
        }

        fun getInstance(context: Context, fileManager: ExpenseFileManager? = null): PersistentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, fileManager).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, fileManager: ExpenseFileManager?): PersistentDatabase {
            cachedFileManager = fileManager
            val prefs = context.getSharedPreferences(PERSISTENT_PREFS_NAME, Context.MODE_PRIVATE)
            val dbPath = resolveDbPath(context, prefs)
            Log.d(TAG, "Building PersistentDatabase at: $dbPath")

            return Room.databaseBuilder(
                context.applicationContext,
                PersistentDatabase::class.java,
                dbPath
            )
                .addCallback(MigrationCallback())
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Callback that seeds default data on first creation and triggers an
         * idempotent JSON -> DB migration.
         *
         * The migration is triggered from both onCreate and onOpen, but is
         * guarded so it runs once per process; if it fails (or ran before the
         * legacy JSON files were reachable) it is simply retried on the next
         * open — no persisted flag is ever burnt, so data can never be left
         * unstuck.
         */
        private class MigrationCallback : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d(TAG, "PersistentDatabase created — seeding defaults + migrating legacy data")
                ensureMigrated()
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                ensureMigrated()
            }
        }

        /**
         * Kicks off default seeding + the idempotent JSON -> DB migration.
         * Safe to call repeatedly; runs at most once per process.
         */
        fun ensureMigrated(fileManager: ExpenseFileManager? = null) {
            if (fileManager != null) cachedFileManager = fileManager
            val database = INSTANCE ?: return

            if (migratedInProcess) return
            synchronized(this) {
                if (migratedInProcess) return@synchronized
                migratedInProcess = true
            }

            val fm = fileManager ?: cachedFileManager
            if (fm == null) {
                // No file manager available yet — release the guard so a later
                // call with a file manager can still run the migration.
                synchronized(this) { migratedInProcess = false }
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    seedDefaultsIfEmpty(database)

                    val migrator = PersistentDataMigrator(
                        templateDao = database.messageTemplateDao(),
                        expenseDao = database.manualExpenseDao(),
                        skippedDao = database.skippedTransactionDao(),
                        bankDao = database.monitoredBankDao(),
                        source = FileJsonDataSource(fm)
                    )
                    val result = migrator.migrate()
                    Log.d(TAG, "JSON -> DB migration completed: $result")
                } catch (e: Exception) {
                    Log.e(TAG, "JSON -> DB migration failed, will retry on next open: ${e.message}", e)
                    // Do not burn the migration: allow a later open to retry it.
                    synchronized(this) { migratedInProcess = false }
                }
            }
        }

        private suspend fun seedDefaultsIfEmpty(database: PersistentDatabase) {
            try {
                val templateDao = database.messageTemplateDao()
                if (templateDao.count() == 0) {
                    val defaults = MessageTemplate.defaultTemplates.map { MessageTemplateEntity.fromDomain(it) }
                    templateDao.upsertAll(defaults)
                    Log.d(TAG, "Seeded ${defaults.size} default templates")
                }

                val bankDao = database.monitoredBankDao()
                if (bankDao.count() == 0) {
                    val defaults = BankSender.defaultSenders.map { MonitoredBankEntity.fromDomain(it) }
                    bankDao.upsertAll(defaults)
                    Log.d(TAG, "Seeded ${defaults.size} default banks")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error seeding defaults: ${e.message}")
            }
        }

        fun getDatabasePath(context: Context): String {
            val prefs = context.getSharedPreferences(PERSISTENT_PREFS_NAME, Context.MODE_PRIVATE)
            return resolveDbPath(context, prefs)
        }
    }
}
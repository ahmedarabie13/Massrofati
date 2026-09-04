package com.banksms.expensetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.banksms.expensetracker.data.local.dao.BankSenderDao
import com.banksms.expensetracker.data.local.dao.TransactionDao
import com.banksms.expensetracker.data.local.entity.BankSenderEntity
import com.banksms.expensetracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        BankSenderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun bankSenderDao(): BankSenderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bank_sms_tracker.db"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-populate default popular banks
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDefaultBanks(database.bankSenderDao())
                    }
                }
            }
        }

        suspend fun populateDefaultBanks(dao: BankSenderDao) {
            val defaultBanks = listOf(
                // The 3 monitored banks
                BankSenderEntity("Alinma", "Alinma Bank (بنك الإنماء)", true),
                BankSenderEntity("alinma", "Alinma Bank (بنك الإنماء)", true),
                BankSenderEntity("ALINMA", "Alinma Bank (بنك الإنماء)", true),
                BankSenderEntity("AlinmaPay", "AlinmaPay", true),
                BankSenderEntity("ALINMAPAY", "AlinmaPay", true),
                BankSenderEntity("alinmapay", "AlinmaPay", true),
                BankSenderEntity("AlRajhiBank", "Al Rajhi Bank (مصرف الراجحي)", true),
                BankSenderEntity("ALRAJHIBANK", "Al Rajhi Bank (مصرف الراجحي)", true),
                BankSenderEntity("alrajhibank", "Al Rajhi Bank (مصرف الراجحي)", true),
                BankSenderEntity("AlRajhi", "Al Rajhi Bank (مصرف الراجحي)", true),
                BankSenderEntity("Rajhi", "Al Rajhi Bank (مصرف الراجحي)", true)
            )
            dao.insertAll(defaultBanks)
        }
    }
}

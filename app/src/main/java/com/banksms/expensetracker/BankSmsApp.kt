package com.banksms.expensetracker

import android.app.Application
import com.banksms.expensetracker.data.local.AppDatabase
import com.banksms.expensetracker.data.reader.SmsReader
import com.banksms.expensetracker.data.repository.TransactionRepository

class BankSmsApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: TransactionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        val smsReader = SmsReader(this)
        repository = TransactionRepository(
            transactionDao = database.transactionDao(),
            bankSenderDao = database.bankSenderDao(),
            smsReader = smsReader
        )
    }
}

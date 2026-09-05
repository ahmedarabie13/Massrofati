package com.banksms.expensetracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.banksms.expensetracker.BankSmsApp
import com.banksms.expensetracker.data.file.ExpenseFileManager
import com.banksms.expensetracker.data.local.AppDatabase
import com.banksms.expensetracker.data.reader.SmsReader
import com.banksms.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()

        val repository = (context.applicationContext as? BankSmsApp)?.repository ?: run {
            val db = AppDatabase.getInstance(context)
            TransactionRepository(
                transactionDao = db.transactionDao(),
                bankSenderDao = db.bankSenderDao(),
                smsReader = SmsReader(context),
                fileManager = ExpenseFileManager(context)
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Group multipart SMS fragments by sender so full message body is assembled
                val messagesBySender = messages.groupBy {
                    it.displayOriginatingAddress ?: it.originatingAddress ?: ""
                }

                for ((sender, partList) in messagesBySender) {
                    if (sender.isBlank()) continue

                    val fullBody = partList.joinToString(separator = "") {
                        it.displayMessageBody ?: it.messageBody ?: ""
                    }
                    val timestamp = partList.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

                    val inserted = repository.processIncomingSms(
                        sender = sender,
                        body = fullBody,
                        timestamp = timestamp
                    )

                    if (inserted) {
                        Log.d("SmsReceiver", "Successfully processed incoming bank SMS from $sender")
                    } else {
                        Log.d("SmsReceiver", "Ignored or duplicate incoming SMS from $sender")
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

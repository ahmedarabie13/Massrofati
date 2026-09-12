package com.banksms.expensetracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.banksms.expensetracker.BankSmsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()

        // Cloud data needs a user: signed-out receivers drop the SMS.
        // (The app auto-reopens the persisted session on process start, so
        // this only happens when genuinely signed out.)
        val app = context.applicationContext as? BankSmsApp
        val repository = app?.ensureSession()
        if (repository == null) {
            Log.d("SmsReceiver", "No signed-in user — SMS ignored")
            return
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

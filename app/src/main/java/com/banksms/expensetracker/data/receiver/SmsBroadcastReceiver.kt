package com.banksms.expensetracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.banksms.expensetracker.data.local.AppDatabase
import com.banksms.expensetracker.data.local.entity.TransactionEntity
import com.banksms.expensetracker.data.parser.BankSmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()
        val db = AppDatabase.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val monitoredSenders = db.bankSenderDao().getMonitoredSendersSync()
                    .map { it.senderId.lowercase() }
                    .toSet()

                for (sms in messages) {
                    val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: continue
                    val body = sms.displayMessageBody ?: sms.messageBody ?: continue
                    val timestamp = sms.timestampMillis

                    val senderLower = sender.lowercase()
                    // Check if sender matches any monitored sender (exact or substring)
                    val isMonitored = monitoredSenders.any { monitored ->
                        senderLower == monitored || senderLower.contains(monitored) || monitored.contains(senderLower)
                    }

                    if (isMonitored) {
                        val parsed = BankSmsParser.parse(body, sender)
                        if (parsed != null) {
                            val transaction = parsed.toTransaction(
                                messageId = timestamp, // Use timestamp as unique messageId for incoming
                                sender = sender,
                                timestamp = timestamp,
                                rawBody = body
                            )
                            db.transactionDao().insert(TransactionEntity.fromDomain(transaction))
                            Log.d("SmsReceiver", "Successfully processed incoming bank SMS: $transaction")
                        }
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

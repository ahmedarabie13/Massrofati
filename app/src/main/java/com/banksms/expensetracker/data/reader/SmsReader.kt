package com.banksms.expensetracker.data.reader

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.parser.BankSmsParser
import com.banksms.expensetracker.data.parser.RawSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiscoveredSender(
    val address: String,
    val messageCount: Int,
    val lastMessageDate: Long
)

class SmsReader(private val context: Context) {

    private val inboxUri: Uri = Telephony.Sms.Inbox.CONTENT_URI

    /**
     * Scans SMS inbox to discover all unique sender addresses (e.g. CIB, Vodafone, Uber, etc.)
     */
    suspend fun discoverSenders(): List<DiscoveredSender> = withContext(Dispatchers.IO) {
        val sendersMap = mutableMapOf<String, Pair<Int, Long>>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                inboxUri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val addressCol = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val dateCol = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val address = it.getString(addressCol)?.trim() ?: continue
                    val date = it.getLong(dateCol)

                    val existing = sendersMap[address]
                    if (existing == null) {
                        sendersMap[address] = Pair(1, date)
                    } else {
                        sendersMap[address] = Pair(existing.first + 1, maxOf(existing.second, date))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReader", "Error discovering SMS senders", e)
        }

        sendersMap.map { (address, stats) ->
            DiscoveredSender(
                address = address,
                messageCount = stats.first,
                lastMessageDate = stats.second
            )
        }.sortedByDescending { it.messageCount }
    }

    /**
     * Reads all SMS messages for monitored bank senders WITHOUT parsing.
     * Used by the AI pipeline, which batches raw texts to the on-device model.
     */
    suspend fun readRawBankMessages(
        monitoredSenders: Set<String>,
        sinceTimestamp: Long = 0L
    ): List<RawSms> = withContext(Dispatchers.IO) {
        if (monitoredSenders.isEmpty()) return@withContext emptyList()

        val messages = mutableListOf<RawSms>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val senderList = monitoredSenders.toList()
        val placeholders = senderList.joinToString(",") { "?" }
        val selection = "${Telephony.Sms.ADDRESS} IN ($placeholders) AND ${Telephony.Sms.DATE} >= ?"
        val selectionArgs = (senderList + sinceTimestamp.toString()).toTypedArray()

        try {
            val cursor: Cursor? = context.contentResolver.query(
                inboxUri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressCol = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val seenMessageIds = mutableSetOf<Long>()

                while (it.moveToNext()) {
                    val messageId = it.getLong(idCol)
                    if (!seenMessageIds.add(messageId)) continue
                    messages.add(
                        RawSms(
                            messageId = messageId,
                            sender = it.getString(addressCol) ?: "",
                            body = it.getString(bodyCol) ?: "",
                            timestamp = it.getLong(dateCol)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReader", "Error reading raw bank SMS inbox", e)
        }

        messages
    }

    /**
     * Reads all SMS messages for monitored bank senders and parses them into Transactions.
     */
    suspend fun readBankMessages(
        monitoredSenders: Set<String>,
        sinceTimestamp: Long = 0L,
        customTemplates: List<com.banksms.expensetracker.data.model.MessageTemplate> = emptyList()
    ): List<Transaction> = withContext(Dispatchers.IO) {
        if (monitoredSenders.isEmpty()) return@withContext emptyList()

        val transactions = mutableListOf<Transaction>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        // Build selection query
        val senderList = monitoredSenders.toList()
        val placeholders = senderList.joinToString(",") { "?" }
        val selection = "${Telephony.Sms.ADDRESS} IN ($placeholders) AND ${Telephony.Sms.DATE} >= ?"
        val selectionArgs = (senderList + sinceTimestamp.toString()).toTypedArray()

        try {
            val cursor: Cursor? = context.contentResolver.query(
                inboxUri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressCol = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val seenMessageIds = mutableSetOf<Long>()

                while (it.moveToNext()) {
                    val messageId = it.getLong(idCol)
                    if (!seenMessageIds.add(messageId)) continue

                    val sender = it.getString(addressCol) ?: ""
                    val body = it.getString(bodyCol) ?: ""
                    val date = it.getLong(dateCol)

                    val parsed = BankSmsParser.parse(body, sender, customTemplates)
                    if (parsed != null) {
                        transactions.add(parsed.toTransaction(messageId, sender, date, body))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReader", "Error reading bank SMS inbox", e)
        }

        transactions
    }
}

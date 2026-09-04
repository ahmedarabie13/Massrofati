package com.banksms.expensetracker.ui.screens.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.util.DateUtils
import java.io.File
import java.io.FileWriter

object CsvExporter {

    fun exportAndShare(context: Context, transactions: List<Transaction>) {
        if (transactions.isEmpty()) return

        try {
            val fileName = "bank_transactions_report_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            val writer = FileWriter(file)

            // CSV Header
            writer.append("ID,Date,Bank Sender,Type,Amount,Currency,Category,Merchant,Card/Account,Available Balance,Raw SMS\n")

            // Rows
            for (t in transactions) {
                writer.append("${t.id},")
                writer.append("\"${DateUtils.formatFullDateTime(t.timestamp)}\",")
                writer.append("\"${escapeCsv(t.sender)}\",")
                writer.append("\"${t.type.name}\",")
                writer.append("${t.amount},")
                writer.append("\"${t.currency}\",")
                writer.append("\"${escapeCsv(t.category)}\",")
                writer.append("\"${escapeCsv(t.merchant ?: "")}\",")
                writer.append("\"${escapeCsv(t.accountOrCard ?: "")}\",")
                writer.append("${t.availableBalance ?: ""},")
                writer.append("\"${escapeCsv(t.rawBody)}\"\n")
            }

            writer.flush()
            writer.close()

            // Share file intent
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Bank SMS Transactions Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Transactions CSV Report")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }
}

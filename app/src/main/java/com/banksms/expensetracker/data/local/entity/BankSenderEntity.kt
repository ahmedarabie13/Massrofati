package com.banksms.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.banksms.expensetracker.data.model.BankSender

@Entity(tableName = "bank_senders")
data class BankSenderEntity(
    @PrimaryKey
    val senderId: String,
    val displayName: String,
    val isMonitored: Boolean = true,
    val customRegex: String? = null
) {
    fun toDomain(txCount: Int = 0, lastTx: Long? = null): BankSender = BankSender(
        senderId = senderId,
        displayName = displayName,
        isMonitored = isMonitored,
        customRegex = customRegex,
        totalTransactionsCount = txCount,
        lastTransactionTime = lastTx
    )

    companion object {
        fun fromDomain(sender: BankSender): BankSenderEntity = BankSenderEntity(
            senderId = sender.senderId,
            displayName = sender.displayName,
            isMonitored = sender.isMonitored,
            customRegex = sender.customRegex
        )
    }
}

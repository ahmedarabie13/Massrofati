package com.banksms.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.banksms.expensetracker.data.model.BankSender

@Entity(tableName = "monitored_banks")
data class MonitoredBankEntity(
    @PrimaryKey
    val senderId: String,
    val displayName: String,
    val isMonitored: Boolean = true,
    val customRegex: String? = null
) {
    fun toDomain(): BankSender = BankSender(
        senderId = senderId,
        displayName = displayName,
        isMonitored = isMonitored,
        customRegex = customRegex
    )

    companion object {
        fun fromDomain(bank: BankSender): MonitoredBankEntity = MonitoredBankEntity(
            senderId = bank.senderId,
            displayName = bank.displayName,
            isMonitored = bank.isMonitored,
            customRegex = bank.customRegex
        )
    }
}

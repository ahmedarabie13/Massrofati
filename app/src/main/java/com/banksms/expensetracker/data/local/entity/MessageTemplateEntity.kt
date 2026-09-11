package com.banksms.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.model.TransactionType

@Entity(tableName = "message_templates")
data class MessageTemplateEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val sender: String = "",
    val pattern: String,
    val defaultType: String = "EXPENSE",
    val defaultCurrency: String = "SAR",
    val defaultCategory: String = "General",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): MessageTemplate = MessageTemplate(
        id = id,
        name = name,
        sender = sender,
        pattern = pattern,
        defaultType = try { TransactionType.valueOf(defaultType) } catch (_: Exception) { TransactionType.EXPENSE },
        defaultCurrency = defaultCurrency,
        defaultCategory = defaultCategory,
        isEnabled = isEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(template: MessageTemplate): MessageTemplateEntity = MessageTemplateEntity(
            id = template.id,
            name = template.name,
            sender = template.sender,
            pattern = template.pattern,
            defaultType = template.defaultType.name,
            defaultCurrency = template.defaultCurrency,
            defaultCategory = template.defaultCategory,
            isEnabled = template.isEnabled,
            createdAt = template.createdAt,
            updatedAt = template.updatedAt
        )
    }
}

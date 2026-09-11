package com.banksms.expensetracker

import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.file.SkippedTransaction
import com.banksms.expensetracker.data.local.entity.ManualExpenseEntity
import com.banksms.expensetracker.data.local.entity.MessageTemplateEntity
import com.banksms.expensetracker.data.local.entity.MonitoredBankEntity
import com.banksms.expensetracker.data.local.entity.SkippedTransactionEntity
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class PersistentEntitiesTest {

    @Test
    fun testManualExpenseEntityMapping() {
        val original = ManualExpense(
            id = "test-manual-1",
            amount = 150.75,
            currency = "SAR",
            type = TransactionType.EXPENSE,
            category = "Dining",
            merchant = "Shawarma House",
            paymentMethod = "Credit Card",
            timestamp = 1726000000000L,
            note = "Team lunch"
        )

        val entity = ManualExpenseEntity.fromDomain(original)
        assertEquals("test-manual-1", entity.id)
        assertEquals(150.75, entity.amount, 0.001)
        assertEquals("SAR", entity.currency)
        assertEquals("EXPENSE", entity.type)
        assertEquals("Dining", entity.category)
        assertEquals("Shawarma House", entity.merchant)
        assertEquals("Credit Card", entity.paymentMethod)
        assertEquals("Team lunch", entity.note)

        val mappedBack = entity.toDomain()
        assertEquals(original.id, mappedBack.id)
        assertEquals(original.amount, mappedBack.amount, 0.001)
        assertEquals(original.type, mappedBack.type)
        assertEquals(original.category, mappedBack.category)
        assertEquals(original.merchant, mappedBack.merchant)
        assertEquals(original.paymentMethod, mappedBack.paymentMethod)
        assertEquals(original.note, mappedBack.note)
    }

    @Test
    fun testSkippedTransactionEntityMapping() {
        val original = SkippedTransaction(
            originalMessageId = 8888L,
            sender = "AlRajhiBank",
            amount = 99.0,
            currency = "SAR",
            type = TransactionType.EXPENSE,
            merchant = "Amazon",
            category = "Shopping",
            rawBody = "شراء عبر الإنترنت...",
            timestamp = 1726000000000L,
            skippedAt = 1726000050000L,
            reason = "Duplicate notification"
        )

        val entity = SkippedTransactionEntity.fromDomain(original)
        assertEquals(8888L, entity.originalMessageId)
        assertEquals("AlRajhiBank", entity.sender)
        assertEquals(99.0, entity.amount, 0.001)
        assertEquals("Amazon", entity.merchant)
        assertEquals("Duplicate notification", entity.reason)

        val mappedBack = entity.toDomain()
        assertEquals(original.originalMessageId, mappedBack.originalMessageId)
        assertEquals(original.sender, mappedBack.sender)
        assertEquals(original.amount, mappedBack.amount, 0.001)
        assertEquals(original.merchant, mappedBack.merchant)
        assertEquals(original.rawBody, mappedBack.rawBody)
        assertEquals(original.reason, mappedBack.reason)
    }

    @Test
    fun testMessageTemplateEntityMapping() {
        for (defaultTpl in MessageTemplate.defaultTemplates) {
            val entity = MessageTemplateEntity.fromDomain(defaultTpl)
            assertEquals(defaultTpl.id, entity.id)
            assertEquals(defaultTpl.name, entity.name)
            assertEquals(defaultTpl.pattern, entity.pattern)
            assertEquals(defaultTpl.sender, entity.sender)
            assertEquals(defaultTpl.defaultType.name, entity.defaultType)
            assertEquals(defaultTpl.isEnabled, entity.isEnabled)

            val mappedBack = entity.toDomain()
            assertEquals(defaultTpl.id, mappedBack.id)
            assertEquals(defaultTpl.name, mappedBack.name)
            assertEquals(defaultTpl.pattern, mappedBack.pattern)
            assertEquals(defaultTpl.defaultType, mappedBack.defaultType)
            assertEquals(defaultTpl.isEnabled, mappedBack.isEnabled)
        }
    }

    @Test
    fun testMonitoredBankEntityMapping() {
        for (defaultBank in BankSender.defaultSenders) {
            val entity = MonitoredBankEntity.fromDomain(defaultBank)
            assertEquals(defaultBank.senderId, entity.senderId)
            assertEquals(defaultBank.displayName, entity.displayName)
            assertEquals(defaultBank.isMonitored, entity.isMonitored)

            val mappedBack = entity.toDomain()
            assertEquals(defaultBank.senderId, mappedBack.senderId)
            assertEquals(defaultBank.displayName, mappedBack.displayName)
            assertEquals(defaultBank.isMonitored, mappedBack.isMonitored)
        }
    }

    @Test
    fun testDefaultTemplatesContainRefundAndPos() {
        val templates = MessageTemplate.defaultTemplates
        assertTrue(templates.any { it.id == "default_tpl_alinma_pos" })
        assertTrue(templates.any { it.id == "default_tpl_alrajhi_online" })
        assertTrue(templates.any { it.id == "default_tpl_snb_purchase" })
        assertTrue(templates.any { it.id == "default_tpl_incoming_transfer" })
        assertTrue(templates.any { it.id == "default_tpl_alinma_refund" })

        val refundTpl = templates.first { it.id == "default_tpl_alinma_refund" }
        assertEquals(TransactionType.INCOME, refundTpl.defaultType)
        assertTrue(refundTpl.pattern.contains("استرجاع"))
    }
}

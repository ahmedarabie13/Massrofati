package com.banksms.expensetracker

import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.data.parser.BankSmsParser
import com.banksms.expensetracker.data.parser.TemplateMatcher
import org.junit.Assert.*
import org.junit.Test

class TemplateMatcherTest {

    @Test
    fun testArabicTemplateExtraction() {
        val template = MessageTemplate(
            name = "Alinma POS Template",
            sender = "alinma",
            pattern = "شراء عبر: POS\nالبطاقة الائتمانية: {card}\nمبلغ: {currency} {amount}\nلدى: {merchant}\n*الرصيد: {balance} ريال",
            defaultType = TransactionType.EXPENSE,
            defaultCurrency = "SAR"
        )

        val sms = """
            شراء عبر: POS
            البطاقة الائتمانية: **7639
            مبلغ: SAR 450.50
            لدى: STARBUCKS RIYADH
            في: 10:59 2026-09-01
            الرصيد: 26,236.61 ريال
        """.trimIndent()

        val parsed = TemplateMatcher.match(sms, "Alinma", template)
        assertNotNull(parsed)
        assertEquals(450.50, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("STARBUCKS RIYADH", parsed.merchant)
        assertEquals("**7639", parsed.accountOrCard)
        assertEquals(26236.61, parsed.availableBalance!!, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun testEnglishTemplateWithCustomBank() {
        val template = MessageTemplate(
            name = "SNB Debit Purchase",
            sender = "SNB",
            pattern = "Purchase with card {card} of {currency} {amount} at {merchant}. Available Balance: {balance}",
            defaultType = TransactionType.EXPENSE,
            defaultCurrency = "SAR"
        )

        val sms = "Purchase with card **1234 of SAR 85.00 at Riyadh Cafe. Available Balance: 15,400.20"

        val parsed = TemplateMatcher.match(sms, "SNB", template)
        assertNotNull(parsed)
        assertEquals(85.00, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("Riyadh Cafe", parsed.merchant)
        assertEquals("**1234", parsed.accountOrCard)
        assertEquals(15400.20, parsed.availableBalance!!, 0.001)
    }

    @Test
    fun testEasternArabicDigitsNormalization() {
        val template = MessageTemplate(
            name = "Generic Arabic Template",
            sender = "*",
            pattern = "خصم {amount} {currency} لدى {merchant}",
            defaultType = TransactionType.EXPENSE,
            defaultCurrency = "SAR"
        )

        // Using Eastern Arabic numerals: ٤٥٠ = 450
        val sms = "خصم ٤٥٠ ريال لدى جرير"

        val parsed = TemplateMatcher.match(sms, "AnyBank", template)
        assertNotNull(parsed)
        assertEquals(450.0, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("جرير", parsed.merchant)
    }

    @Test
    fun testWildcardMatching() {
        val template = MessageTemplate(
            name = "Wildcard Template",
            sender = "*",
            pattern = "Purchase * amount: {amount} {currency} * merchant: {merchant}",
            defaultType = TransactionType.EXPENSE,
            defaultCurrency = "SAR"
        )

        val sms = "Purchase successful with Apple Pay. amount: 120.00 SAR for ref #987263 merchant: Noon KSA"

        val parsed = TemplateMatcher.match(sms, "RandomBank", template)
        assertNotNull(parsed)
        assertEquals(120.0, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("Noon KSA", parsed.merchant)
    }

    @Test
    fun testInteractiveTesterReportsMatchAndFailures() {
        val template = MessageTemplate(
            name = "Test Tpl",
            pattern = "Amount: {amount} SAR from {merchant}"
        )

        val matchingSms = "Amount: 350.00 SAR from Careem Ride"
        val nonMatchingSms = "Account balance is 500 SAR"

        val matchResult = TemplateMatcher.test(template, matchingSms)
        assertTrue(matchResult.isMatch)
        assertEquals(350.0, matchResult.parsedTransaction!!.amount, 0.001)
        assertEquals("Careem Ride", matchResult.parsedTransaction!!.merchant)

        val failResult = TemplateMatcher.test(template, nonMatchingSms)
        assertFalse(failResult.isMatch)
        assertNotNull(failResult.errorMessage)
    }

    @Test
    fun testBankSmsParserIntegrationWithCustomTemplates() {
        val customTemplate = MessageTemplate(
            name = "Special Custom Bank",
            sender = "CustomBank",
            pattern = "Dear Customer, you spent {currency} {amount} at {merchant}",
            defaultType = TransactionType.EXPENSE,
            defaultCurrency = "SAR"
        )

        val sms = "Dear Customer, you spent SAR 99.90 at Domino's Pizza"

        // Without custom templates, BankSmsParser returns null for non-default banks
        val withoutCustom = BankSmsParser.parse(sms, "CustomBank", emptyList())
        assertNull(withoutCustom)

        // With custom template passed, it successfully extracts the transaction!
        val withCustom = BankSmsParser.parse(sms, "CustomBank", listOf(customTemplate))
        assertNotNull(withCustom)
        assertEquals(99.90, withCustom!!.amount, 0.001)
        assertEquals("Domino's Pizza", withCustom.merchant)
    }

    @Test
    fun testDisabledTemplateIsIgnored() {
        val disabledTemplate = MessageTemplate(
            name = "Disabled Tpl",
            sender = "AnyBank",
            pattern = "Amount: {amount} SAR",
            isEnabled = false
        )

        val sms = "Amount: 100 SAR"
        val parsed = BankSmsParser.parse(sms, "AnyBank", listOf(disabledTemplate))
        assertNull(parsed)
    }
}

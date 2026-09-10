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

    @Test
    fun testIncomingTransferTemplate() {
        val template = MessageTemplate(
            id = "default_tpl_incoming_transfer",
            name = "Incoming Transfer (حوالة واردة)",
            sender = "*",
            pattern = "حوالة واردة {amount} {currency}\nمن {merchant}; {account}\nفي {time} {date}",
            defaultType = TransactionType.INCOME,
            defaultCurrency = "SAR",
            defaultCategory = "Income / Deposits"
        )

        val sms = """
            حوالة واردة 5 ريال
            من AHMED GAMA*; *1001
            في 04:23 26-09-05
        """.trimIndent()

        val testResult = TemplateMatcher.test(template, sms)
        assertTrue("Test result should match: ${testResult.errorMessage}", testResult.isMatch)
        val parsed = testResult.parsedTransaction
        assertNotNull(parsed)
        assertEquals(5.0, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("AHMED GAMA*", parsed.merchant)
        assertEquals("**1001", parsed.accountOrCard)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("Income / Deposits", parsed.category)
    }

    @Test
    fun testLocalInternetPurchaseTemplate() {
        val sms = """
            Local Internet purchase
            Amount 275.32 SAR
            Account *2509
            At Noon
            LAK *1679
            on 19/08/26 at 22:42
        """.trimIndent()

        // 1. The user's original template with duplicate {amount} on line 5:
        val buggyTemplate = MessageTemplate(
            name = "Local Internet purchase (buggy)",
            pattern = """
                Local Internet purchase
                Amount {amount} {currency}
                Account {card}
                At {merchant}
                LAK *1679{amount}
                on {date} at {time}
            """.trimIndent()
        )
        val buggyResult = TemplateMatcher.test(buggyTemplate, sms)
        assertFalse(buggyResult.isMatch)

        // 2. The corrected template without duplicate {amount} on line 5:
        val fixedTemplate = MessageTemplate(
            name = "Local Internet purchase (fixed)",
            pattern = """
                Local Internet purchase
                Amount {amount} {currency}
                Account {card}
                At {merchant}
                LAK *1679
                on {date} at {time}
            """.trimIndent()
        )
        val fixedResult = TemplateMatcher.test(fixedTemplate, sms)
        assertTrue("Fixed template should match: ${fixedResult.errorMessage}", fixedResult.isMatch)
        val parsed = fixedResult.parsedTransaction
        assertNotNull(parsed)
        assertEquals(275.32, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("Noon", parsed.merchant)
        assertEquals("**2509", parsed.accountOrCard)

        // 3. Flexible variation with wildcard for LAK line (matches any LAK account/card):
        val flexibleTemplate = MessageTemplate(
            name = "Local Internet purchase (flexible)",
            pattern = """
                Local Internet purchase
                Amount {amount} {currency}
                Account {card}
                At {merchant}
                LAK *
                on {date} at {time}
            """.trimIndent()
        )
        val flexibleResult = TemplateMatcher.test(flexibleTemplate, sms)
        assertTrue("Flexible template should match: ${flexibleResult.errorMessage}", flexibleResult.isMatch)
    }

    @Test
    fun testAlinmaRefundTemplateMatching() {
        val template = MessageTemplate(
            id = "default_tpl_alinma_refund",
            name = "Alinma Card Purchase Refund",
            sender = "alinma",
            pattern = "استرجاع عملية شراء\nلبطاقة ائتمانية: {card}\nمبلغ: {amount} {currency}\nرقم حساب: {account}\nفي: {merchant}\n*",
            defaultType = TransactionType.INCOME,
            defaultCurrency = "SAR",
            defaultCategory = "Income / Deposits"
        )

        val sms = """
            استرجاع عملية شراء
            لبطاقة ائتمانية: *7639
            مبلغ: 38.76 SAR
            رقم حساب: **0000
            في: TEMU.COM
            في: أيرلندا
            في: 2026-09-10 17:19:00
        """.trimIndent()

        val testResult = TemplateMatcher.test(template, sms)
        assertTrue("Refund template should match: ${testResult.errorMessage}", testResult.isMatch)
        val parsed = testResult.parsedTransaction
        assertNotNull(parsed)
        assertEquals(38.76, parsed!!.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("TEMU.COM", parsed.merchant)
        assertEquals("**7639", parsed.accountOrCard)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("Income / Deposits", parsed.category)
    }

    @Test
    fun testTemplateWithTypePlaceholderRecognizesRefundAsIncome() {
        val template = MessageTemplate(
            name = "Card Purchase / Refund",
            sender = "alinma",
            pattern = "{type} عملية شراء\nلبطاقة ائتمانية: {card}\nمبلغ: {amount} {currency}\nرقم حساب: {account}\nفي: {merchant}\n*",
            defaultType = TransactionType.EXPENSE, // Default is EXPENSE!
            defaultCurrency = "SAR"
        )

        val sms = """
            استرجاع عملية شراء
            لبطاقة ائتمانية: *7639
            مبلغ: 38.76 SAR
            رقم حساب: **0000
            في: TEMU.COM
            في: أيرلندا
            في: 2026-09-10 17:19:00
        """.trimIndent()

        val parsed = TemplateMatcher.match(sms, "Alinma", template)
        assertNotNull(parsed)
        // Despite defaultType = EXPENSE, captured {type} is "استرجاع", so it MUST be INCOME!
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(38.76, parsed.amount, 0.001)
        assertEquals("TEMU.COM", parsed.merchant)
        assertEquals("**7639", parsed.accountOrCard)
    }

    @Test
    fun testTemplateWithoutTypePlaceholderPrioritizesRefundKeywordsOverExpenseDefault() {
        val template = MessageTemplate(
            name = "Alinma Refund Without Type Group",
            sender = "*",
            pattern = "استرجاع عملية شراء\nلبطاقة ائتمانية: {card}\nمبلغ: {amount} {currency}\n*",
            defaultType = TransactionType.EXPENSE // Default is EXPENSE!
        )

        val sms = """
            استرجاع عملية شراء
            لبطاقة ائتمانية: *7639
            مبلغ: 38.76 SAR
            في: TEMU.COM
        """.trimIndent()

        val parsed = TemplateMatcher.match(sms, "AnyBank", template)
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
    }

    @Test
    fun testEnglishRefundKeywordsClassifiedAsIncome() {
        val template = MessageTemplate(
            name = "English Refund",
            sender = "*",
            pattern = "{type} for purchase of {amount} {currency} at {merchant}",
            defaultType = TransactionType.EXPENSE
        )

        val sms = "Refund for purchase of 150.00 SAR at Amazon"
        val parsed = TemplateMatcher.match(sms, "AnyBank", template)
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(150.00, parsed.amount, 0.001)
        assertEquals("Amazon", parsed.merchant)
    }
}

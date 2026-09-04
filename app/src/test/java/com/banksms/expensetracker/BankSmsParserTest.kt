package com.banksms.expensetracker

import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.data.parser.BankSmsParser
import org.junit.Assert.*
import org.junit.Test

class BankSmsParserTest {

    // ═══════════════════════════════════════════════════════════════
    //  ALINMA  (بنك الإنماء)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun alinma_posPurchase() {
        val sms = """
            شراء عبر: POS
            البطاقة الائتمانية: **7639
            مبلغ: SAR 4
            لدى: 170672 riyadh metro
            في: 10:59 2026-09-01
            الرصيد: 26,236.61 ريال
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "Alinma")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(4.0, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("**7639", parsed.accountOrCard)
        assertEquals("170672 riyadh metro", parsed.merchant)
        assertEquals(26236.61, parsed.availableBalance!!, 0.001)
    }

    @Test
    fun alinma_posWithInlineAmount() {
        val sms = """
            شراء نقاط بيع 26.05 SAR - Samsung PAY
            بطاقة ائتمانية **7639
            حساب **0000
            من ananinja.com
            في 2026-09-01 00:11
            رصيد 26,240.61 SAR
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "Alinma")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(26.05, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("**7639", parsed.accountOrCard)
        assertEquals("ananinja.com", parsed.merchant)
        assertEquals(26240.61, parsed.availableBalance!!, 0.001)
    }

    @Test
    fun alinma_onlinePurchase() {
        val sms = """
            شراء عبر الإنترنت
            بطاقة ائتمانية **7639
            مبلغ SAR 1,064
            من Riyadh Air
            حساب **0000
            SA
            في 2026-08-25 22:54
            رصيد 24,186.05 SAR
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "Alinma")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(1064.0, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("**7639", parsed.accountOrCard)
        assertEquals("Riyadh Air", parsed.merchant)
        assertEquals(24186.05, parsed.availableBalance!!, 0.001)
        assertEquals("Travel & Flights", parsed.category)
    }

    @Test
    fun alinma_internationalOnlinePurchaseWithFees() {
        val sms = """
            شراء دولي إنترنت SAR 156
            بطاقة ائتمانية **7639
            حساب **0000
            من GB- MSARWEB
            في 13:00:14 2026-08-23
            رسوم SAR 3.59
            سعر صرف 1.00
            المبلغ المستحق SAR 159.59
            رصيد SAR 25979.55
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "Alinma")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        // Should use المبلغ المستحق (total due) instead of base amount
        assertEquals(159.59, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("**7639", parsed.accountOrCard)
        assertEquals("GB- MSARWEB", parsed.merchant)
        assertEquals(25979.55, parsed.availableBalance!!, 0.001)
    }

    // ═══════════════════════════════════════════════════════════════
    //  ALRAJHI  (مصرف الراجحي)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun alrajhi_internetPurchase() {
        val sms = """
            شراء إنترنت بـSR 412.1
            عبر7871;فيزا
            لـnoon.com
            رصيد:48152.81 SR
            30/8/26 23:54
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlRajhiBank")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(412.1, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("**7871", parsed.accountOrCard)
        assertEquals("noon.com", parsed.merchant)
        assertEquals(48152.81, parsed.availableBalance!!, 0.001)
    }

    @Test
    fun alrajhi_posPurchase() {
        val sms = """
            شراء عبر نقاط البيع
            بطاقة:7871 ;فيزا
            لدى:Noon
            مبلغ:74.4 SAR
            رصيد:48564.91 SAR
            30/8/26 22:58
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlRajhiBank")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(74.4, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("**7871", parsed.accountOrCard)
        assertEquals("Noon", parsed.merchant)
        assertEquals(48564.91, parsed.availableBalance!!, 0.001)
    }

    @Test
    fun alrajhi_outgoingTransfer() {
        val sms = """
            حوالة داخلية صادرة بـSR 63
            من4941
            لـ6657;ذياب الصيري
            26/8/29 00:12
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlRajhiBank")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(63.0, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("Transfers", parsed.category)
        assertEquals("ذياب الصيري", parsed.merchant)
    }

    @Test
    fun alrajhi_incomingTransfer() {
        val sms = """
            حوالة محلية واردة بـSR 21737
            لـ4941
            من5702;شركة دار البلد لحلول الأعمال
            26/8/27 12:09
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlRajhiBank")
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(21737.0, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("Income / Deposits", parsed.category)
        assertEquals("شركة دار البلد لحلول الأعمال", parsed.merchant)
    }

    @Test
    fun alrajhi_samsungPayPurchase() {
        val sms = """
            شراء إنترنت بـSR 92
            عبر7871;فيزا-سامسونج باي
            لـananinja.
            رصيد:49071.66 SR
            28/8/26 2:47
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlRajhiBank")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(92.0, parsed.amount, 0.001)
        assertEquals("**7871", parsed.accountOrCard)
    }

    @Test
    fun alrajhi_posSamsungPay() {
        val sms = """
            شراء عبر نقاط البيع
            بطاقة:7871 ;فيزا-سامسونج باي
            لدى:DAWAHI AL
            مبلغ:7 SAR
            رصيد:49281.8 SAR
            25/8/26 19:31
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlRajhiBank")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(7.0, parsed.amount, 0.001)
        assertEquals("**7871", parsed.accountOrCard)
        assertEquals("DAWAHI AL", parsed.merchant)
        assertEquals(49281.8, parsed.availableBalance!!, 0.001)
    }

    @Test
    fun alrajhi_foodDeliveryPurchase() {
        val sms = """
            شراء إنترنت بـSR 33.53
            عبر7871;فيزا
            لـHUNGERSTA
            رصيد:49281.8 SR
            21/8/26 12:38
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlRajhiBank")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(33.53, parsed.amount, 0.001)
        assertEquals("Food & Dining", parsed.category)
    }

    // ═══════════════════════════════════════════════════════════════
    //  ALINMA PAY
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun alinmapay_debitLocalTransfer() {
        val sms = """
            Debit via local transfer
            From: AHMED ARABIE
            Amount:SAR 9.50
            To:AMER ABDELNABY
            Account:**9695
            Fees:0
            Ref:FT26243QXW7T
            On :2026-08-31 12:57
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlinmaPay")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(9.50, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("AMER ABDELNABY", parsed.merchant)
        assertEquals("**9695", parsed.accountOrCard)
        assertEquals("Transfers", parsed.category)
    }

    @Test
    fun alinmapay_creditLocalTransfer() {
        val sms = """
            Credit via local transfer
            Via:AlinmaPay
            Amount:SAR 1.00
            From:FAWAZ MASHI
            Account:**1673
            On:2026-08-31 10:08
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlinmaPay")
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(1.0, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("FAWAZ MASHI", parsed.merchant)
        assertEquals("**1673", parsed.accountOrCard)
        assertEquals("Income / Deposits", parsed.category)
    }

    @Test
    fun alinmapay_outgoingFundsTransfer() {
        val sms = """
            Outgoing Funds Transfer Approved
            Debited from wallet: **4303
            To: Ahmed Arabie
            Amount: 1,000.60 SAR
            Fee: 0.58 SAR
            IBAN: **6105
            At 2026-08-28 18:35
            Ref: EPY2624048VDTQ6X
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlinmaPay")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(1000.60, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("Ahmed Arabie", parsed.merchant)
        assertEquals("**4303", parsed.accountOrCard)
        assertEquals("Transfers", parsed.category)
    }

    @Test
    fun alinmapay_purchaseCard() {
        val sms = """
            Purchase Card
            Card: **9860 MADA
            Amount: 129.11 SAR
            At: shawerma house restura
            On: 2026-08-23 13:08
            Remaining Balance: 2,786.59 SAR
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlinmaPay")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(129.11, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("**9860", parsed.accountOrCard)
        assertEquals("shawerma house restura", parsed.merchant)
        assertEquals(2786.59, parsed.availableBalance!!, 0.001)
        assertEquals("Food & Dining", parsed.category)
    }

    @Test
    fun alinmapay_internationalTransfer() {
        val sms = """
            Debit  International Transfer
            Via Alinma Direct
            From: AHMED GAMAL ARABIE
            Sender Account: **4303
            Amount: 14,705 SAR
            Fees: 5.75 SAR
            To: Ahmed Gamal Mahmoud Arabie
            Receiver Account: **4648
            Country: Egypt
            Ref: 3012126217PX2YS
            On: 2026-08-05 06:40
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlinmaPay")
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(14705.0, parsed.amount, 0.001)
        assertEquals("SAR", parsed.currency)
        assertEquals("Ahmed Gamal Mahmoud Arabie", parsed.merchant)
        assertEquals("**4303", parsed.accountOrCard)
        assertEquals("Transfers", parsed.category)
    }

    // ═══════════════════════════════════════════════════════════════
    //  AUTO-DETECTION (no sender provided)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun autoDetect_alinmaFromContent() {
        val sms = """
            شراء عبر: POS
            البطاقة الائتمانية: **7639
            مبلغ: SAR 100
            لدى: Test Merchant
            في: 10:59 2026-09-01
            الرصيد: 10,000.00 ريال
        """.trimIndent()

        // No sender provided — should auto-detect as Alinma
        val parsed = BankSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(100.0, parsed.amount, 0.001)
    }

    @Test
    fun autoDetect_alrajhiFromContent() {
        val sms = """
            شراء إنترنت بـSR 50
            عبر1234;فيزا
            لـtest.com
            رصيد:9999.99 SR
            01/9/26 10:00
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(50.0, parsed.amount, 0.001)
    }

    @Test
    fun autoDetect_alinmaPayFromContent() {
        val sms = """
            Purchase Card
            Card: **5555 MADA
            Amount: 200.00 SAR
            At: Test Store
            On: 2026-01-01 12:00
            Remaining Balance: 5,000.00 SAR
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(200.0, parsed.amount, 0.001)
    }

    @Test
    fun unknownBank_returnsNull() {
        val sms = "Your OTP is 123456. Do not share."
        val parsed = BankSmsParser.parse(sms)
        assertNull(parsed)
    }

    // ═══════════════════════════════════════════════════════════════
    //  CREDIT CARD PAYMENT EXCLUSION
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun alrajhi_creditCardPayment_shouldBeExcluded() {
        val sms = """
            بطاقة فيزا:سداد بـSR 1431.09
            عبر7871;فيزا
            رصيد:49583.9 SR
            1/9/26 22:06
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "AlRajhiBank")
        assertNull("Credit card payment (سداد) should be excluded", parsed)
    }

    @Test
    fun alinma_creditCardSettlement_shouldBeExcluded() {
        val sms = """
            سداد بطاقة ائتمانية
            بطاقة ائتمانية **7639
            مبلغ SAR 5000
            رصيد 20,000 SAR
        """.trimIndent()

        val parsed = BankSmsParser.parse(sms, "Alinma")
        assertNull("Credit card settlement (سداد) should be excluded", parsed)
    }
}

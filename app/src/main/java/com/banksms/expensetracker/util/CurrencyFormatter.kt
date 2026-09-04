package com.banksms.expensetracker.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {

    private val formatter = DecimalFormat("#,##0.00")

    fun format(amount: Double, currency: String = "EGP"): String {
        val formattedNumber = formatter.format(amount)
        return when (currency.uppercase()) {
            "USD" -> "$$formattedNumber"
            "EUR" -> "€$formattedNumber"
            "GBP" -> "£$formattedNumber"
            "INR" -> "₹$formattedNumber"
            "AED" -> "$formattedNumber AED"
            "SAR" -> "$formattedNumber SAR"
            "EGP" -> "$formattedNumber EGP"
            else -> "$formattedNumber $currency"
        }
    }

    fun formatSigned(amount: Double, isExpense: Boolean, currency: String = "EGP"): String {
        val sign = if (isExpense) "- " else "+ "
        return sign + format(amount, currency)
    }
}

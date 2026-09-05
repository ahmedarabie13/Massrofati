package com.banksms.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

@Composable
fun BankBadge(
    sender: String,
    modifier: Modifier = Modifier
) {
    val bankColor = getBankColor(sender)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bankColor.copy(alpha = 0.14f))
            .border(
                width = 0.8.dp,
                color = bankColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatBankDisplayName(sender),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                letterSpacing = 0.3.sp,
                color = bankColor
            )
        )
    }
}

fun formatBankDisplayName(sender: String): String {
    return when (sender.lowercase()) {
        "alinma" -> "Alinma"
        "alrajhibank" -> "Al Rajhi"
        "alinmapay" -> "AlinmaPay"
        "manual" -> "Manual"
        else -> sender.uppercase()
    }
}

private val BankPalette = listOf(
    Color(0xFF0284C7), // Sky Blue (Al Rajhi style)
    Color(0xFF0D9488), // Teal (Alinma style)
    Color(0xFF8B5CF6), // Violet (AlinmaPay style)
    Color(0xFFF59E0B), // Amber Gold
    Color(0xFF10B981), // Emerald
    Color(0xFFEC4899), // Pink
    Color(0xFF6366F1), // Indigo
    Color(0xFF64748B)  // Slate
)

fun getBankColor(sender: String): Color {
    return when (sender.lowercase()) {
        "alrajhibank" -> Color(0xFF0284C7)
        "alinma" -> Color(0xFF0D9488)
        "alinmapay" -> Color(0xFF8B5CF6)
        "manual" -> Color(0xFF10B981)
        else -> {
            val index = sender.hashCode().absoluteValue % BankPalette.size
            BankPalette[index]
        }
    }
}

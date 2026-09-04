package com.banksms.expensetracker.ui.components

import androidx.compose.foundation.background
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
            .clip(RoundedCornerShape(6.dp))
            .background(bankColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = sender.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = bankColor
            )
        )
    }
}

private val BankPalette = listOf(
    Color(0xFF1E88E5), // Blue
    Color(0xFF00897B), // Teal
    Color(0xFF5E35B1), // Deep Purple
    Color(0xFFE65100), // Orange
    Color(0xFF00838F), // Cyan
    Color(0xFF2E7D32), // Green
    Color(0xFFC2185B), // Pink
    Color(0xFF455A64)  // Slate
)

fun getBankColor(sender: String): Color {
    val index = sender.hashCode().absoluteValue % BankPalette.size
    return BankPalette[index]
}

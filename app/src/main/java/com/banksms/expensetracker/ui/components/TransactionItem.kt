package com.banksms.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.ui.theme.CategoryColors
import com.banksms.expensetracker.ui.theme.ExpenseCoral
import com.banksms.expensetracker.ui.theme.IncomeEmerald
import com.banksms.expensetracker.ui.theme.MasariEmerald
import com.banksms.expensetracker.util.CurrencyFormatter
import com.banksms.expensetracker.util.DateUtils

@Composable
fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) ExpenseCoral else IncomeEmerald
    val categoryColor = CategoryColors[transaction.category] ?: Color(0xFF64748B)
    val categoryIcon = getCategoryIcon(transaction.category, transaction.type)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = transaction.category,
                    tint = categoryColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = transaction.merchant?.takeIf { it.isNotBlank() }
                            ?: if (isExpense) "Expense" else "Credit",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (transaction.isManual) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MasariEmerald.copy(alpha = 0.15f),
                            border = CardDefaults.outlinedCardBorder(enabled = true)
                        ) {
                            Text(
                                text = "Manual",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MasariEmerald,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BankBadge(sender = transaction.sender)

                    transaction.accountOrCard?.let { card ->
                        Text(
                            text = card,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = DateUtils.formatShortDate(transaction.timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatSigned(transaction.amount, isExpense, transaction.currency),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = amountColor
                )

                transaction.availableBalance?.let { bal ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Bal: ${CurrencyFormatter.format(bal, transaction.currency)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun getCategoryIcon(category: String, type: TransactionType): ImageVector {
    return when {
        category.contains("Food", ignoreCase = true) -> Icons.Default.Restaurant
        category.contains("Shopping", ignoreCase = true) -> Icons.Default.ShoppingCart
        category.contains("Transport", ignoreCase = true) -> Icons.Default.DirectionsCar
        category.contains("Bill", ignoreCase = true) -> Icons.Default.Receipt
        category.contains("Entertainment", ignoreCase = true) -> Icons.Default.Theaters
        category.contains("ATM", ignoreCase = true) -> Icons.Default.LocalAtm
        category.contains("Salary", ignoreCase = true) -> Icons.Default.AccountBalanceWallet
        category.contains("Medical", ignoreCase = true) -> Icons.Default.LocalHospital
        category.contains("Transfer", ignoreCase = true) -> Icons.Default.SwapHoriz
        type == TransactionType.INCOME -> Icons.Default.ArrowUpward
        else -> Icons.Default.Payments
    }
}

package com.banksms.expensetracker.ui.components

import androidx.compose.foundation.background
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
import com.banksms.expensetracker.ui.theme.DribbbleAmountRed
import com.banksms.expensetracker.ui.theme.DribbbleGreen
import com.banksms.expensetracker.ui.theme.DribbblePurple
import com.banksms.expensetracker.util.CurrencyFormatter
import com.banksms.expensetracker.util.DateUtils

@Composable
fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) DribbbleAmountRed else DribbbleGreen
    val categoryColor = CategoryColors[transaction.category] ?: Color(0xFF64748B)
    val categoryIcon = getCategoryIcon(transaction.category, transaction.type)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon in soft tinted squircle
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
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
                            ?: if (isExpense) "Debit Expense" else "Credit / Deposit",
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
                            color = DribbblePurple.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "MANUAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = DribbblePurple,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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

                    transaction.accountOrCard?.let { rawCard ->
                        val cleanCard = rawCard.replace("*", "").trim()
                        val displayCard = if (cleanCard.isNotEmpty()) "•• $cleanCard" else rawCard
                        Text(
                            text = displayCard,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
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
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = amountColor
                )

                transaction.availableBalance?.let { bal ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Bal: ${CurrencyFormatter.format(bal, transaction.currency)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
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

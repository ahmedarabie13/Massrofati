package com.banksms.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.ui.theme.ExpenseCoral
import com.banksms.expensetracker.ui.theme.IncomeEmerald
import com.banksms.expensetracker.ui.theme.MasariCyan
import com.banksms.expensetracker.ui.theme.MasariEmerald
import com.banksms.expensetracker.util.CurrencyFormatter

@Composable
fun SummaryOverviewCard(
    totalExpense: Double,
    totalIncome: Double,
    netSavings: Double,
    currency: String,
    periodLabel: String,
    onPeriodClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPositiveFlow = netSavings >= 0
    val flowColor = if (isPositiveFlow) IncomeEmerald else ExpenseCoral
    val totalFlow = totalExpense + totalIncome
    val expenseRatio = if (totalFlow > 0) (totalExpense / totalFlow).toFloat().coerceIn(0f, 1f) else 0f
    val incomeRatio = if (totalFlow > 0) (totalIncome / totalFlow).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MasariEmerald.copy(alpha = 0.35f),
                        MasariCyan.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header: Status Badge & Period Picker Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Badge with pulsing indicator dot
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = flowColor.copy(alpha = 0.12f),
                        border = CardDefaults.outlinedCardBorder(enabled = true)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(flowColor)
                            )
                            Text(
                                text = if (isPositiveFlow) "NET SURPLUS" else "NET DEFICIT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.8.sp
                                ),
                                color = flowColor
                            )
                        }
                    }

                    // Interactive Period Selector Pill
                    Surface(
                        onClick = onPeriodClick,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        border = CardDefaults.outlinedCardBorder(enabled = true),
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MasariEmerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = periodLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Net Amount
                Text(
                    text = "Total Cash Flow",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = CurrencyFormatter.format(netSavings, currency),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            letterSpacing = (-0.5).sp,
                            color = flowColor
                        )
                    )
                    Icon(
                        imageVector = if (isPositiveFlow) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = flowColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Mini Flow Distribution Bar
                if (totalFlow > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (expenseRatio > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(expenseRatio.coerceAtLeast(0.01f))
                                    .background(ExpenseCoral)
                            )
                        }
                        if (incomeRatio > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(incomeRatio.coerceAtLeast(0.01f))
                                    .background(IncomeEmerald)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(expenseRatio * 100).toInt()}% Outflow",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(incomeRatio * 100).toInt()}% Inflow",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.8.dp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Split Metric Cards: Expenses vs Credits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricItem(
                        title = "Expenses",
                        subtitle = "Total spent",
                        amount = totalExpense,
                        currency = currency,
                        color = ExpenseCoral,
                        icon = Icons.Default.ArrowDownward,
                        modifier = Modifier.weight(1f)
                    )

                    MetricItem(
                        title = "Income",
                        subtitle = "Total received",
                        amount = totalIncome,
                        currency = currency,
                        color = IncomeEmerald,
                        icon = Icons.Default.ArrowUpward,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    title: String,
    subtitle: String,
    amount: Double,
    currency: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.07f),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = CurrencyFormatter.format(amount, currency),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    ),
                    color = color
                )
            }
        }
    }
}

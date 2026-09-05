package com.banksms.expensetracker.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.data.model.BankExpenseShare
import com.banksms.expensetracker.data.model.CategoryShare
import com.banksms.expensetracker.data.model.MonthlyTrend
import com.banksms.expensetracker.ui.components.BankBadge
import com.banksms.expensetracker.ui.components.DateRangeSelectionBottomSheet
import com.banksms.expensetracker.ui.components.MasariTopAppBar
import com.banksms.expensetracker.ui.components.SummaryOverviewCard
import com.banksms.expensetracker.ui.components.getBankColor
import com.banksms.expensetracker.ui.theme.CategoryColors
import com.banksms.expensetracker.ui.theme.ExpenseCoral
import com.banksms.expensetracker.ui.theme.IncomeEmerald
import com.banksms.expensetracker.ui.theme.MasariEmerald
import com.banksms.expensetracker.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MasariTopAppBar(
                title = "Reports",
                subtitle = "Analytics & Breakdown",
                showBrandEmblem = false,
                actions = {
                    IconButton(
                        onClick = {
                            CsvExporter.exportAndShare(context, state.rawTransactions)
                        },
                        enabled = state.rawTransactions.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export CSV Report",
                            tint = if (state.rawTransactions.isNotEmpty()) MasariEmerald else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Overview Hero Card
            item {
                Spacer(modifier = Modifier.height(2.dp))
                SummaryOverviewCard(
                    totalExpense = state.report.totalExpense,
                    totalIncome = state.report.totalIncome,
                    netSavings = state.report.netSavings,
                    currency = state.report.currency,
                    periodLabel = state.dateRange.label,
                    onPeriodClick = { showDatePicker = true }
                )
            }

            // 2. Export Button Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Export Detailed Report",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${state.rawTransactions.size} transactions in selected period",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { CsvExporter.exportAndShare(context, state.rawTransactions) },
                            enabled = state.rawTransactions.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MasariEmerald)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CSV", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Bank Expense Distribution
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(enabled = true),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Expenses by Bank",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${state.report.bankShares.size} banks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        if (state.report.bankShares.isEmpty()) {
                            Text(
                                text = "No bank expenses recorded for this period.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val maxBankExpense = state.report.bankShares.maxOfOrNull { it.totalExpense } ?: 1.0
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                state.report.bankShares.forEach { bankShare ->
                                    BankShareItem(
                                        bankShare = bankShare,
                                        maxAmount = maxBankExpense,
                                        currency = state.report.currency
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Category Breakdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(enabled = true),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Expenses by Category",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${state.report.categoryShares.size} categories",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        if (state.report.categoryShares.isEmpty()) {
                            Text(
                                text = "No category data available for this period.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                state.report.categoryShares.forEach { catShare ->
                                    CategoryShareItem(
                                        share = catShare,
                                        currency = state.report.currency
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Monthly History Trends
            if (state.report.monthlyTrends.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(enabled = true),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Monthly Cash Flow History",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.report.monthlyTrends.reversed().forEach { trend ->
                                    MonthlyTrendItem(trend = trend, currency = state.report.currency)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showDatePicker) {
            DateRangeSelectionBottomSheet(
                selectedPreset = state.dateRange.preset,
                onPresetSelected = { viewModel.setDateRangePreset(it) },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

@Composable
private fun BankShareItem(
    bankShare: BankExpenseShare,
    maxAmount: Double,
    currency: String
) {
    val fraction = if (maxAmount > 0) (bankShare.totalExpense / maxAmount).toFloat().coerceIn(0f, 1f) else 0f
    val bankColor = getBankColor(bankShare.sender)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankBadge(sender = bankShare.sender)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${bankShare.transactionCount} txns",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = CurrencyFormatter.format(bankShare.totalExpense, currency),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp
                ),
                color = ExpenseCoral
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = bankColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CategoryShareItem(
    share: CategoryShare,
    currency: String
) {
    val categoryColor = CategoryColors[share.category] ?: Color(0xFF64748B)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = share.category,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = categoryColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${String.format("%.1f", share.percentage)}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = categoryColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = CurrencyFormatter.format(share.totalAmount, currency),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        LinearProgressIndicator(
            progress = { (share.percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = categoryColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun MonthlyTrendItem(
    trend: MonthlyTrend,
    currency: String
) {
    val netSavings = trend.totalIncome - trend.totalExpense
    val isPositive = netSavings >= 0
    val totalMonthFlow = trend.totalExpense + trend.totalIncome
    val expenseRatio = if (totalMonthFlow > 0) (trend.totalExpense / totalMonthFlow).toFloat() else 0f
    val incomeRatio = if (totalMonthFlow > 0) (trend.totalIncome / totalMonthFlow).toFloat() else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = trend.monthLabel,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (isPositive) IncomeEmerald else ExpenseCoral).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isPositive) "+${CurrencyFormatter.format(netSavings, currency)}" else CurrencyFormatter.format(netSavings, currency),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            ),
                            color = if (isPositive) IncomeEmerald else ExpenseCoral,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Outflow",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.format(trend.totalExpense, currency),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = ExpenseCoral
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Inflow",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.format(trend.totalIncome, currency),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = IncomeEmerald
                        )
                    }
                }
            }

            if (totalMonthFlow > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
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
            }
        }
    }
}

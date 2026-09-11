package com.banksms.expensetracker.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
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
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.ui.components.BankBadge
import com.banksms.expensetracker.ui.components.DateRangeSelectionBottomSheet
import com.banksms.expensetracker.ui.components.DonutChart
import com.banksms.expensetracker.ui.components.DonutSegment
import com.banksms.expensetracker.ui.components.MonthPill
import com.banksms.expensetracker.ui.components.SegmentedToggle
import com.banksms.expensetracker.ui.components.getBankColor
import com.banksms.expensetracker.ui.components.getCategoryIcon
import com.banksms.expensetracker.ui.theme.CategoryColors
import com.banksms.expensetracker.ui.theme.DribbbleAmountRed
import com.banksms.expensetracker.ui.theme.DribbbleDonutPalette
import com.banksms.expensetracker.ui.theme.DribbbleGreen
import com.banksms.expensetracker.ui.theme.DribbblePurple
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
    var tabIndex by remember { mutableStateOf(0) } // 0 = Expenses, 1 = Income
    var byBank by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ReportHeader(
                periodLabel = state.dateRange.label,
                onPeriodClick = { showDatePicker = true },
                canExport = state.rawTransactions.isNotEmpty(),
                onExport = { CsvExporter.exportAndShare(context, state.rawTransactions) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SegmentedToggle(
                    options = listOf("Expenses", "Income"),
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (tabIndex == 0) {
                // ---- Expenses view ----
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expenses Report",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ViewToggleIcon(
                                icon = Icons.Default.BarChart,
                                contentDescription = "By bank",
                                selected = byBank,
                                onClick = { byBank = true }
                            )
                            ViewToggleIcon(
                                icon = Icons.Default.DonutLarge,
                                contentDescription = "By category",
                                selected = !byBank,
                                onClick = { byBank = false }
                            )
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.report.categoryShares.isEmpty()) {
                            DonutChart(
                                segments = listOf(DonutSegment(1f, MaterialTheme.colorScheme.surfaceContainerHigh)),
                                centerLabel = "Total Expenses",
                                centerValue = CurrencyFormatter.format(state.report.totalExpense, state.report.currency),
                                showBadge = false
                            )
                        } else if (byBank) {
                            val total = state.report.bankShares.sumOf { it.totalExpense }.coerceAtLeast(0.01)
                            DonutChart(
                                segments = state.report.bankShares.map { share ->
                                    DonutSegment(
                                        fraction = (share.totalExpense / total).toFloat(),
                                        color = getBankColor(share.sender)
                                    )
                                },
                                centerLabel = "Total Expenses",
                                centerValue = CurrencyFormatter.format(state.report.totalExpense, state.report.currency)
                            )
                        } else {
                            DonutChart(
                                segments = state.report.categoryShares.map { share ->
                                    DonutSegment(
                                        fraction = (share.percentage / 100f).coerceIn(0f, 1f),
                                        color = CategoryColors[share.category] ?: Color(0xFF64748B)
                                    )
                                },
                                centerLabel = "Total Expenses",
                                centerValue = CurrencyFormatter.format(state.report.totalExpense, state.report.currency)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (byBank) "All Banks" else "All Expenses",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Total  ",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.format(state.report.totalExpense, state.report.currency),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                if (byBank) {
                    if (state.report.bankShares.isEmpty()) {
                        item { EmptyNote("No bank expenses recorded for this period.") }
                    } else {
                        val maxBank = state.report.bankShares.maxOfOrNull { it.totalExpense } ?: 1.0
                        state.report.bankShares.forEach { share ->
                            item {
                                BankShareCard(
                                    share = share,
                                    maxAmount = maxBank,
                                    currency = state.report.currency,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                } else {
                    if (state.report.categoryShares.isEmpty()) {
                        item { EmptyNote("No category data available for this period.") }
                    } else {
                        state.report.categoryShares.forEach { share ->
                            item {
                                CategoryShareCard(
                                    share = share,
                                    currency = state.report.currency,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ---- Income view ----
                item {
                    Text(
                        text = "Income Report",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                val incomeMonths = state.report.monthlyTrends
                    .filter { it.totalIncome > 0 }
                    .takeLast(6)
                val incomeTotal = incomeMonths.sumOf { it.totalIncome }.coerceAtLeast(0.01)

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (incomeMonths.isEmpty()) {
                            DonutChart(
                                segments = listOf(DonutSegment(1f, MaterialTheme.colorScheme.surfaceContainerHigh)),
                                centerLabel = "Total Income",
                                centerValue = CurrencyFormatter.format(state.report.totalIncome, state.report.currency),
                                showBadge = false
                            )
                        } else {
                            DonutChart(
                                segments = incomeMonths.mapIndexed { index, trend ->
                                    DonutSegment(
                                        fraction = (trend.totalIncome / incomeTotal).toFloat(),
                                        color = DribbbleDonutPalette[index % DribbbleDonutPalette.size]
                                    )
                                },
                                centerLabel = "Total Income",
                                centerValue = CurrencyFormatter.format(state.report.totalIncome, state.report.currency)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Income",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Total  ",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.format(state.report.totalIncome, state.report.currency),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                if (incomeMonths.isEmpty()) {
                    item { EmptyNote("No income recorded for this period.") }
                } else {
                    val maxIncome = incomeMonths.maxOfOrNull { it.totalIncome } ?: 1.0
                    incomeMonths.reversed().forEachIndexed { index, trend ->
                        item {
                            IncomeMonthCard(
                                trend = trend,
                                color = DribbbleDonutPalette[index % DribbbleDonutPalette.size],
                                maxAmount = maxIncome,
                                currency = state.report.currency,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            // Export card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
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
                            colors = ButtonDefaults.buttonColors(containerColor = DribbblePurple)
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

            // Monthly trends
            if (state.report.monthlyTrends.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
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
                Spacer(modifier = Modifier.height(128.dp)) // Clearance for the floating dock
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportHeader(
    periodLabel: String,
    onPeriodClick: () -> Unit,
    canExport: Boolean,
    onExport: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Report",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        actions = {
            MonthPill(
                label = periodLabel,
                onClick = onPeriodClick,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(onClick = onExport, enabled = canExport) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export CSV Report",
                    tint = if (canExport) DribbblePurple
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun ViewToggleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) DribbblePurple else Color.Transparent,
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun CategoryShareCard(
    share: CategoryShare,
    currency: String,
    modifier: Modifier = Modifier
) {
    val categoryColor = CategoryColors[share.category] ?: Color(0xFF64748B)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(categoryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(share.category, TransactionType.EXPENSE),
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = share.category,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format("%.1f", share.percentage)}% of total",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = CurrencyFormatter.format(share.totalAmount, currency),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (share.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = categoryColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainer
            )
        }
    }
}

@Composable
private fun BankShareCard(
    share: BankExpenseShare,
    maxAmount: Double,
    currency: String,
    modifier: Modifier = Modifier
) {
    val fraction = if (maxAmount > 0) (share.totalExpense / maxAmount).toFloat().coerceIn(0f, 1f) else 0f
    val bankColor = getBankColor(share.sender)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BankBadge(sender = share.sender)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = "${share.transactionCount} txns",
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
                    text = CurrencyFormatter.format(share.totalExpense, currency),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    ),
                    color = DribbbleAmountRed
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = bankColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainer
            )
        }
    }
}

@Composable
private fun IncomeMonthCard(
    trend: MonthlyTrend,
    color: Color,
    maxAmount: Double,
    currency: String,
    modifier: Modifier = Modifier
) {
    val fraction = if (maxAmount > 0) (trend.totalIncome / maxAmount).toFloat().coerceIn(0f, 1f) else 0f
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trend.monthLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = CurrencyFormatter.format(trend.totalIncome, currency),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    ),
                    color = DribbbleGreen
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainer
            )
        }
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
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
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
                        color = (if (isPositive) DribbbleGreen else DribbbleAmountRed).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isPositive) "+${CurrencyFormatter.format(netSavings, currency)}" else CurrencyFormatter.format(netSavings, currency),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            ),
                            color = if (isPositive) DribbbleGreen else DribbbleAmountRed,
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
                            color = DribbbleAmountRed
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
                            color = DribbbleGreen
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
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    if (expenseRatio > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(expenseRatio.coerceAtLeast(0.01f))
                                .background(DribbbleAmountRed)
                        )
                    }
                    if (incomeRatio > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(incomeRatio.coerceAtLeast(0.01f))
                                .background(DribbbleGreen)
                        )
                    }
                }
            }
        }
    }
}

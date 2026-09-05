package com.banksms.expensetracker.ui.screens.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.ui.components.*
import com.banksms.expensetracker.ui.screens.transactions.TransactionDetailDialog
import com.banksms.expensetracker.ui.theme.MasariCyan
import com.banksms.expensetracker.ui.theme.MasariEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSyncMessage()
        }
    }

    // Spin animation for syncing
    val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SyncRotationAngle"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MasariTopAppBar(
                title = "Masari",
                subtitle = "Smart Financial Tracker",
                showBrandEmblem = true,
                actions = {
                    IconButton(
                        onClick = { viewModel.syncSms() },
                        enabled = !state.isSyncing
                    ) {
                        if (state.isSyncing) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Syncing SMS...",
                                tint = MasariEmerald,
                                modifier = Modifier.rotate(rotation)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Bank SMS",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
            // 1. Overview Summary Hero Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SummaryOverviewCard(
                    totalExpense = state.summary.totalExpense,
                    totalIncome = state.summary.totalIncome,
                    netSavings = state.summary.netSavings,
                    currency = state.summary.currency,
                    periodLabel = state.dateRange.label,
                    onPeriodClick = { showDatePicker = true }
                )
            }

            // 2. Quick Actions Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionButton(
                        title = "Add Expense",
                        subtitle = "Manual entry",
                        icon = Icons.Default.Add,
                        accentColor = MasariEmerald,
                        onClick = { showAddExpenseDialog = true },
                        modifier = Modifier.weight(1f)
                    )

                    QuickActionButton(
                        title = if (state.isSyncing) "Scanning..." else "Scan SMS",
                        subtitle = if (state.isSyncing) "In progress" else "Auto-sync",
                        icon = Icons.Default.Sync,
                        accentColor = MasariCyan,
                        isSpinning = state.isSyncing,
                        spinAngle = rotation,
                        onClick = { viewModel.syncSms() },
                        modifier = Modifier.weight(1f)
                    )

                    QuickActionButton(
                        title = "Activity",
                        subtitle = "All records",
                        icon = Icons.Default.ReceiptLong,
                        accentColor = Color(0xFFA78BFA), // Lavender / Violet
                        onClick = onNavigateToTransactions,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Recent Activity Section Header
            item {
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
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        )
                        if (state.recentTransactions.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${state.recentTransactions.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    TextButton(onClick = onNavigateToTransactions) {
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = MasariEmerald
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MasariEmerald,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // 4. Transactions List or Clean Empty State
            if (state.recentTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = CardDefaults.outlinedCardBorder(enabled = true)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(MasariEmerald.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MasariEmerald
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No transactions found in this period",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Scan your incoming bank SMS or record manual cash expenses to see your balance analytics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.syncSms() },
                                    enabled = !state.isSyncing,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MasariEmerald)
                                ) {
                                    Text(if (state.isSyncing) "Scanning..." else "Scan Bank SMS", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showAddExpenseDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("+ Add Manual", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                items(state.recentTransactions, key = { it.id }) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onClick = { selectedTransactionForDetail = transaction }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (showDatePicker) {
            DateRangeSelectionBottomSheet(
                selectedPreset = state.dateRange.preset,
                onPresetSelected = { viewModel.setDateRangePreset(it) },
                onDismiss = { showDatePicker = false }
            )
        }

        if (showAddExpenseDialog) {
            AddEditExpenseDialog(
                initialExpense = null,
                onDismiss = { showAddExpenseDialog = false },
                onSave = { expense -> viewModel.addManualExpense(expense) }
            )
        }

        selectedTransactionForDetail?.let { tx ->
            TransactionDetailDialog(
                transaction = tx,
                onDismiss = { selectedTransactionForDetail = null },
                onSkipTransaction = { viewModel.skipTransaction(it) },
                onDeleteManual = { viewModel.deleteManualExpense(it) }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    isSpinning: Boolean = false,
    spinAngle: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder(enabled = true),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(
                        width = 0.8.dp,
                        color = accentColor.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (isSpinning) Modifier.rotate(spinAngle) else Modifier)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

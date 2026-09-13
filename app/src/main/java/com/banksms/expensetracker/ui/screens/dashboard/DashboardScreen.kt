package com.banksms.expensetracker.ui.screens.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.ui.components.AddEditExpenseDialog
import com.banksms.expensetracker.ui.components.DateRangeSelectionBottomSheet
import com.banksms.expensetracker.ui.components.MonthPill
import com.banksms.expensetracker.ui.components.TransactionItem
import com.banksms.expensetracker.ui.screens.transactions.TransactionDetailDialog
import com.banksms.expensetracker.ui.theme.DribbbleBlue
import com.banksms.expensetracker.ui.theme.DribbbleBluePale
import com.banksms.expensetracker.ui.theme.DribbbleCoral
import com.banksms.expensetracker.ui.theme.DribbbleCoralPale
import com.banksms.expensetracker.ui.theme.DribbbleHeaderBottom
import com.banksms.expensetracker.ui.theme.DribbbleHeaderTop
import com.banksms.expensetracker.ui.theme.DribbblePurple
import com.banksms.expensetracker.ui.theme.DribbblePurplePale
import com.banksms.expensetracker.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToReports: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
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

    val todayLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM, yyyy", Locale.ENGLISH))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Purple balance header
            item {
                BalanceHeaderCard(
                    balance = CurrencyFormatter.format(state.summary.netSavings, state.summary.currency),
                    incomePill = "+ ${CurrencyFormatter.format(state.summary.totalIncome, state.summary.currency)} income",
                    periodLabel = state.dateRange.shortLabel,
                    isSyncing = state.isSyncing,
                    onPeriodClick = { showDatePicker = true },
                    // Tapping the spinner while syncing stops the sync.
                    onSyncClick = { if (state.isSyncing) viewModel.stopSync() else viewModel.syncSms() },
                    onChatClick = onNavigateToChat,
                    onProfileClick = onNavigateToProfile,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
            }

            // 2. Your Money + Details
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Money",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        onClick = onNavigateToReports,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Income / Expenses cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MoneyCard(
                        label = "Income",
                        amount = CurrencyFormatter.format(state.summary.totalIncome, state.summary.currency),
                        icon = Icons.Default.AccountBalanceWallet,
                        iconTint = DribbbleBlue,
                        iconBg = DribbbleBluePale,
                        modifier = Modifier.weight(1f)
                    )
                    MoneyCard(
                        label = "Expenses",
                        amount = CurrencyFormatter.format(state.summary.totalExpense, state.summary.currency),
                        icon = Icons.Default.Wallet,
                        iconTint = DribbbleCoral,
                        iconBg = DribbbleCoralPale,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Black insight banner
            item {
                Surface(
                    onClick = onNavigateToReports,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = DribbblePurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your insight is ready",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "View",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 5. Transactions header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        onClick = { showDatePicker = true },
                        shape = CircleShape,
                        color = DribbblePurplePale
                    ) {
                        Text(
                            text = "For the Period",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = DribbblePurple,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // 6. Date + total row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = todayLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Total  ",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.format(state.summary.totalExpense, state.summary.currency),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // 7. Transactions list / empty state
            if (state.recentTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
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
                                    .background(DribbblePurple.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = DribbblePurple
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
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { if (state.isSyncing) viewModel.stopSync() else viewModel.syncSms() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isSyncing) DribbbleCoral else DribbblePurple
                                    )
                                ) {
                                    Text(
                                        if (state.isSyncing) "Stop" else "Scan Bank SMS",
                                        fontWeight = FontWeight.Bold
                                    )
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
                        onClick = { selectedTransactionForDetail = transaction },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
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

        if (showAddExpenseDialog) {
            AddEditExpenseDialog(
                initialExpense = null,
                onDismiss = { showAddExpenseDialog = false },
                onSave = { expense -> viewModel.addManualExpense(expense) }
            )
        }

        selectedTransactionForDetail?.let { selected ->
            // Refresh from state so a re-scan immediately shows the new parse.
            val tx = state.recentTransactions.firstOrNull { it.id == selected.id } ?: selected
            TransactionDetailDialog(
                transaction = tx,
                onDismiss = {
                    selectedTransactionForDetail = null
                    viewModel.clearRescanMessage()
                },
                showRescan = state.isAiMode,
                rescanInFlight = state.rescanInFlightId == tx.id,
                rescanMessage = state.rescanMessage,
                onRescan = { viewModel.rescanTransaction(tx) },
                onSkipTransaction = { viewModel.skipTransaction(it) },
                onDeleteManual = { viewModel.deleteManualExpense(it) }
            )
        }
    }
}

@Composable
private fun BalanceHeaderCard(
    balance: String,
    incomePill: String,
    periodLabel: String,
    isSyncing: Boolean,
    onPeriodClick: () -> Unit,
    onSyncClick: () -> Unit,
    onChatClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(DribbbleHeaderTop, DribbbleHeaderBottom)
                )
            )
    ) {
        // Soft decorative circles
        Box(
            modifier = Modifier
                .size(170.dp)
                .offset(x = 130.dp, y = (-60).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .align(Alignment.TopEnd)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = (-40).dp, y = 120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .align(Alignment.BottomStart)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Balanced slots: equal 88dp sides keep the month pill truly
            // centered, and the weighted pill shrinks (ellipsis) instead of
            // shoving the chat/sync buttons off-screen on narrow devices.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(88.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Surface(
                        onClick = onProfileClick,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                MonthPill(
                    label = periodLabel,
                    onClick = onPeriodClick,
                    containerColor = Color.White.copy(alpha = 0.22f),
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    modifier = Modifier.width(88.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onChatClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Chat with Assistant",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onSyncClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        if (isSyncing) {
                            // Static stop square: tap halts the running sync.
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop sync",
                                tint = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Bank SMS",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Current Balance",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = balance,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp,
                    letterSpacing = (-1).sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f)
            ) {
                Text(
                    text = incomePill,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MoneyCard(
    label: String,
    amount: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

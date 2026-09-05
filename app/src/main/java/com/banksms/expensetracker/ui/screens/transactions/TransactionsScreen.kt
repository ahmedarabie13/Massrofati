package com.banksms.expensetracker.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.model.Transaction
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.ui.components.AddEditExpenseDialog
import com.banksms.expensetracker.ui.components.DateRangeSelectionBottomSheet
import com.banksms.expensetracker.ui.components.MasariTopAppBar
import com.banksms.expensetracker.ui.components.TransactionItem
import com.banksms.expensetracker.ui.components.TransactionTypeFilterRow
import com.banksms.expensetracker.ui.theme.MasariEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var editingManualExpense by remember { mutableStateOf<ManualExpense?>(null) }

    Scaffold(
        topBar = {
            MasariTopAppBar(
                title = "Transactions",
                subtitle = "${state.transactions.size} records found",
                showBrandEmblem = false
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddExpenseDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Expense") },
                text = { Text("Add Expense", fontWeight = FontWeight.Bold) },
                containerColor = MasariEmerald,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search merchant, amount, or bank...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MasariEmerald,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Date Range Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = CardDefaults.outlinedCardBorder(enabled = true)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MasariEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = state.dateRange.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
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

                Text(
                    text = "${state.transactions.size} transactions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Type Filter Chips Row
            TransactionTypeFilterRow(
                selectedType = state.selectedType,
                onTypeSelected = { viewModel.setTypeFilter(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Bank Filter Chips Row
            if (state.availableBanks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.width(8.dp))

                    FilterChip(
                        selected = state.selectedBank == null,
                        onClick = { viewModel.setBankFilter(null) },
                        label = { Text("All Sources", fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )

                    state.availableBanks.forEach { bank ->
                        FilterChip(
                            selected = state.selectedBank == bank,
                            onClick = { viewModel.setBankFilter(if (state.selectedBank == bank) null else bank) },
                            label = { Text(bank, fontSize = 12.sp) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Transactions List
            if (state.transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No matching transactions found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try adjusting your filters or date range.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    items(state.transactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            onClick = { selectedTransactionForDetail = transaction }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(84.dp)) // Padding for Extended FAB
                    }
                }
            }
        }

        if (showDatePicker) {
            DateRangeSelectionBottomSheet(
                selectedPreset = state.dateRange.preset,
                onPresetSelected = { viewModel.setDateRangePreset(it) },
                onDismiss = { showDatePicker = false }
            )
        }

        selectedTransactionForDetail?.let { tx ->
            TransactionDetailDialog(
                transaction = tx,
                onDismiss = { selectedTransactionForDetail = null },
                onEditManual = { manualTx ->
                    editingManualExpense = ManualExpense(
                        id = manualTx.manualId ?: "",
                        amount = manualTx.amount,
                        currency = manualTx.currency,
                        type = manualTx.type,
                        category = manualTx.category,
                        merchant = manualTx.merchant ?: "",
                        paymentMethod = manualTx.accountOrCard ?: "Cash",
                        timestamp = manualTx.timestamp,
                        note = manualTx.rawBody.removePrefix("Note: ").takeIf { it != "Manual entry recorded on app" } ?: ""
                    )
                },
                onDeleteManual = { manualId ->
                    viewModel.deleteManualExpense(manualId)
                },
                onSkipTransaction = { smsTx ->
                    viewModel.skipTransaction(smsTx)
                }
            )
        }

        if (showAddExpenseDialog) {
            AddEditExpenseDialog(
                initialExpense = null,
                onDismiss = { showAddExpenseDialog = false },
                onSave = { expense -> viewModel.addManualExpense(expense) }
            )
        }

        editingManualExpense?.let { expense ->
            AddEditExpenseDialog(
                initialExpense = expense,
                onDismiss = { editingManualExpense = null },
                onSave = { updated -> viewModel.updateManualExpense(updated) },
                onDelete = { manualId -> viewModel.deleteManualExpense(manualId) }
            )
        }
    }
}

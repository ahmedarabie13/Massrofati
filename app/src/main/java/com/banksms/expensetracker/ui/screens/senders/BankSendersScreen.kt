package com.banksms.expensetracker.ui.screens.senders

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.model.ParseMode
import com.banksms.expensetracker.data.model.TransactionType
import com.banksms.expensetracker.data.parser.AiScanProgress
import com.banksms.expensetracker.data.parser.SenderScanStatus
import com.banksms.expensetracker.ui.components.BankBadge
import com.banksms.expensetracker.ui.components.RizeqTopAppBar
import com.banksms.expensetracker.ui.theme.ExpenseCoral
import com.banksms.expensetracker.ui.theme.IncomeEmerald
import com.banksms.expensetracker.ui.theme.DribbblePurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSendersScreen(
    viewModel: BankSendersViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showAddBankDialog by remember { mutableStateOf(false) }
    var editingBank by remember { mutableStateOf<BankSender?>(null) }
    var bankToDelete by remember { mutableStateOf<BankSender?>(null) }
    var showClearFilesDialog by remember { mutableStateOf(false) }
    var showSandboxDialog by remember { mutableStateOf(false) }
    var showAddTemplateDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<MessageTemplate?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isAllFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshFromFiles()
    }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RizeqTopAppBar(
                title = "Banks & Parser",
                subtitle = if (state.selectedTab == 0) "Monitored Bank Senders" else "Custom SMS Templates",
                showBrandEmblem = false,
                actions = {
                    IconButton(onClick = { viewModel.refreshFromFiles() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload from File System",
                            tint = DribbblePurple
                        )
                    }
                    IconButton(onClick = { showSandboxDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Test SMS Parser",
                            tint = DribbblePurple
                        )
                    }
                    if (state.selectedTab == 0) {
                        IconButton(
                            onClick = { viewModel.discoverFromInbox() },
                            enabled = !state.isDiscovering
                        ) {
                            if (state.isDiscovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = DribbblePurple
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FindInPage,
                                    contentDescription = "Discover Senders"
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Lifted clear of the floating dock
            Box(modifier = Modifier.padding(bottom = 104.dp)) {
                if (state.selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { showAddBankDialog = true },
                        containerColor = DribbblePurple,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Bank")
                    }
                } else {
                    ExtendedFloatingActionButton(
                        onClick = { showAddTemplateDialog = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = "New Template") },
                        text = { Text("New Template", fontWeight = FontWeight.Bold) },
                        containerColor = DribbblePurple,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Selector: Monitored Banks vs Parser Templates
            PrimaryTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = DribbblePurple,
                divider = {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.8.dp
                    )
                }
            ) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = {
                        Text(
                            text = "Monitored Banks (${state.senders.size})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (state.selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = {
                        Text(
                            text = "SMS Templates (${state.templates.size})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (state.selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
            }

            if (state.selectedTab == 0) {
                // TAB 0: Monitored Banks
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        ParsingModeCard(
                            mode = state.parseMode,
                            aiScan = state.aiScan,
                            isSyncing = state.isAiSyncing || state.syncRunning,
                            modelAvailable = remember(state.parseMode) {
                                viewModel.isAiModelAvailable()
                            },
                            onSelectMode = { viewModel.setParseMode(it) },
                            onRescan = { viewModel.rescanInbox() },
                            onClearAi = { viewModel.clearAiData() },
                            onDismissScan = { viewModel.dismissAiScan() },
                            onStopSync = { viewModel.stopSync() }
                        )
                    }

                    item {
                        StoragePersistenceCard(
                            storagePath = state.storagePath,
                            isPublicStorage = state.isPublicStorageActive,
                            isAllFilesGranted = isAllFilesGranted,
                            onRequestPermission = { requestAllFilesAccess() },
                            onClearFiles = { showClearFilesDialog = true }
                        )
                    }

                    item {
                        AccountCard()
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(enabled = true),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Selective Bank Monitoring",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Manage your monitored banks. You can add, edit, or remove banks at any time. Configurations are stored in monitored_banks.json in the file system.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(state.senders, key = { it.senderId }) { sender ->
                        BankSenderItem(
                            sender = sender,
                            onToggle = { viewModel.toggleMonitored(sender) },
                            onEdit = { editingBank = sender },
                            onDelete = { bankToDelete = sender }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(128.dp)) // Clearance for FAB + floating dock
                    }
                }
            } else {
                // TAB 1: Parser Message Templates
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(enabled = true),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Custom SMS Template Engine",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Define and save SMS patterns to extract transactions from any bank. Placeholders like {amount}, {merchant}, {card}, and {balance} are automatically parsed and saved in the file system.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (state.templates.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.DataObject,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No custom templates yet",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap '+ New Template' to add a pattern for any bank.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.templates, key = { it.id }) { template ->
                            MessageTemplateCard(
                                template = template,
                                onToggle = { viewModel.toggleTemplate(template.id, !template.isEnabled) },
                                onClick = { editingTemplate = template }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(128.dp)) // Clearance for FAB + floating dock
                    }
                }
            }
        }

        if (showAddBankDialog) {
            AddEditBankDialog(
                initialBank = null,
                onDismiss = { showAddBankDialog = false },
                onSave = { _, newBank ->
                    viewModel.addSender(newBank.senderId, newBank.displayName, newBank.customRegex)
                    showAddBankDialog = false
                }
            )
        }

        editingBank?.let { bank ->
            AddEditBankDialog(
                initialBank = bank,
                onDismiss = { editingBank = null },
                onSave = { oldId, updatedBank ->
                    viewModel.updateSender(oldId ?: updatedBank.senderId, updatedBank)
                    editingBank = null
                }
            )
        }

        bankToDelete?.let { bank ->
            DeleteBankConfirmDialog(
                bank = bank,
                onDismiss = { bankToDelete = null },
                onConfirm = {
                    viewModel.deleteSender(bank)
                    bankToDelete = null
                }
            )
        }

        if (showClearFilesDialog) {
            ClearFilesConfirmDialog(
                storagePath = state.storagePath,
                onDismiss = { showClearFilesDialog = false },
                onConfirm = {
                    viewModel.clearAllPersistenceFiles()
                    showClearFilesDialog = false
                }
            )
        }

        if (showSandboxDialog) {
            SmsParserSandboxDialog(
                onDismiss = { showSandboxDialog = false }
            )
        }

        if (showAddTemplateDialog) {
            AddEditTemplateDialog(
                initialTemplate = null,
                onDismiss = { showAddTemplateDialog = false },
                onSave = { template -> viewModel.saveTemplate(template) }
            )
        }

        editingTemplate?.let { tpl ->
            AddEditTemplateDialog(
                initialTemplate = tpl,
                onDismiss = { editingTemplate = null },
                onSave = { updated -> viewModel.saveTemplate(updated) },
                onDelete = { id -> viewModel.deleteTemplate(id) }
            )
        }

        if (state.showDiscoveredDialog) {
            DiscoveredSendersBottomSheet(
                discovered = state.discoveredSenders,
                monitoredIds = state.senders.map { it.senderId }.toSet(),
                onAddSender = { viewModel.addDiscoveredSender(it) },
                onDismiss = { viewModel.closeDiscoveredDialog() }
            )
        }
    }
}

@Composable
private fun ParsingModeCard(
    mode: ParseMode,
    aiScan: AiScanProgress?,
    isSyncing: Boolean,
    modelAvailable: Boolean,
    onSelectMode: (ParseMode) -> Unit,
    onRescan: () -> Unit,
    onClearAi: () -> Unit,
    onDismissScan: () -> Unit,
    onStopSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(enabled = true),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = DribbblePurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Parsing Mode",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (mode == ParseMode.AI) DribbblePurple.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (mode == ParseMode.AI) "AI DATABASE" else "MANUAL DATABASE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        ),
                        color = if (mode == ParseMode.AI) DribbblePurple
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Manual is the fast regex parser. AI sends messages to the on-device " +
                    "model 20 at a time across 3 engines in parallel and stores results " +
                    "in a separate database. Only messages mentioning a currency are scanned. " +
                    "Dashboard, Transactions, Reports and Assistant always follow the active mode.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            ParseModeOption(
                selected = mode == ParseMode.MANUAL,
                title = "Manual parsing",
                subtitle = "Regex + templates · instant",
                onClick = { onSelectMode(ParseMode.MANUAL) }
            )
            ParseModeOption(
                selected = mode == ParseMode.AI,
                title = "AI parsing",
                subtitle = "On-device model · slower, separate database",
                onClick = { onSelectMode(ParseMode.AI) }
            )

            // The scan panel + Stop stay visible whenever a scan exists or is
            // running — even in Manual mode, since switching modes doesn't
            // stop a running scan and it must remain stoppable from here.
            val showScanControls = mode == ParseMode.AI || isSyncing || aiScan != null
            if (mode == ParseMode.AI && !modelAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI model not downloaded — get it from the Assistant tab. " +
                        "Scans will report the missing model until then.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (showScanControls) {
                Spacer(modifier = Modifier.height(10.dp))
                aiScan?.let { scan ->
                    AiScanReport(scan = scan, onDismiss = onDismissScan)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (isSyncing) {
                    // A sync (this rescan or Dashboard auto-sync) is running:
                    // offer Stop. In-flight model calls finish naturally, no
                    // new batches start, partial results are kept.
                    OutlinedButton(
                        onClick = onStopSync,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseCoral),
                        border = BorderStroke(1.dp, ExpenseCoral.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop sync", fontWeight = FontWeight.Bold)
                    }
                } else if (mode == ParseMode.AI) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onRescan,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DribbblePurple),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Rescan inbox with AI")
                        }
                        TextButton(onClick = onClearAi) {
                            Text("Clear AI data", color = ExpenseCoral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiScanReport(scan: AiScanProgress, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (scan.finished) "Scan complete"
                else "Scanning… batch ${scan.batchesDone}/${scan.batchesTotal}" +
                    (if (scan.activeCalls > 0) " · ${scan.activeCalls} active" else ""),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (scan.finished) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Dismiss")
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = DribbblePurple
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${scan.scanned}/${scan.totalMessages} scanned · " +
                "${scan.imported} imported · ${scan.excluded} excluded · " +
                "${scan.remaining} remaining",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val toScanTotal = scan.totalMessages - scan.excluded
        if (!scan.finished && toScanTotal > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = scan.scanned.toFloat() / toScanTotal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
                color = DribbblePurple
            )
        }

        if (scan.excluded > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Excluded: ${scan.excludedNoCurrency} no currency · " +
                    "${scan.excludedDuplicate} duplicates · ${scan.excludedSkipped} skipped · " +
                    "${scan.excludedScanned} already scanned",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (scan.senders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            scan.senders.forEach { sender -> SenderScanRow(status = sender) }
        }

        scan.errors.forEach { err ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SenderScanRow(status: SenderScanStatus) {
    val dot = when {
        status.isExcluded -> MaterialTheme.colorScheme.outline
        status.isDone -> IncomeEmerald
        else -> DribbblePurple
    }
    val label = when {
        status.isExcluded -> "Excluded"
        status.isDone -> "Done"
        else -> "${status.remaining} left"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status.sender,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${status.scanned}/${status.toScan}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = dot
            )
        }
        if (!status.isDone && status.toScan > 0) {
            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = status.scanned.toFloat() / status.toScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
                color = DribbblePurple
            )
        }
    }
}

@Composable
private fun ParseModeOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = DribbblePurple)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageTemplateCard(
    template: MessageTemplate,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (template.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (template.isEnabled) 1.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (template.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (template.sender.isNotBlank() && template.sender != "*") {
                            BankBadge(sender = template.sender)
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "ANY BANK",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isExpense = template.defaultType == TransactionType.EXPENSE
                        Text(
                            text = if (isExpense) "Debit (Expense)" else "Credit (Income)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isExpense) ExpenseCoral else IncomeEmerald
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = template.defaultCurrency,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = template.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = DribbblePurple
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pattern Code Snippet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = template.pattern.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BankSenderItem(
    sender: BankSender,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sender.isMonitored) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (sender.isMonitored) 1.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BankBadge(sender = sender.senderId)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sender.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (sender.isMonitored) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SMS ID: ${sender.senderId}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${sender.totalTransactionsCount} txs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Bank",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Bank",
                        tint = ExpenseCoral,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Switch(
                    checked = sender.isMonitored,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = DribbblePurple
                    )
                )
            }
        }
    }
}

@Composable
private fun AddEditBankDialog(
    initialBank: BankSender?,
    onDismiss: () -> Unit,
    onSave: (oldSenderId: String?, updated: BankSender) -> Unit
) {
    val isEditing = initialBank != null
    var senderId by remember { mutableStateOf(initialBank?.senderId ?: "") }
    var displayName by remember { mutableStateOf(initialBank?.displayName ?: "") }
    var customRegex by remember { mutableStateOf(initialBank?.customRegex ?: "") }
    var isMonitored by remember { mutableStateOf(initialBank?.isMonitored ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Monitored Bank" else "Add Monitored Bank",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "The SMS Sender ID must match the sender name shown in your messages app (e.g. 'alinma', 'alrajhibank', 'SNB').",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Bank Name *") },
                    placeholder = { Text("e.g. Alinma Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = senderId,
                    onValueChange = { senderId = it },
                    label = { Text("SMS Sender ID *") },
                    placeholder = { Text("e.g. alinma") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = customRegex,
                    onValueChange = { customRegex = it },
                    label = { Text("Custom Pattern / Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Active Monitoring",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Switch(
                        checked = isMonitored,
                        onCheckedChange = { isMonitored = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = DribbblePurple
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = displayName.trim().ifBlank { senderId.trim() }
                    val updated = BankSender(
                        senderId = senderId.trim(),
                        displayName = finalName,
                        isMonitored = isMonitored,
                        customRegex = customRegex.trim().takeIf { it.isNotBlank() },
                        totalTransactionsCount = initialBank?.totalTransactionsCount ?: 0,
                        lastTransactionTime = initialBank?.lastTransactionTime
                    )
                    onSave(initialBank?.senderId, updated)
                },
                enabled = senderId.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DribbblePurple)
            ) {
                Text(if (isEditing) "Save Changes" else "Add Bank")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteBankConfirmDialog(
    bank: BankSender,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = ExpenseCoral,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("Delete Bank?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Are you sure you want to remove '${bank.displayName}' (${bank.senderId})? Messages from this bank will no longer be tracked and it will be deleted from monitored_banks.json.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral)
            ) {
                Text("Delete Bank")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ClearFilesConfirmDialog(
    storagePath: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(30.dp)
            )
        },
        title = { Text("Clear All Saved Files?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This will permanently delete all 4 persistence files in:\n$storagePath",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp
                )
                Text(
                    text = "• manual_expenses.json\n• skipped_transactions.json\n• message_templates.json\n• monitored_banks.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete All Files")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AccountCard() {
    val context = LocalContext.current
    val app = context.applicationContext as com.banksms.expensetracker.BankSmsApp
    val user = app.authRepository.currentUser
    var biometricOn by remember {
        mutableStateOf(user?.let { app.biometricUnlock.isEnabledFor(it.uid) } ?: false)
    }
    val canUseBiometrics = remember { app.biometricUnlock.canAuthenticate(context) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(enabled = true),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = DribbblePurple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (!user?.displayName.isNullOrBlank()) user?.displayName ?: ""
                else user?.email ?: "Signed in",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!user?.displayName.isNullOrBlank()) {
                Text(
                    text = user?.email ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canUseBiometrics && user != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fingerprint unlock",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = biometricOn,
                        onCheckedChange = { checked ->
                            app.biometricUnlock.setEnabled(user.uid, checked)
                            biometricOn = checked
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { app.authRepository.signOut() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun StoragePersistenceCard(
    storagePath: String,
    isPublicStorage: Boolean,
    isAllFilesGranted: Boolean,
    onRequestPermission: () -> Unit,
    onClearFiles: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(enabled = true),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = DribbblePurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Data Files & Persistence",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPublicStorage) DribbblePurple.copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = if (isPublicStorage) "Public Storage (Persistent)" else "Internal Sandboxed",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
                        color = if (isPublicStorage) DribbblePurple else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Directory: $storagePath",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Files saved here survive clearing application storage in Android Settings. You can also view or edit them via any file manager.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isAllFilesGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grant All Files Access for Public Storage",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onClearFiles,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseCoral),
                    border = BorderStroke(1.dp, ExpenseCoral.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear All Saved Files",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveredSendersBottomSheet(
    discovered: List<com.banksms.expensetracker.data.reader.DiscoveredSender>,
    monitoredIds: Set<String>,
    onAddSender: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Senders Found in SMS Inbox",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap on any sender from your messages to add and monitor its transactions:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (discovered.isEmpty()) {
                Text(
                    text = "No senders found in your SMS inbox.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discovered) { item ->
                        val isAlreadyAdded = monitoredIds.contains(item.address)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable(enabled = !isAlreadyAdded) {
                                    onAddSender(item.address)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = item.address,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${item.messageCount} messages in inbox",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isAlreadyAdded) {
                                Text(
                                    text = "Monitored",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = DribbblePurple
                                )
                            } else {
                                Button(
                                    onClick = { onAddSender(item.address) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DribbblePurple)
                                ) {
                                    Text("+ Track")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

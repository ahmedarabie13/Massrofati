package com.banksms.expensetracker.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banksms.expensetracker.data.llm.EngineState
import com.banksms.expensetracker.data.llm.LlmModelFiles
import com.banksms.expensetracker.data.llm.ModelExportState
import com.banksms.expensetracker.data.llm.ModelFetchState
import com.banksms.expensetracker.ui.theme.DribbblePurple
import com.banksms.expensetracker.ui.theme.DribbblePurplePale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val fetchState by viewModel.fetchState.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    var input by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }

    // Follow the conversation: new messages always scroll into view; streamed
    // text only pulls us down when the user is already near the bottom, so
    // reading history (or a just-computed direct answer above the fold) is
    // never yanked away. Text-only updates (no count change) must also
    // trigger — otherwise instant answers render below the visible viewport.
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val layout = listState.layoutInfo
        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
        if (lastVisible == null || layout.totalItemsCount - lastVisible <= 2) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(exportState) {
        when (val export = exportState) {
            is ModelExportState.Done -> {
                snackbarHostState.showSnackbar("Model saved to ${export.location}")
                viewModel.dismissExportState()
            }
            is ModelExportState.Failed -> {
                snackbarHostState.showSnackbar("Backup failed: ${export.reason}")
                viewModel.dismissExportState()
            }
            else -> {}
        }
    }

    fun submit() {
        viewModel.sendMessage(input)
        input = ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Assistant",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (state.isDemo) MaterialTheme.colorScheme.surfaceContainerHigh
                            else DribbblePurplePale
                        ) {
                            Text(
                                text = when {
                                    state.engineState == EngineState.LOADING -> "Loading…"
                                    state.isDemo -> "Demo"
                                    state.engineState == EngineState.READY -> "On-device"
                                    else -> "Offline"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (state.isDemo || state.engineState == EngineState.LOADING)
                                    MaterialTheme.colorScheme.onSurfaceVariant else DribbblePurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Assistant options",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save model to Downloads") },
                                enabled = !state.isDemo &&
                                    exportState !is ModelExportState.Copying,
                                onClick = {
                                    showMenu = false
                                    viewModel.exportModelToDownloads()
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isDemo) {
                ModelSetupCard(
                    fetchState = fetchState,
                    onDownload = { viewModel.startDownload() },
                    onCancelDownload = { viewModel.cancelDownload() },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onDismissError = { viewModel.dismissFetchError() }
                )
            }

            val export = exportState
            if (export is ModelExportState.Copying) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Backing up model… ${export.copiedMb} MB copied",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { export.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = DribbblePurple,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }

            state.engineStatus?.let { status ->
                if (state.engineState == EngineState.ERROR) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
                if (state.isGenerating && state.messages.lastOrNull()?.streaming == false) {
                    item { TypingIndicator() }
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Input bar
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                ),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Ask about your spending…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = { submit() },
                        enabled = input.isNotBlank() && !state.isGenerating,
                        shape = CircleShape,
                        color = if (input.isNotBlank() && !state.isGenerating) DribbblePurple
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (input.isNotBlank() && !state.isGenerating) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (message.isUser) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = DribbblePurple,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                MarkdownText(
                    markdown = if (message.streaming && message.text.isEmpty()) "…" else message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "Typing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TypingAlpha"
    )
    Surface(
        shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = "●●●",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(alpha)
        )
    }
}

@Composable
private fun ModelSetupCard(
    fetchState: ModelFetchState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onImport: () -> Unit,
    onDismissError: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = DribbblePurple,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "On-device model needed",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Gemma 4 E4B (~3.7 GB, one-time). AI Edge Gallery keeps its copy " +
                    "in private storage, so fetch it here — download directly or " +
                    "import a ${LlmModelFiles.MODEL_FILE_NAME} file you already have.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (fetchState) {
                is ModelFetchState.Downloading -> {
                    val totalLabel = if (fetchState.totalMb > 0) " / ${fetchState.totalMb} MB" else ""
                    Text(
                        text = "Downloading… ${fetchState.downloadedMb} MB$totalLabel",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { fetchState.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = DribbblePurple,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCancelDownload,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                }
                is ModelFetchState.Importing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = DribbblePurple
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Copying model file…",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ModelFetchState.Failed -> {
                    Text(
                        text = fetchState.reason,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onDismissError(); onDownload() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DribbblePurple)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onImport,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Import file", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DribbblePurple)
                        ) {
                            Text("Download", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onImport,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Import file", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

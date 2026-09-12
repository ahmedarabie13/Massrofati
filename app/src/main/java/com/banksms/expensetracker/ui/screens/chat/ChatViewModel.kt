package com.banksms.expensetracker.ui.screens.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.banksms.expensetracker.data.llm.ChatEngine
import com.banksms.expensetracker.data.llm.EngineState
import com.banksms.expensetracker.data.llm.HistoryContext
import com.banksms.expensetracker.data.llm.LlmEngines
import com.banksms.expensetracker.data.llm.LlmModelFiles
import com.banksms.expensetracker.data.llm.ModelDownloader
import com.banksms.expensetracker.data.llm.ModelExportState
import com.banksms.expensetracker.data.llm.ModelFetchState
import com.banksms.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val streaming: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = listOf(
        ChatUiMessage(
            id = 0L,
            isUser = false,
            text = "Hi, I'm your on-device assistant. Ask me about your spending — " +
                "everything runs locally on your phone."
        )
    ),
    val isGenerating: Boolean = false,
    val engineState: EngineState = EngineState.IDLE,
    val engineStatus: String? = null,
    val isDemo: Boolean = true
)

class ChatViewModel(
    application: Application,
    private val repository: TransactionRepository,
    private var engine: ChatEngine
) : ViewModel() {

    private val appContext = application.applicationContext
    private val downloader = ModelDownloader(appContext)

    private val _uiState = MutableStateFlow(ChatUiState(isDemo = engine.isDemo))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _fetchState = MutableStateFlow<ModelFetchState>(ModelFetchState.Idle)
    val fetchState: StateFlow<ModelFetchState> = _fetchState.asStateFlow()

    private val _exportState = MutableStateFlow<ModelExportState>(ModelExportState.Idle)
    val exportState: StateFlow<ModelExportState> = _exportState.asStateFlow()

    private var nextId = 1L
    private var engineCollectors: Job? = null
    private var downloadCancelled = false

    companion object {
        /** Push streamed text to the UI at most this often. */
        private const val STREAM_UI_THROTTLE_MS = 120L
        /** Safety cap so a runaway generation can't grow the UI string forever. */
        private const val MAX_REPLY_CHARS = 8000
    }

    init {
        observeEngine(engine)
        viewModelScope.launch {
            engine.ensureLoaded()
        }
    }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating) return
        val userMsg = ChatUiMessage(id = nextId++, isUser = true, text = text)
        val modelMsgId = nextId++
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg +
                    ChatUiMessage(id = modelMsgId, isUser = false, text = "", streaming = true),
                isGenerating = true
            )
        }
        viewModelScope.launch {
            try {
                // Whole history goes to the model; it filters and answers itself.
                val context = HistoryContext.build(repository)
                val builder = StringBuilder()
                var truncated = false
                // Throttle UI updates: fast token streams would otherwise
                // recompose the whole message list on every delta.
                var lastPush = 0L
                engine.streamReply(text, context).collect { delta ->
                    if (truncated) return@collect
                    builder.append(delta)
                    if (builder.length > MAX_REPLY_CHARS) {
                        truncated = true
                        builder.setLength(MAX_REPLY_CHARS)
                    }
                    val now = android.os.SystemClock.uptimeMillis()
                    if (truncated || now - lastPush >= STREAM_UI_THROTTLE_MS) {
                        lastPush = now
                        val partial = builder.toString()
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { msg ->
                                    if (msg.id == modelMsgId) msg.copy(text = partial) else msg
                                }
                            )
                        }
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == modelMsgId) {
                                val finalText = builder.toString()
                                    .ifBlank { "I couldn't generate a reply." }
                                msg.copy(
                                    text = if (truncated) "$finalText… (reply truncated)"
                                    else finalText,
                                    streaming = false
                                )
                            } else msg
                        },
                        isGenerating = false
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == modelMsgId) {
                                msg.copy(
                                    text = "Something went wrong: ${t.message ?: t.toString()}",
                                    streaming = false
                                )
                            } else msg
                        },
                        isGenerating = false
                    )
                }
            }
        }
    }

    /** Starts the one-time ~2.5 GB model download. */
    fun startDownload() {
        if (_fetchState.value is ModelFetchState.Downloading ||
            _fetchState.value is ModelFetchState.Importing
        ) return
        downloadCancelled = false
        viewModelScope.launch {
            var failed: ModelFetchState.Failed? = null
            downloader.download(
                LlmModelFiles.MODEL_DOWNLOAD_URL,
                LlmModelFiles.modelFile(appContext)
            ).collect { progress ->
                if (progress is ModelFetchState.Failed && downloadCancelled) {
                    _fetchState.value = ModelFetchState.Idle
                } else {
                    _fetchState.value = progress
                    if (progress is ModelFetchState.Failed) failed = progress
                }
            }
            // The flow returns silently on success: verify the file landed.
            if (failed == null && !downloadCancelled &&
                LlmModelFiles.modelFile(appContext).exists()
            ) {
                _fetchState.value = ModelFetchState.Idle
                activateRealEngine()
            }
            downloadCancelled = false
        }
    }

    fun cancelDownload() {
        downloadCancelled = true
        downloader.cancel()
        _fetchState.value = ModelFetchState.Idle
    }

    /** Copies a user-picked `.litertlm` file into app storage. */
    fun importModel(uri: Uri) {
        if (_fetchState.value is ModelFetchState.Downloading ||
            _fetchState.value is ModelFetchState.Importing
        ) return
        viewModelScope.launch(Dispatchers.IO) {
            _fetchState.value = ModelFetchState.Importing
            try {
                downloader.importFrom(uri, LlmModelFiles.modelFile(appContext))
                _fetchState.value = ModelFetchState.Idle
                withContext(Dispatchers.Main) { activateRealEngine() }
            } catch (t: Throwable) {
                _fetchState.value = ModelFetchState.Failed(t.message ?: t.toString())
            }
        }
    }

    fun dismissFetchError() {
        if (_fetchState.value is ModelFetchState.Failed) {
            _fetchState.value = ModelFetchState.Idle
        }
    }

    /** Copies the model to Downloads/Massrofati so it survives reinstalls. */
    fun exportModelToDownloads() {
        if (_exportState.value is ModelExportState.Copying) return
        viewModelScope.launch(Dispatchers.IO) {
            downloader.exportToSharedDownloads(LlmModelFiles.modelFile(appContext))
                .collect { _exportState.value = it }
        }
    }

    fun dismissExportState() {
        _exportState.value = ModelExportState.Idle
    }

    private fun observeEngine(chatEngine: ChatEngine) {
        engineCollectors?.cancel()
        engineCollectors = viewModelScope.launch {
            launch {
                chatEngine.state.collect { state ->
                    _uiState.update { it.copy(engineState = state) }
                }
            }
            launch {
                chatEngine.status.collect { status ->
                    _uiState.update { it.copy(engineStatus = status) }
                }
            }
        }
    }

    private fun activateRealEngine() {
        viewModelScope.launch {
            engine.close()
            // Reuse the shared App engine so chat + AI parsing load the model once.
            val real = (appContext as? com.banksms.expensetracker.BankSmsApp)?.refreshEngine()
                ?: LlmEngines.get(appContext)
            engine = real
            _uiState.update { it.copy(isDemo = real.isDemo) }
            observeEngine(real)
            real.ensureLoaded()
        }
    }

    override fun onCleared() {
        engine.close()
    }

    class Factory(
        private val application: Application,
        private val repository: TransactionRepository,
        private val engine: ChatEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(application, repository, engine) as T
        }
    }
}

package com.banksms.expensetracker.data.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Lifecycle of the on-device chat engine. */
enum class EngineState { IDLE, LOADING, READY, ERROR }

/**
 * Smallest useful seam for the assistant: stream text deltas for a prompt.
 * [financeContext] is the retrieval-style snapshot (real repo data rendered
 * as text) so even small local models can answer money questions.
 */
interface ChatEngine {
    val state: StateFlow<EngineState>
    val status: StateFlow<String?>
    val isDemo: Boolean
    suspend fun ensureLoaded()
    fun streamReply(userText: String, financeContext: String): Flow<String>
    /**
     * One-shot blocking generation for background tasks (e.g. SMS parsing).
     * The prompt must be self-contained; implementations must not leak it
     * into any chat conversation history.
     */
    suspend fun generateText(prompt: String): String
    fun close()
}

/** Concatenates the text parts of a streamed message (one delta per chunk). */
private fun Message.textDelta(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

/** Where the `.litertlm` model lives once the user downloads it. */
object LlmModelFiles {
    const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"

    /** HuggingFace repo hosting the file below. */
    const val MODEL_HF_REPO = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"

    /** Direct download URL (~2.5 GB). Gated repos may reject anonymous download. */
    const val MODEL_DOWNLOAD_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

    /**
     * App-specific external storage: readable by DownloadManager (a system
     * process, which CANNOT write to app-private filesDir) and by the
     * LiteRT-LM engine via plain path. No storage permission needed for
     * our own directory. Falls back to filesDir if external is unavailable.
     */
    fun modelFile(context: Context): File {
        val base = context.getExternalFilesDir("llm") ?: File(context.filesDir, "llm")
        return File(base, MODEL_FILE_NAME)
    }
}

/** Picks the real engine when a model file exists, otherwise the demo stub. */
object LlmEngines {
    fun get(context: Context): ChatEngine {
        val app = context.applicationContext
        return if (LlmModelFiles.modelFile(app).exists()) LiteRtLmChatEngine(app)
        else FakeChatEngine()
    }
}

/**
 * Demo engine used until a `.litertlm` model is downloaded. Streams a canned
 * reply that still references the REAL finance snapshot so the UI, ViewModel
 * and retrieval plumbing can be verified without a multi-GB download.
 */
class FakeChatEngine : ChatEngine {
    override val isDemo = true
    private val _state = MutableStateFlow(EngineState.IDLE)
    override val state: StateFlow<EngineState> = _state.asStateFlow()
    private val _status = MutableStateFlow<String?>("Demo mode — no model file yet")
    override val status: StateFlow<String?> = _status.asStateFlow()

    override suspend fun ensureLoaded() {
        delay(400)
        _state.value = EngineState.READY
    }

    override suspend fun generateText(prompt: String): String {
        // Demo engine never parses: an empty result set is the safe answer.
        return "[]"
    }

    override fun streamReply(userText: String, financeContext: String): Flow<String> = flow {
        val reply = buildString {
            append("Here's what I found. ")
            append(financeContext)
            append(" Ask me about a category or a bank and I'll break it down. ")
            append("(Demo answer — download a .litertlm model for live on-device replies.)")
        }
        // Word-by-word emission mimics token streaming for the UI.
        reply.split(" ").forEachIndexed { index, word ->
            emit(if (index == 0) word else " $word")
            delay(28)
        }
    }.flowOn(Dispatchers.Default)

    override fun close() {}
}

/**
 * Real on-device engine (LiteRT-LM Kotlin API). Created only when
 * [LlmModelFiles.modelFile] exists; otherwise [FakeChatEngine] is used.
 */
class LiteRtLmChatEngine(private val appContext: Context) : ChatEngine {
    override val isDemo = false
    private val _state = MutableStateFlow(EngineState.IDLE)
    override val state: StateFlow<EngineState> = _state.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    override val status: StateFlow<String?> = _status.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    /** Serializes native init: two concurrent Engine() initializations can crash the process. */
    private val initMutex = Mutex()
    /**
     * Caps concurrent inference per Engine instance. Same-engine generations
     * are serialized natively anyway; this just bounds re-entrant use (chat
     * streaming vs single-SMS parsing sharing BankSmsApp.llmEngine).
     * Bulk scans use one private engine per batch instead.
     */
    private val genPermits = Semaphore(
        com.banksms.expensetracker.data.parser.AiSmsParser.PARALLEL_ENGINES
    )

    override suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (engine != null) {
                _state.value = EngineState.READY
                return@withContext
            }
            _state.value = EngineState.LOADING
            _status.value = null
            try {
                val modelFile = LlmModelFiles.modelFile(appContext)
                check(modelFile.exists()) {
                    "Model file missing: ${modelFile.absolutePath} " +
                        "(see ${LlmModelFiles.MODEL_HF_REPO})"
                }
                val eng = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = appContext.cacheDir.path
                    )
                )
                eng.initialize() // Slow (~seconds): already on Dispatchers.IO.
                engine = eng
                _state.value = EngineState.READY
            } catch (t: Throwable) {
                _status.value = t.message ?: t.toString()
                _state.value = EngineState.ERROR
            }
        }
    }

    override fun streamReply(userText: String, financeContext: String): Flow<String> = flow {
        val eng = engine ?: throw IllegalStateException("Engine not loaded")
        val conv = conversation ?: eng.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "You are Massrofati, the built-in assistant of the Massrofati " +
                        "expense-tracker app. You HAVE the user's spending data " +
                        "below — never claim you lack access to it.\n" +
                        "SCOPE: answer ONLY questions about the user's spending, income and " +
                        "transactions, personal-finance help based on that data, and how to " +
                        "use the Massrofati app (tracking, banks, categories, reports, " +
                        "budgets). For ANYTHING else (general knowledge, coding, news, " +
                        "homework, jokes, other apps...), refuse briefly: in English reply " +
                        "\"I can only help with your expenses and using Massrofati.\"; " +
                        "in Arabic reply \"أقدر أساعدك فقط في مصاريفك واستخدام مصروفاتي.\"\n" +
                        "GROUNDING: filter and sum using ONLY the history below. Never invent " +
                        "transactions, merchants or amounts. If nothing matches, say so " +
                        "plainly. Quote amounts exactly as given, with currency.\n" +
                        "LANGUAGE: reply in the user's language (Arabic or English).\n" +
                        "FORMAT: every answer in Markdown — start with a short ## heading, " +
                        "put key amounts in **bold**, list transactions as - bullets " +
                        "(one line each: date — merchant — **amount**), never use tables, " +
                        "keep it short.\n" +
                        "Current snapshot: $financeContext"
                ),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
            )
        ).also { conversation = it }
        // Blocking inference on Dispatchers.IO. The async Flow variant
        // (sendMessageAsync) cannot be used: litertlm 0.15–0.17 bytecode calls
        // SendChannel.close$default as a static on the SendChannel interface
        // (-Xjvm-default=all style), but stock kotlinx-coroutines only has it
        // on SendChannel$DefaultImpls → NoSuchMethodError when generation
        // completes. The UI still types the reply out word by word below.
        // genPermits: background parsing may share this Engine instance.
        val fullReply = genPermits.withPermit { conv.sendMessage(userText).textDelta() }
        fullReply.split(" ").forEachIndexed { index, word ->
            emit(if (index == 0) word else " $word")
            delay(30)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateText(prompt: String): String = withContext(Dispatchers.IO) {
        val eng = engine ?: throw IllegalStateException("Engine not loaded")
        // Fresh throwaway conversation per call: the chat conversation keeps
        // the finance snapshot, and reusing one conversation for hundreds of
        // parse batches would blow the 4k context window. Same blocking call
        // as chat (see NoSuchMethodError note above), same genMutex.
        val conv = eng.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    com.banksms.expensetracker.data.parser.AiSmsParser.SYSTEM_INSTRUCTION
                ),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)
            )
        )
        try {
            genPermits.withPermit { conv.sendMessage(prompt).textDelta() }
        } finally {
            try {
                conv.close()
            } catch (_: Throwable) {
            }
        }
    }

    override fun close() {
        try {
            conversation?.close()
        } catch (_: Throwable) {
        }
        conversation = null
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
    }
}

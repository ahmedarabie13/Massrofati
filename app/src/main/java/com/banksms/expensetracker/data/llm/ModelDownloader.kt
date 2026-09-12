package com.banksms.expensetracker.data.llm

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** UI-facing state of the one-time model fetch. */
sealed interface ModelFetchState {
    data object Idle : ModelFetchState
    data class Downloading(
        val fraction: Float,
        val downloadedMb: Long,
        val totalMb: Long
    ) : ModelFetchState

    data object Importing : ModelFetchState
    data class Failed(val reason: String) : ModelFetchState
}

/** UI-facing state of the model backup copy to shared storage. */
sealed interface ModelExportState {
    data object Idle : ModelExportState
    data class Copying(val fraction: Float, val copiedMb: Long) : ModelExportState
    data class Done(val location: String) : ModelExportState
    data class Failed(val reason: String) : ModelExportState
}

/**
 * Fetches the `.litertlm` model into app-private storage, two ways:
 * - [download]: system DownloadManager (survives process death, shows a
 *   notification). Fails gracefully on gated repos that reject anonymous
 *   download — the user can then use [importFrom] instead.
 * - [importFrom]: copies a user-picked file (Storage Access Framework, no
 *   storage permission needed). This covers reusing a file the user already
 *   downloaded elsewhere, since AI Edge Gallery's private copy is not
 *   readable by other apps.
 */
class ModelDownloader(private val appContext: Context) {

    private var activeDownloadId: Long = -1L

    fun cancel() {
        if (activeDownloadId != -1L) {
            appContext.getSystemService(DownloadManager::class.java)?.remove(activeDownloadId)
            activeDownloadId = -1L
        }
    }

    fun download(url: String, dest: File): Flow<ModelFetchState> = flow {
        try {
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            val dm = appContext.getSystemService(DownloadManager::class.java)
                ?: throw IllegalStateException("DownloadManager unavailable")
            val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Massrofati assistant model")
            .setDescription(LlmModelFiles.MODEL_FILE_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(dest))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = dm.enqueue(request)
        activeDownloadId = id
        try {
            while (true) {
                val query = DownloadManager.Query().setFilterById(id)
                dm.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        emit(ModelFetchState.Failed("Download vanished from the queue."))
                        return@flow
                    }
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return@flow
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            )
                            dest.delete()
                            emit(ModelFetchState.Failed(describeFailure(reason)))
                            return@flow
                        }
                        else -> {
                            val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                            emit(
                                ModelFetchState.Downloading(
                                    fraction = fraction,
                                    downloadedMb = downloaded / (1024 * 1024),
                                    totalMb = if (total > 0) total / (1024 * 1024) else -1L
                                )
                            )
                        }
                    }
                }
                delay(600)
            }
            } finally {
                activeDownloadId = -1L
            }
        } catch (t: Throwable) {
            // Never crash the app on download setup/polling errors (e.g. bad
            // destination, DownloadManager quirks): surface as a card instead.
            dest.delete()
            emit(ModelFetchState.Failed(t.message ?: t.toString()))
        }
    }.flowOn(Dispatchers.IO)

    /** Copies a SAF-picked file into app storage. Reports bytes via [onBytes]. */
    suspend fun importFrom(uri: Uri, dest: File, onBytes: (Long) -> Unit = {}) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        if (tmp.exists()) tmp.delete()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output ->
                val buffer = ByteArray(256 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    total += read
                    onBytes(total)
                }
                output.flush()
            }
        } ?: throw IllegalStateException("Could not open the selected file.")
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            throw IllegalStateException("Could not store the selected file.")
        }
    }

    private fun describeFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Server rejected the download (HTTP $reason). " +
            "This model may require login — use Import instead."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage unavailable."
        DownloadManager.ERROR_CANNOT_RESUME -> "Download interrupted and cannot resume. Retry."
        DownloadManager.ERROR_FILE_ERROR -> "File error while saving."
        else -> "Download failed (code $reason). Try Import instead."
    }

    /**
     * Copies the model to shared storage (`Downloads/Massrofati/`) so it
     * survives reinstalls. Uses MediaStore on Android 10+ (no permission
     * needed); legacy direct copy below that (needs storage permission).
     */
    fun exportToSharedDownloads(src: File): Flow<ModelExportState> = flow {
        if (!src.exists()) {
            emit(ModelExportState.Failed("Model file not found."))
            return@flow
        }
        try {
            val total = src.length()
            suspend fun report(copied: Long, @Suppress("UNUSED_PARAMETER") ignored: Long) {
                val fraction = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else 0f
                emit(ModelExportState.Copying(fraction, copied / (1024 * 1024)))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(src, ::report)
            } else {
                exportLegacy(src, ::report)
            }
            emit(ModelExportState.Done("Downloads/Massrofati/${src.name}"))
        } catch (t: Throwable) {
            emit(ModelExportState.Failed(t.message ?: t.toString()))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun exportViaMediaStore(
        src: File,
        onProgress: suspend (copied: Long, total: Long) -> Unit
    ) {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, src.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Massrofati"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the backup entry.")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                src.inputStream().use { input ->
                    copyWithProgress(input, output, src.length(), onProgress)
                }
            } ?: throw IllegalStateException("Could not open the backup entry.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun exportLegacy(
        src: File,
        onProgress: suspend (copied: Long, total: Long) -> Unit
    ) {
        val destDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Massrofati"
        )
        destDir.mkdirs()
        val dest = File(destDir, src.name)
        dest.outputStream().use { output ->
            src.inputStream().use { input ->
                copyWithProgress(input, output, src.length(), onProgress)
            }
        }
    }

    private suspend fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        total: Long,
        onProgress: suspend (copied: Long, total: Long) -> Unit
    ) {
        val buffer = ByteArray(256 * 1024)
        var copied = 0L
        var lastEmit = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            copied += read
            if (copied - lastEmit >= 8 * 1024 * 1024 || copied >= total) {
                lastEmit = copied
                onProgress(copied, total)
            }
        }
        output.flush()
        onProgress(copied, total)
    }
}

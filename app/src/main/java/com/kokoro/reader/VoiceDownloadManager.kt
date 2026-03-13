package com.kokoro.reader

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads Piper voice models from GitHub Releases to app internal storage.
 *
 * Voice models that are not bundled in the APK can be fetched on demand.
 * Downloaded files are stored in: filesDir/piper-models/{voiceId}.onnx
 *
 * Thread-safe: downloads run on caller-provided background threads.
 * Progress and completion are reported via callbacks on the download thread.
 */
object VoiceDownloadManager {

    private const val TAG = "VoiceDownloadManager"
    private const val PIPER_SUBDIR = "piper-models"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 60_000

    enum class State { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, ERROR }

    /** Currently downloading voice IDs (for UI state) */
    private val activeDownloads = mutableSetOf<String>()
    private val lock = Object()

    /** Callback: (voiceId, progress 0..100) — called on download thread */
    @Volatile var onProgress: ((String, Int) -> Unit)? = null

    /** Callback: (voiceId, success) — called on download thread */
    @Volatile var onComplete: ((String, Boolean) -> Unit)? = null

    // ── Path helpers ─────────────────────────────────────────────────────────

    fun getDownloadDir(ctx: Context): File =
        File(ctx.filesDir, PIPER_SUBDIR).also { it.mkdirs() }

    fun getVoiceFile(ctx: Context, voiceId: String): File =
        File(getDownloadDir(ctx), "$voiceId.onnx")

    /** Check if a voice model has been downloaded to internal storage */
    fun isDownloaded(ctx: Context, voiceId: String): Boolean =
        getVoiceFile(ctx, voiceId).exists()

    // ── State queries ────────────────────────────────────────────────────────

    fun getState(ctx: Context, voiceId: String): State {
        val voice = PiperVoiceCatalog.byId(voiceId) ?: return State.NOT_DOWNLOADED
        if (voice.bundled) return State.DOWNLOADED
        synchronized(lock) { if (voiceId in activeDownloads) return State.DOWNLOADING }
        if (isDownloaded(ctx, voiceId)) return State.DOWNLOADED
        return State.NOT_DOWNLOADED
    }

    fun isDownloading(voiceId: String): Boolean =
        synchronized(lock) { voiceId in activeDownloads }

    // ── Download ─────────────────────────────────────────────────────────────

    /**
     * Download a voice model in the background.
     * Must be called from a background thread (performs network I/O).
     * Returns true on success, false on failure.
     */
    fun download(ctx: Context, voiceId: String): Boolean {
        val voice = PiperVoiceCatalog.byId(voiceId)
        if (voice == null) {
            Log.e(TAG, "Voice not in catalog: $voiceId")
            return false
        }
        if (voice.bundled) {
            Log.d(TAG, "Voice $voiceId is bundled — no download needed")
            return true
        }
        if (isDownloaded(ctx, voiceId)) {
            Log.d(TAG, "Voice $voiceId already downloaded")
            return true
        }

        synchronized(lock) {
            if (voiceId in activeDownloads) {
                Log.d(TAG, "Voice $voiceId download already in progress")
                return false
            }
            activeDownloads.add(voiceId)
        }

        val url = PiperVoiceCatalog.downloadUrl(voiceId)
        val destFile = getVoiceFile(ctx, voiceId)
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")

        Log.i(TAG, "Downloading $voiceId from $url")

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Accept", "application/octet-stream")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP $responseCode for $voiceId")
                onComplete?.invoke(voiceId, false)
                return false
            }

            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            onProgress?.invoke(voiceId, progress.coerceIn(0, 100))
                        }
                    }
                }
            }

            // Atomic move: rename temp → final
            if (tempFile.renameTo(destFile)) {
                Log.i(TAG, "Downloaded $voiceId (${destFile.length() / 1024 / 1024}MB)")
                onComplete?.invoke(voiceId, true)
                return true
            } else {
                Log.e(TAG, "Failed to rename temp file for $voiceId")
                tempFile.delete()
                onComplete?.invoke(voiceId, false)
                return false
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Download failed for $voiceId", e)
            tempFile.delete()
            onComplete?.invoke(voiceId, false)
            return false
        } finally {
            synchronized(lock) { activeDownloads.remove(voiceId) }
        }
    }

    /** Delete a downloaded voice model to free space */
    fun delete(ctx: Context, voiceId: String): Boolean {
        val file = getVoiceFile(ctx, voiceId)
        return if (file.exists()) file.delete() else true
    }

    /** Total disk usage of downloaded voice models in bytes */
    fun downloadedSizeBytes(ctx: Context): Long {
        val dir = getDownloadDir(ctx)
        return dir.listFiles()?.filter { it.extension == "onnx" }?.sumOf { it.length() } ?: 0L
    }
}

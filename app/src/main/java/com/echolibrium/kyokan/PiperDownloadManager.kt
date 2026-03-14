package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Downloads and manages individual Piper TTS voice packages.
 *
 * Each voice is a tar.bz2 from k2-fsa/sherpa-onnx releases containing:
 *   model.onnx, tokens.txt, espeak-ng-data/
 *
 * Storage: filesDir/sherpa/piper/{voiceId}/
 *
 * Thread-safe: downloads run on background threads, state is volatile.
 */
object PiperDownloadManager {

    private const val TAG = "PiperDownload"

    enum class State { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    // Per-voice state tracking
    private val voiceStates = mutableMapOf<String, State>()
    private val voiceProgress = mutableMapOf<String, Int>()
    private val voiceErrors = mutableMapOf<String, String>()
    private val downloading = mutableSetOf<String>()

    @Volatile var onStateChange: ((voiceId: String, State) -> Unit)? = null
    @Volatile var onProgress: ((voiceId: String, Int) -> Unit)? = null

    // ── Paths ───────────────────────────────────────────────────────────────

    fun getPiperDir(ctx: Context): File =
        File(ctx.filesDir, "sherpa/piper").also { it.mkdirs() }

    fun getVoiceDir(ctx: Context, voiceId: String): File =
        File(getPiperDir(ctx), voiceId)

    // ── State queries ───────────────────────────────────────────────────────

    fun isVoiceReady(ctx: Context, voiceId: String): Boolean {
        val dir = getVoiceDir(ctx, voiceId)
        return dir.exists()
            && getModelFile(dir, voiceId) != null
            && File(dir, "tokens.txt").exists()
            && File(dir, "espeak-ng-data").exists()
    }

    /**
     * Find the .onnx model file inside a voice directory.
     * sherpa-onnx names it "{voiceId}.onnx" (e.g. en_US-lessac-medium.onnx).
     */
    fun getModelFile(voiceDir: File, voiceId: String): File? {
        val expected = File(voiceDir, "$voiceId.onnx")
        if (expected.exists()) return expected
        // Fallback: find any .onnx file that isn't a .json
        return voiceDir.listFiles()?.firstOrNull {
            it.extension == "onnx" && !it.name.endsWith(".onnx.json")
        }
    }

    fun isDownloading(voiceId: String): Boolean =
        synchronized(downloading) { voiceId in downloading }

    fun isAnyDownloading(): Boolean =
        synchronized(downloading) { downloading.isNotEmpty() }

    fun getState(ctx: Context, voiceId: String): State {
        if (isVoiceReady(ctx, voiceId)) return State.READY
        if (isDownloading(voiceId)) return State.DOWNLOADING
        return voiceStates[voiceId] ?: State.NOT_DOWNLOADED
    }

    fun getProgress(voiceId: String): Int =
        voiceProgress[voiceId] ?: 0

    fun getError(voiceId: String): String =
        voiceErrors[voiceId] ?: ""

    // ── Download ────────────────────────────────────────────────────────────

    fun downloadVoice(ctx: Context, voiceId: String) {
        synchronized(downloading) {
            if (voiceId in downloading) return
            downloading.add(voiceId)
        }

        if (isVoiceReady(ctx, voiceId)) {
            updateState(voiceId, State.READY)
            synchronized(downloading) { downloading.remove(voiceId) }
            return
        }

        updateState(voiceId, State.DOWNLOADING)
        voiceProgress[voiceId] = 0

        Thread {
            val tmpFile = File(getPiperDir(ctx), "$voiceId.tar.bz2.tmp")
            try {
                val url = PiperVoices.downloadUrl(voiceId)
                Log.d(TAG, "Downloading $voiceId from $url")

                DownloadUtil.download(url, tmpFile) { pct ->
                    voiceProgress[voiceId] = pct
                    onProgress?.invoke(voiceId, pct)
                }

                Log.d(TAG, "Extracting $voiceId")
                onProgress?.invoke(voiceId, -1) // signal extracting
                val archiveDir = PiperVoices.archiveDirName(voiceId)
                val targetDir = getVoiceDir(ctx, voiceId)
                targetDir.mkdirs()
                DownloadUtil.extractTarBz2(tmpFile, targetDir, archiveDir)

                tmpFile.delete()

                if (isVoiceReady(ctx, voiceId)) {
                    Log.d(TAG, "Voice $voiceId ready")
                    updateState(voiceId, State.READY)
                } else {
                    voiceErrors[voiceId] = "Extraction incomplete"
                    updateState(voiceId, State.ERROR)
                }
            } catch (e: Exception) {
                tmpFile.delete()
                voiceErrors[voiceId] = e.message ?: "Unknown error"
                Log.e(TAG, "Download failed for $voiceId", e)
                updateState(voiceId, State.ERROR)
            } finally {
                synchronized(downloading) { downloading.remove(voiceId) }
            }
        }.start()
    }

    fun deleteVoice(ctx: Context, voiceId: String) {
        getVoiceDir(ctx, voiceId).deleteRecursively()
        voiceStates.remove(voiceId)
        voiceProgress.remove(voiceId)
        voiceErrors.remove(voiceId)
        updateState(voiceId, State.NOT_DOWNLOADED)
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun updateState(voiceId: String, state: State) {
        voiceStates[voiceId] = state
        onStateChange?.invoke(voiceId, state)
    }

}

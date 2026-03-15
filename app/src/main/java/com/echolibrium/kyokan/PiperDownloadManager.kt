package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads and manages individual Piper TTS voice packages.
 *
 * All voices are downloaded as bundled vits-piper-*.tar.bz2 archives from our
 * GitHub release (model + tokens + espeak-ng-data included in each archive).
 *
 * Storage: filesDir/sherpa/piper/{voiceId}/
 *
 * Thread-safe: downloads run on background threads, state is volatile.
 */
class PiperDownloadManager {

    companion object {
        private const val TAG = "PiperDownload"
    }

    // Per-voice state tracking (ConcurrentHashMap for thread safety across UI + download threads)
    private val voiceStates = ConcurrentHashMap<String, DownloadState>()
    private val voiceProgress = ConcurrentHashMap<String, Int>()
    private val voiceErrors = ConcurrentHashMap<String, String>()
    private val downloading = mutableSetOf<String>()
    private val downloadThreads = ConcurrentHashMap<String, Thread>()

    private val stateListeners = mutableListOf<(voiceId: String, DownloadState) -> Unit>()
    private val progressListeners = mutableListOf<(voiceId: String, Int) -> Unit>()
    private val listenersLock = Object()

    fun addStateListener(l: (voiceId: String, DownloadState) -> Unit) {
        synchronized(listenersLock) { stateListeners.add(l) }
    }
    fun removeStateListener(l: (voiceId: String, DownloadState) -> Unit) {
        synchronized(listenersLock) { stateListeners.remove(l) }
    }
    fun addProgressListener(l: (voiceId: String, Int) -> Unit) {
        synchronized(listenersLock) { progressListeners.add(l) }
    }
    fun removeProgressListener(l: (voiceId: String, Int) -> Unit) {
        synchronized(listenersLock) { progressListeners.remove(l) }
    }

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

    fun getState(ctx: Context, voiceId: String): DownloadState {
        if (isVoiceReady(ctx, voiceId)) return DownloadState.READY
        if (isDownloading(voiceId)) return DownloadState.DOWNLOADING
        return voiceStates[voiceId] ?: DownloadState.NOT_DOWNLOADED
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
            updateState(voiceId, DownloadState.READY)
            synchronized(downloading) { downloading.remove(voiceId) }
            return
        }

        updateState(voiceId, DownloadState.DOWNLOADING)
        voiceProgress[voiceId] = 0

        val thread = Thread {
            try {
                downloadBundled(ctx, voiceId)

                if (isVoiceReady(ctx, voiceId)) {
                    Log.d(TAG, "Voice $voiceId ready")
                    updateState(voiceId, DownloadState.READY)
                } else {
                    voiceErrors[voiceId] = "Download incomplete — missing required files"
                    updateState(voiceId, DownloadState.ERROR)
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Download cancelled for $voiceId")
                updateState(voiceId, DownloadState.NOT_DOWNLOADED)
            } catch (e: Exception) {
                voiceErrors[voiceId] = e.message ?: "Unknown error"
                Log.e(TAG, "Download failed for $voiceId", e)
                updateState(voiceId, DownloadState.ERROR)
            } finally {
                synchronized(downloading) { downloading.remove(voiceId) }
                downloadThreads.remove(voiceId)
            }
        }
        downloadThreads[voiceId] = thread
        thread.start()
    }

    /** L16: Cancel an in-progress download for a specific voice. */
    fun cancelDownload(voiceId: String) {
        downloadThreads[voiceId]?.interrupt()
        downloadThreads.remove(voiceId)
    }

    /** Download a pre-built vits-piper bundle (tar.bz2 with everything inside) */
    private fun downloadBundled(ctx: Context, voiceId: String) {
        val tmpFile = File(getPiperDir(ctx), "$voiceId.tar.bz2.tmp")
        try {
            val url = PiperVoices.bundleUrl(voiceId)
            Log.d(TAG, "Downloading bundle $voiceId from $url")

            DownloadUtil.download(url, tmpFile) { pct ->
                voiceProgress[voiceId] = pct
                notifyProgress(voiceId, pct)
            }

            Log.d(TAG, "Extracting $voiceId")
            notifyProgress(voiceId, -1)
            val archiveDir = PiperVoices.archiveDirName(voiceId)
            val targetDir = getVoiceDir(ctx, voiceId)
            targetDir.mkdirs()
            DownloadUtil.extractTarBz2(tmpFile, targetDir, archiveDir)
        } finally {
            tmpFile.delete()
        }
    }


    fun deleteVoice(ctx: Context, voiceId: String) {
        getVoiceDir(ctx, voiceId).deleteRecursively()
        voiceStates.remove(voiceId)
        voiceProgress.remove(voiceId)
        voiceErrors.remove(voiceId)
        updateState(voiceId, DownloadState.NOT_DOWNLOADED)
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun updateState(voiceId: String, state: DownloadState) {
        voiceStates[voiceId] = state
        synchronized(listenersLock) { stateListeners.toList() }.forEach { it(voiceId, state) }
    }

    private fun notifyProgress(voiceId: String, pct: Int) {
        synchronized(listenersLock) { progressListeners.toList() }.forEach { it(voiceId, pct) }
    }

}

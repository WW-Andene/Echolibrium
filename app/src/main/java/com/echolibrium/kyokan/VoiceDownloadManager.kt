package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Downloads and extracts the Kokoro TTS model.
 *
 * Model: kokoro-en-v0_19 (int8 quantized, ~120MB download)
 * After extraction, the model directory contains:
 *   model.onnx, voices.bin, tokens.txt, espeak-ng-data/
 *
 * Stored in: context.filesDir/sherpa/kokoro-en-v0_19/
 */
class VoiceDownloadManager {

    companion object {
        private const val TAG = "VoiceDownload"
        const val MODEL_NAME   = "kokoro-en-v0_19"
        const val DOWNLOAD_URL = "https://github.com/WW-Andene/Echolibrium/releases/download/tts-assets-v1/kokoro-en-v0_19.tar.bz2"
        const val MODEL_SIZE_MB = 120  // approximate, for display
    }

    @Volatile var state: DownloadState = DownloadState.NOT_DOWNLOADED
    @Volatile var progressPercent: Int = 0
    @Volatile var errorMessage: String = ""

    private val stateListeners = mutableListOf<(DownloadState) -> Unit>()
    private val progressListeners = mutableListOf<(Int) -> Unit>()
    private val listenersLock = Object()

    fun addStateListener(l: (DownloadState) -> Unit) {
        synchronized(listenersLock) { stateListeners.add(l) }
    }
    fun removeStateListener(l: (DownloadState) -> Unit) {
        synchronized(listenersLock) { stateListeners.remove(l) }
    }
    fun addProgressListener(l: (Int) -> Unit) {
        synchronized(listenersLock) { progressListeners.add(l) }
    }
    fun removeProgressListener(l: (Int) -> Unit) {
        synchronized(listenersLock) { progressListeners.remove(l) }
    }

    // Backward-compat setters — used nowhere internally, kept for external callers
    var onProgress: ((Int) -> Unit)?
        get() = null
        set(value) { if (value != null) synchronized(listenersLock) { progressListeners.clear(); progressListeners.add(value) } }
    var onStateChange: ((DownloadState) -> Unit)?
        get() = null
        set(value) { if (value != null) synchronized(listenersLock) { stateListeners.clear(); stateListeners.add(value) } }

    // ── Paths ─────────────────────────────────────────────────────────────────

    fun getSherpaDir(ctx: Context): File = File(ctx.filesDir, "sherpa").also { it.mkdirs() }
    fun getModelDir(ctx: Context): File = File(getSherpaDir(ctx), MODEL_NAME)

    fun isModelReady(ctx: Context): Boolean {
        val dir = getModelDir(ctx)
        return dir.exists()
            && File(dir, "model.onnx").exists()
            && File(dir, "voices.bin").exists()
            && File(dir, "tokens.txt").exists()
            && File(dir, "espeak-ng-data").exists()
    }

    // ── Download ──────────────────────────────────────────────────────────────

    fun downloadModel(ctx: Context) {
        if (state == DownloadState.DOWNLOADING) return
        if (isModelReady(ctx)) { updateState(DownloadState.READY); return }

        updateState(DownloadState.DOWNLOADING)
        progressPercent = 0

        Thread {
            val tmpFile = File(getSherpaDir(ctx), "download.tar.bz2.tmp")
            try {
                Log.d(TAG, "Downloading from $DOWNLOAD_URL")
                DownloadUtil.download(DOWNLOAD_URL, tmpFile) { pct ->
                    progressPercent = pct
                    notifyProgress(pct)
                }

                Log.d(TAG, "Extracting to ${getSherpaDir(ctx)}")
                notifyProgress(-1)  // signal "extracting"
                DownloadUtil.extractTarBz2(tmpFile, getSherpaDir(ctx))

                tmpFile.delete()

                if (isModelReady(ctx)) {
                    Log.d(TAG, "Model ready at ${getModelDir(ctx)}")
                    updateState(DownloadState.READY)
                } else {
                    errorMessage = "Extraction incomplete — missing required files"
                    updateState(DownloadState.ERROR)
                }

            } catch (e: Exception) {
                tmpFile.delete()
                errorMessage = e.message ?: "Unknown error"
                Log.e(TAG, "Download failed", e)
                updateState(DownloadState.ERROR)
            }
        }.start()
    }

    fun deleteModel(ctx: Context) {
        getModelDir(ctx).deleteRecursively()
        updateState(DownloadState.NOT_DOWNLOADED)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun updateState(s: DownloadState) {
        state = s
        synchronized(listenersLock) { stateListeners.toList() }.forEach { it(s) }
    }

    private fun notifyProgress(pct: Int) {
        synchronized(listenersLock) { progressListeners.toList() }.forEach { it(pct) }
    }

}

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
object VoiceDownloadManager {

    private const val TAG = "VoiceDownload"

    // Model package — int8 is ~120MB vs ~400MB for float32
    const val MODEL_NAME   = "kokoro-en-v0_19"
    const val DOWNLOAD_URL = "https://github.com/WW-Andene/Echolibrium/releases/download/tts-assets-v1/kokoro-en-v0_19.tar.bz2"
    const val MODEL_SIZE_MB = 120  // approximate, for display

    @Volatile var state: DownloadState = DownloadState.NOT_DOWNLOADED
    @Volatile var progressPercent: Int = 0
    @Volatile var errorMessage: String = ""

    // Standardized callback properties matching PiperDownloadManager (H3)
    @Volatile var onProgress: ((Int) -> Unit)? = null
    @Volatile var onStateChange: ((DownloadState) -> Unit)? = null

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
                    onProgress?.invoke(pct)
                }

                Log.d(TAG, "Extracting to ${getSherpaDir(ctx)}")
                onProgress?.invoke(-1)  // signal "extracting"
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
        onStateChange?.invoke(s)
    }

}

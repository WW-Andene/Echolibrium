package com.kokoro.reader

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages the Kokoro TTS model bundled in app assets.
 *
 * The model is pre-packaged at build time in assets/kokoro-model/
 * and extracted to context.filesDir/sherpa/kokoro-multi-lang-v1_0/ on first launch.
 * No internet connection is required — the APK is fully autonomous.
 *
 * Model files after extraction:
 *   model.onnx, voices.bin, tokens.txt, espeak-ng-data/
 */
object VoiceDownloadManager {

    private const val TAG = "ModelManager"

    const val MODEL_NAME    = "kokoro-multi-lang-v1_0"
    private const val ASSET_DIR = "kokoro-model"

    enum class State { NOT_EXTRACTED, EXTRACTING, READY, ERROR }

    @Volatile var state: State = State.NOT_EXTRACTED
    @Volatile var progressPercent: Int = 0
    @Volatile var errorMessage: String = ""

    @Volatile private var progressCallback: ((Int) -> Unit)? = null
    @Volatile private var stateCallback: ((State) -> Unit)? = null

    fun onProgress(cb: (Int) -> Unit) { progressCallback = cb }
    fun onStateChange(cb: (State) -> Unit) { stateCallback = cb }

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

    // ── Extract from assets ───────────────────────────────────────────────────

    /**
     * Extracts the bundled model from assets to internal storage.
     * Safe to call multiple times — skips if already extracted.
     * Runs on a background thread and reports progress via callbacks.
     */
    fun ensureModel(ctx: Context) {
        if (state == State.EXTRACTING) return
        if (isModelReady(ctx)) { updateState(State.READY); return }

        updateState(State.EXTRACTING)
        progressPercent = 0

        Thread {
            try {
                Log.d(TAG, "Extracting model from assets/$ASSET_DIR")
                val destDir = getModelDir(ctx)
                destDir.mkdirs()

                copyAssetsRecursive(ctx, ASSET_DIR, destDir)

                if (isModelReady(ctx)) {
                    Log.d(TAG, "Model ready at $destDir")
                    updateState(State.READY)
                } else {
                    updateState(State.ERROR)
                    errorMessage = "Extraction incomplete — missing required files"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unknown error"
                Log.e(TAG, "Asset extraction failed", e)
                updateState(State.ERROR)
            }
        }.start()
    }

    /**
     * Synchronous version — blocks until model is extracted.
     * Use from background threads only.
     */
    fun ensureModelSync(ctx: Context): Boolean {
        if (isModelReady(ctx)) { updateState(State.READY); return true }
        return try {
            val destDir = getModelDir(ctx)
            destDir.mkdirs()
            copyAssetsRecursive(ctx, ASSET_DIR, destDir)
            val ready = isModelReady(ctx)
            updateState(if (ready) State.READY else State.ERROR)
            ready
        } catch (e: Exception) {
            Log.e(TAG, "Asset extraction failed", e)
            updateState(State.ERROR)
            false
        }
    }

    fun deleteModel(ctx: Context) {
        getModelDir(ctx).deleteRecursively()
        updateState(State.NOT_EXTRACTED)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun updateState(s: State) {
        state = s
        stateCallback?.invoke(s)
    }

    private fun copyAssetsRecursive(ctx: Context, assetPath: String, dest: File) {
        val assetManager = ctx.assets
        val entries = assetManager.list(assetPath) ?: return

        if (entries.isEmpty()) {
            // It's a file — copy it to dest
            dest.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            }
        } else {
            // It's a directory — recurse into each child
            dest.mkdirs()
            for (entry in entries) {
                copyAssetsRecursive(ctx, "$assetPath/$entry", File(dest, entry))
            }
        }
    }
}

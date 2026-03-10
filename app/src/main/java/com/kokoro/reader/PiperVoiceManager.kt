package com.kokoro.reader

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages Piper TTS voice model files.
 *
 * All voices are bundled in the APK's assets/piper-models/ and extracted
 * on first launch. No internet connection is needed.
 *
 * Each Piper voice needs:
 *   - {voice_id}.onnx  (the model, voice-specific)
 *   - tokens.txt        (shared across all Piper voices)
 *   - espeak-ng-data/   (shared with Kokoro model)
 *
 * Stored in: context.filesDir/piper/
 */
object PiperVoiceManager {

    private const val TAG = "PiperVoiceManager"
    private const val ASSET_DIR = "piper-models"
    private const val PIPER_DIR = "piper"

    enum class VoiceState { NOT_AVAILABLE, READY, ERROR }

    /** Callback for UI refresh after extraction completes */
    @Volatile var downloadCallback: ((String, VoiceState) -> Unit)? = null

    /** All voices are bundled in the APK — always returns true for catalog voices */
    fun isBundled(voiceId: String): Boolean = true

    // ── Paths ─────────────────────────────────────────────────────────────────

    fun getPiperDir(ctx: Context): File = File(ctx.filesDir, PIPER_DIR).also { it.mkdirs() }

    fun getTokensPath(ctx: Context): String =
        File(getPiperDir(ctx), "tokens.txt").absolutePath

    fun getModelPath(ctx: Context, voiceId: String): String =
        File(getPiperDir(ctx), "$voiceId.onnx").absolutePath

    /** Reuse espeak-ng-data from the Kokoro model (already extracted) */
    fun getEspeakDataDir(ctx: Context): String =
        File(VoiceDownloadManager.getModelDir(ctx), "espeak-ng-data").absolutePath

    // ── State queries ─────────────────────────────────────────────────────────

    fun getConfigPath(ctx: Context, voiceId: String): String =
        File(getPiperDir(ctx), "$voiceId.onnx.json").absolutePath

    fun isVoiceReady(ctx: Context, voiceId: String): Boolean {
        val modelFile = File(getPiperDir(ctx), "$voiceId.onnx")
        val tokensFile = File(getPiperDir(ctx), "tokens.txt")
        val espeakDir = File(VoiceDownloadManager.getModelDir(ctx), "espeak-ng-data")
        return modelFile.exists() && tokensFile.exists() && espeakDir.exists()
    }

    fun getVoiceState(ctx: Context, voiceId: String): VoiceState {
        if (isVoiceReady(ctx, voiceId)) return VoiceState.READY
        return VoiceState.NOT_AVAILABLE
    }

    /** All locally available Piper voice IDs */
    fun getAvailableVoiceIds(ctx: Context): Set<String> {
        val piperDir = getPiperDir(ctx)
        return piperDir.listFiles()
            ?.filter { it.name.endsWith(".onnx") }
            ?.map { it.nameWithoutExtension }
            ?.toSet() ?: emptySet()
    }

    // ── Extraction from bundled assets ─────────────────────────────────────────

    /**
     * Extract all bundled Piper voice files from assets to internal storage.
     * Safe to call multiple times — skips files that already exist.
     */
    fun extractBundledVoicesSync(ctx: Context) {
        try {
            val piperDir = getPiperDir(ctx)
            val assetManager = ctx.assets
            val entries = assetManager.list(ASSET_DIR) ?: return

            for (entry in entries) {
                val destFile = File(piperDir, entry)
                if (!destFile.exists() || destFile.length() == 0L) {
                    Log.d(TAG, "Extracting bundled Piper file: $entry")
                    assetManager.open("$ASSET_DIR/$entry").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 32 * 1024)
                        }
                    }
                }
            }
            Log.d(TAG, "Bundled Piper voices extracted to $piperDir")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract bundled Piper voices", e)
        }
    }
}

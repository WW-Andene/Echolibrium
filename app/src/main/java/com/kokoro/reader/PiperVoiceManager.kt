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
        val piperDir = getPiperDir(ctx)
        val modelFile = File(piperDir, "$voiceId.onnx")
        val tokensFile = File(piperDir, "tokens.txt")
        val espeakDir = File(VoiceDownloadManager.getModelDir(ctx), "espeak-ng-data")
        return modelFile.exists() && modelFile.length() > 0
            && tokensFile.exists() && espeakDir.exists()
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

    /** Tracks whether shared files (tokens.txt) have already been extracted this session */
    @Volatile private var sharedFilesExtracted = false

    /**
     * Extract a single Piper voice from assets on demand.
     * Also extracts the shared tokens.txt if not yet present.
     * Returns true if the voice is ready after extraction.
     */
    fun ensureVoiceExtracted(ctx: Context, voiceId: String): Boolean {
        if (isVoiceReady(ctx, voiceId)) return true
        return try {
            val piperDir = getPiperDir(ctx)
            val assetManager = ctx.assets

            // Extract shared tokens.txt (once per session)
            if (!sharedFilesExtracted) {
                val tokensFile = File(piperDir, "tokens.txt")
                if (!tokensFile.exists() || tokensFile.length() == 0L) {
                    extractAssetFile(assetManager, "$ASSET_DIR/tokens.txt", tokensFile)
                }
                sharedFilesExtracted = true
            }

            // Extract this specific voice model
            val modelFile = File(piperDir, "$voiceId.onnx")
            if (!modelFile.exists() || modelFile.length() == 0L) {
                Log.d(TAG, "Extracting Piper voice on demand: $voiceId")
                extractAssetFile(assetManager, "$ASSET_DIR/$voiceId.onnx", modelFile)
            }

            // Also extract .onnx.json config if present
            val configFile = File(piperDir, "$voiceId.onnx.json")
            if (!configFile.exists() || configFile.length() == 0L) {
                try {
                    extractAssetFile(assetManager, "$ASSET_DIR/$voiceId.onnx.json", configFile)
                } catch (_: Exception) { /* config is optional */ }
            }

            val ready = isVoiceReady(ctx, voiceId)
            if (ready) Log.d(TAG, "Piper voice ready: $voiceId")
            else Log.w(TAG, "Piper voice extraction incomplete: $voiceId")
            ready
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract Piper voice: $voiceId", e)
            false
        }
    }

    /**
     * Extract all bundled Piper voice files from assets to internal storage.
     * Safe to call multiple times — skips files that already exist.
     * Uses a marker file to avoid re-scanning assets on subsequent calls.
     */
    fun extractBundledVoicesSync(ctx: Context) {
        val piperDir = getPiperDir(ctx)
        // Fast path: marker confirms previous complete extraction
        if (File(piperDir, ".all_extracted").exists()) return
        try {
            val assetManager = ctx.assets
            val entries = assetManager.list(ASSET_DIR) ?: return

            for (entry in entries) {
                val destFile = File(piperDir, entry)
                if (!destFile.exists() || destFile.length() == 0L) {
                    Log.d(TAG, "Extracting bundled Piper file: $entry")
                    extractAssetFile(assetManager, "$ASSET_DIR/$entry", destFile)
                }
            }
            // Mark extraction complete so we never re-scan
            File(piperDir, ".all_extracted").createNewFile()
            sharedFilesExtracted = true
            Log.d(TAG, "Bundled Piper voices extracted to $piperDir")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract bundled Piper voices", e)
        }
    }

    private fun extractAssetFile(assetManager: android.content.res.AssetManager, assetPath: String, dest: File) {
        dest.parentFile?.mkdirs()
        assetManager.open(assetPath).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output, bufferSize = 32 * 1024)
            }
        }
    }
}

package com.kokoro.reader

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Piper TTS voice model files.
 *
 * Some voices are bundled in the APK's assets/piper-models/ and extracted
 * on first launch. Additional voices can be downloaded from HuggingFace.
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

    /** Voice IDs that are bundled in APK assets and extracted on first launch */
    val BUNDLED_VOICE_IDS = setOf(
        "en_US-lessac-medium",
        "en_US-ryan-medium",
        "fr_FR-siwis-medium"
    )

    enum class VoiceState { NOT_AVAILABLE, DOWNLOADING, READY, ERROR }

    private val voiceStates = mutableMapOf<String, VoiceState>()

    @Volatile var downloadCallback: ((String, VoiceState) -> Unit)? = null

    /** Check if a voice is bundled in the APK assets */
    fun isBundled(voiceId: String): Boolean = voiceId in BUNDLED_VOICE_IDS

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
        return voiceStates[voiceId] ?: VoiceState.NOT_AVAILABLE
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
                if (!destFile.exists()) {
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

    // ── Download on demand ────────────────────────────────────────────────────

    /**
     * Download a Piper voice model from its HuggingFace URL.
     * Runs on a background thread.
     */
    fun downloadVoice(ctx: Context, voice: PiperVoice) {
        if (voiceStates[voice.id] == VoiceState.DOWNLOADING) return
        if (isVoiceReady(ctx, voice.id)) {
            voiceStates[voice.id] = VoiceState.READY
            downloadCallback?.invoke(voice.id, VoiceState.READY)
            return
        }

        voiceStates[voice.id] = VoiceState.DOWNLOADING
        downloadCallback?.invoke(voice.id, VoiceState.DOWNLOADING)

        Thread {
            try {
                val piperDir = getPiperDir(ctx)
                val destFile = File(piperDir, "${voice.id}.onnx")

                Log.d(TAG, "Downloading ${voice.id} from ${voice.modelUrl}")
                downloadFile(voice.modelUrl, destFile)

                // Best-effort download of config file (not required by sherpa-onnx)
                val configFile = File(piperDir, "${voice.id}.onnx.json")
                if (!configFile.exists()) {
                    try {
                        downloadFile(voice.configUrl, configFile)
                    } catch (e: Exception) {
                        Log.w(TAG, "Config download failed for ${voice.id} (non-critical)", e)
                    }
                }

                val tokensFile = File(piperDir, "tokens.txt")
                if (!tokensFile.exists()) {
                    Log.w(TAG, "tokens.txt not found — Piper voice may not work without bundled data")
                }

                voiceStates[voice.id] = VoiceState.READY
                downloadCallback?.invoke(voice.id, VoiceState.READY)
                Log.d(TAG, "Downloaded Piper voice: ${voice.id}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to download ${voice.id}", e)
                voiceStates[voice.id] = VoiceState.ERROR
                downloadCallback?.invoke(voice.id, VoiceState.ERROR)
            }
        }.apply { name = "PiperDownload-${voice.id}"; isDaemon = true; start() }
    }

    private fun downloadFile(urlStr: String, dest: File) {
        // Write to a temp file first, then rename atomically.
        // This prevents corrupted partial files from appearing as "ready"
        // if the download is interrupted (network loss, app killed, etc.).
        val tempFile = File(dest.parentFile, "${dest.name}.tmp")
        try {
            val url = URL(urlStr)
            var conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.connect()

            // Follow redirects manually (HuggingFace / GitHub redirects)
            var redirects = 0
            while (conn.responseCode in 300..399 && redirects++ < 5) {
                val location = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = URL(location).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.connect()
            }

            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode} from $urlStr")
            }

            try {
                conn.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024)
                    }
                }
            } finally {
                conn.disconnect()
            }

            // Atomic rename — only makes the file visible once fully written
            if (!tempFile.renameTo(dest)) {
                // Fallback: copy then delete (renameTo can fail across filesystems)
                tempFile.copyTo(dest, overwrite = true)
                tempFile.delete()
            }
        } finally {
            // Clean up temp file if it still exists (e.g. download failed before rename)
            if (tempFile.exists()) tempFile.delete()
        }
    }
}

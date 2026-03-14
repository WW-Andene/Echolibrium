package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads and manages individual Piper TTS voice packages.
 *
 * Two download modes:
 *   • Bundled: voices with vits-piper-*.tar.bz2 archives (model + tokens + espeak-ng-data)
 *   • Individual: downloads raw .onnx + shared assets (tokens.txt, espeak-ng-data/)
 *
 * Shared assets are downloaded once and copied into each voice directory.
 *
 * Storage: filesDir/sherpa/piper/{voiceId}/
 *
 * Thread-safe: downloads run on background threads, state is volatile.
 */
object PiperDownloadManager {

    private const val TAG = "PiperDownload"

    // Per-voice state tracking (ConcurrentHashMap for thread safety across UI + download threads)
    private val voiceStates = ConcurrentHashMap<String, DownloadState>()
    private val voiceProgress = ConcurrentHashMap<String, Int>()
    private val voiceErrors = ConcurrentHashMap<String, String>()
    private val downloading = mutableSetOf<String>()

    @Volatile var onStateChange: ((voiceId: String, DownloadState) -> Unit)? = null
    @Volatile var onProgress: ((voiceId: String, Int) -> Unit)? = null

    // ── Paths ───────────────────────────────────────────────────────────────

    fun getPiperDir(ctx: Context): File =
        File(ctx.filesDir, "sherpa/piper").also { it.mkdirs() }

    fun getVoiceDir(ctx: Context, voiceId: String): File =
        File(getPiperDir(ctx), voiceId)

    private fun getSharedDir(ctx: Context): File =
        File(getPiperDir(ctx), "_shared").also { it.mkdirs() }

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

        Thread {
            try {
                if (PiperVoices.hasBundledArchive(voiceId)) {
                    downloadBundled(ctx, voiceId)
                } else {
                    downloadIndividual(ctx, voiceId)
                }

                if (isVoiceReady(ctx, voiceId)) {
                    Log.d(TAG, "Voice $voiceId ready")
                    updateState(voiceId, DownloadState.READY)
                } else {
                    voiceErrors[voiceId] = "Download incomplete — missing required files"
                    updateState(voiceId, DownloadState.ERROR)
                }
            } catch (e: Exception) {
                voiceErrors[voiceId] = e.message ?: "Unknown error"
                Log.e(TAG, "Download failed for $voiceId", e)
                updateState(voiceId, DownloadState.ERROR)
            } finally {
                synchronized(downloading) { downloading.remove(voiceId) }
            }
        }.start()
    }

    /** Download a pre-built vits-piper bundle (tar.bz2 with everything inside) */
    private fun downloadBundled(ctx: Context, voiceId: String) {
        val tmpFile = File(getPiperDir(ctx), "$voiceId.tar.bz2.tmp")
        try {
            val url = PiperVoices.bundleUrl(voiceId)
            Log.d(TAG, "Downloading bundle $voiceId from $url")

            DownloadUtil.download(url, tmpFile) { pct ->
                voiceProgress[voiceId] = pct
                onProgress?.invoke(voiceId, pct)
            }

            Log.d(TAG, "Extracting $voiceId")
            onProgress?.invoke(voiceId, -1)
            val archiveDir = PiperVoices.archiveDirName(voiceId)
            val targetDir = getVoiceDir(ctx, voiceId)
            targetDir.mkdirs()
            DownloadUtil.extractTarBz2(tmpFile, targetDir, archiveDir)
        } finally {
            tmpFile.delete()
        }
    }

    /**
     * Download raw .onnx + per-voice tokens.txt (from .onnx.json) + shared espeak-ng-data.
     *
     * Each Piper model has its own tokens.txt extracted from its .onnx.json config.
     * The espeak-ng-data/ directory is shared across all voices.
     */
    private fun downloadIndividual(ctx: Context, voiceId: String) {
        val voice = PiperVoices.byId(voiceId)
            ?: throw IllegalArgumentException("Unknown Piper voice: $voiceId")
        val targetDir = getVoiceDir(ctx, voiceId)
        targetDir.mkdirs()

        // Step 1: Download the .onnx model (0-75% of progress)
        val onnxFile = File(targetDir, "$voiceId.onnx")
        if (!onnxFile.exists()) {
            val tmpFile = File(targetDir, "$voiceId.onnx.tmp")
            try {
                val url = PiperVoices.onnxUrl(voice)
                Log.d(TAG, "Downloading model $voiceId from $url")

                DownloadUtil.download(url, tmpFile) { pct ->
                    val scaled = (pct * 75) / 100
                    voiceProgress[voiceId] = scaled
                    onProgress?.invoke(voiceId, scaled)
                }
                tmpFile.renameTo(onnxFile)
            } catch (e: Exception) {
                tmpFile.delete()
                throw e
            }
        }

        // Step 2: Download .onnx.json and extract tokens.txt from it (75-80%)
        val tokensFile = File(targetDir, "tokens.txt")
        if (!tokensFile.exists()) {
            voiceProgress[voiceId] = 76
            onProgress?.invoke(voiceId, 76)
            downloadTokensFromOnnxJson(ctx, voice, tokensFile)
        }

        // Step 3: Ensure shared espeak-ng-data exists, then copy into voice dir (80-95%)
        ensureSharedEspeakData(ctx, voiceId)

        val espeakDir = File(targetDir, "espeak-ng-data")
        if (!espeakDir.exists()) {
            voiceProgress[voiceId] = 96
            onProgress?.invoke(voiceId, 96)
            val sharedEspeak = File(getSharedDir(ctx), "espeak-ng-data")
            sharedEspeak.copyRecursively(espeakDir)
        }

        voiceProgress[voiceId] = 100
        onProgress?.invoke(voiceId, 100)
    }

    /**
     * Download the voice's .onnx.json config and extract phoneme_id_map as tokens.txt.
     * Piper's tokens.txt format: one token per line, "token idx".
     */
    private fun downloadTokensFromOnnxJson(ctx: Context, voice: PiperVoice, tokensFile: File) {
        val tmpFile = File(tokensFile.parentFile, "config.json.tmp")
        try {
            val url = PiperVoices.onnxJsonUrl(voice)
            Log.d(TAG, "Downloading config for ${voice.id} from $url")
            DownloadUtil.download(url, tmpFile) { _ -> }

            val json = JSONObject(tmpFile.readText())
            val phonemeMap = json.getJSONObject("phoneme_id_map")

            // Build tokens.txt: each line is "token idx"
            val entries = mutableListOf<Pair<String, Int>>()
            val keys = phonemeMap.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val arr = phonemeMap.getJSONArray(token)
                entries.add(Pair(token, arr.getInt(0)))
            }
            entries.sortBy { it.second }

            tokensFile.writeText(entries.joinToString("\n") { "${it.first} ${it.second}" } + "\n")
            Log.d(TAG, "Extracted ${entries.size} tokens for ${voice.id}")
        } finally {
            tmpFile.delete()
        }
    }

    /**
     * Download shared espeak-ng-data once to _shared/.
     * Thread-safe: synchronized so multiple concurrent downloads don't duplicate work.
     */
    @Synchronized
    private fun ensureSharedEspeakData(ctx: Context, voiceId: String) {
        val sharedDir = getSharedDir(ctx)
        val sharedEspeak = File(sharedDir, "espeak-ng-data")
        if (sharedEspeak.exists()) return

        val tmpFile = File(sharedDir, "espeak-ng-data.tar.bz2.tmp")
        try {
            Log.d(TAG, "Downloading shared espeak-ng-data")
            voiceProgress[voiceId] = 82
            onProgress?.invoke(voiceId, 82)
            DownloadUtil.download(PiperVoices.espeakDataUrl(), tmpFile) { pct ->
                val scaled = 82 + (pct * 12) / 100
                voiceProgress[voiceId] = scaled
                onProgress?.invoke(voiceId, scaled)
            }
            Log.d(TAG, "Extracting espeak-ng-data")
            onProgress?.invoke(voiceId, -1)
            DownloadUtil.extractTarBz2(tmpFile, sharedDir)
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
        onStateChange?.invoke(voiceId, state)
    }

}

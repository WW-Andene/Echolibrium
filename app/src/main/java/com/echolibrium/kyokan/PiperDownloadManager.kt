package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
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

    enum class State { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    // Per-voice state tracking (ConcurrentHashMap for thread safety across UI + download threads)
    private val voiceStates = ConcurrentHashMap<String, State>()
    private val voiceProgress = ConcurrentHashMap<String, Int>()
    private val voiceErrors = ConcurrentHashMap<String, String>()
    private val downloading = mutableSetOf<String>()

    @Volatile var onStateChange: ((voiceId: String, State) -> Unit)? = null
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
            try {
                if (PiperVoices.hasBundledArchive(voiceId)) {
                    downloadBundled(ctx, voiceId)
                } else {
                    downloadIndividual(ctx, voiceId)
                }

                if (isVoiceReady(ctx, voiceId)) {
                    Log.d(TAG, "Voice $voiceId ready")
                    updateState(voiceId, State.READY)
                } else {
                    voiceErrors[voiceId] = "Download incomplete — missing required files"
                    updateState(voiceId, State.ERROR)
                }
            } catch (e: Exception) {
                voiceErrors[voiceId] = e.message ?: "Unknown error"
                Log.e(TAG, "Download failed for $voiceId", e)
                updateState(voiceId, State.ERROR)
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

    /** Download raw .onnx + shared assets (tokens.txt, espeak-ng-data) */
    private fun downloadIndividual(ctx: Context, voiceId: String) {
        val targetDir = getVoiceDir(ctx, voiceId)
        targetDir.mkdirs()

        // Step 1: Download the .onnx model (~70-90% of progress)
        val onnxFile = File(targetDir, "$voiceId.onnx")
        if (!onnxFile.exists()) {
            val tmpFile = File(targetDir, "$voiceId.onnx.tmp")
            try {
                val url = PiperVoices.onnxUrl(voiceId)
                Log.d(TAG, "Downloading model $voiceId from $url")

                DownloadUtil.download(url, tmpFile) { pct ->
                    // Scale to 0-80% for the model download
                    val scaled = (pct * 80) / 100
                    voiceProgress[voiceId] = scaled
                    onProgress?.invoke(voiceId, scaled)
                }
                tmpFile.renameTo(onnxFile)
            } catch (e: Exception) {
                tmpFile.delete()
                throw e
            }
        }

        // Step 2: Ensure shared assets exist, then copy into voice dir
        ensureSharedAssets(ctx, voiceId)

        // Step 3: Copy tokens.txt into voice dir
        val tokensFile = File(targetDir, "tokens.txt")
        if (!tokensFile.exists()) {
            val sharedTokens = File(getSharedDir(ctx), "tokens.txt")
            sharedTokens.copyTo(tokensFile)
        }

        // Step 4: Copy espeak-ng-data/ into voice dir
        val espeakDir = File(targetDir, "espeak-ng-data")
        if (!espeakDir.exists()) {
            val sharedEspeak = File(getSharedDir(ctx), "espeak-ng-data")
            sharedEspeak.copyRecursively(espeakDir)
        }

        voiceProgress[voiceId] = 100
        onProgress?.invoke(voiceId, 100)
    }

    /**
     * Download shared assets (tokens.txt, espeak-ng-data) once to _shared/.
     * Thread-safe: synchronized so multiple concurrent downloads don't duplicate work.
     */
    @Synchronized
    private fun ensureSharedAssets(ctx: Context, voiceId: String) {
        val sharedDir = getSharedDir(ctx)

        // Download tokens.txt
        val sharedTokens = File(sharedDir, "tokens.txt")
        if (!sharedTokens.exists()) {
            val tmpFile = File(sharedDir, "tokens.txt.tmp")
            try {
                Log.d(TAG, "Downloading shared tokens.txt")
                voiceProgress[voiceId] = 82
                onProgress?.invoke(voiceId, 82)
                DownloadUtil.download(PiperVoices.tokensUrl(), tmpFile) { _ -> }
                tmpFile.renameTo(sharedTokens)
            } catch (e: Exception) {
                tmpFile.delete()
                throw e
            }
        }

        // Download and extract espeak-ng-data
        val sharedEspeak = File(sharedDir, "espeak-ng-data")
        if (!sharedEspeak.exists()) {
            val tmpFile = File(sharedDir, "espeak-ng-data.tar.bz2.tmp")
            try {
                Log.d(TAG, "Downloading shared espeak-ng-data")
                voiceProgress[voiceId] = 85
                onProgress?.invoke(voiceId, 85)
                DownloadUtil.download(PiperVoices.espeakDataUrl(), tmpFile) { pct ->
                    val scaled = 85 + (pct * 10) / 100
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

        voiceProgress[voiceId] = 96
        onProgress?.invoke(voiceId, 96)
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

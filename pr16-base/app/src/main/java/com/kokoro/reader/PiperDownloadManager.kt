package com.kokoro.reader

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Piper TTS voice downloads from the repo's tts-assets-v1 release.
 *
 * Downloads individual .onnx files per voice. Shared assets (tokens.txt,
 * espeak-ng-data/) are downloaded once and copied to each voice directory.
 *
 * Storage: filesDir/sherpa/piper/{voiceId}/
 *          filesDir/sherpa/piper/shared/   (tokens + espeak-ng-data)
 *
 * Thread-safe: downloads run on background threads.
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

    private fun getSharedDir(ctx: Context): File =
        File(getPiperDir(ctx), "shared").also { it.mkdirs() }

    // ── State queries ───────────────────────────────────────────────────────

    fun isVoiceReady(ctx: Context, voiceId: String): Boolean {
        val dir = getVoiceDir(ctx, voiceId)
        return dir.exists()
            && getModelFile(dir, voiceId) != null
            && File(dir, "tokens.txt").exists()
            && File(dir, "espeak-ng-data").isDirectory
    }

    /**
     * Find the .onnx model file inside a voice directory.
     */
    fun getModelFile(voiceDir: File, voiceId: String): File? {
        val expected = File(voiceDir, "$voiceId.onnx")
        if (expected.exists()) return expected
        return voiceDir.listFiles()?.firstOrNull {
            it.extension == "onnx" && !it.name.endsWith(".onnx.json")
        }
    }

    fun isDownloading(voiceId: String): Boolean =
        synchronized(downloading) { voiceId in downloading }

    fun getState(ctx: Context, voiceId: String): State {
        if (isVoiceReady(ctx, voiceId)) return State.READY
        if (isDownloading(voiceId)) return voiceStates[voiceId] ?: State.DOWNLOADING
        return voiceStates[voiceId] ?: State.NOT_DOWNLOADED
    }

    fun getProgress(voiceId: String): Int =
        voiceProgress[voiceId] ?: 0

    fun getError(voiceId: String): String =
        voiceErrors[voiceId] ?: ""

    // ── Download voice ──────────────────────────────────────────────────────

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
                // Step 1: Ensure shared assets exist (tokens + espeak-ng-data)
                ensureSharedAssets(ctx, voiceId)

                // Step 2: Download the voice .onnx file
                val voiceDir = getVoiceDir(ctx, voiceId)
                voiceDir.mkdirs()
                val onnxFile = File(voiceDir, "$voiceId.onnx")
                val tmpFile = File(voiceDir, "$voiceId.onnx.tmp")

                val url = PiperVoices.downloadUrl(voiceId)
                Log.d(TAG, "Downloading $voiceId from $url")
                download(url, tmpFile) { pct ->
                    voiceProgress[voiceId] = pct
                    onProgress?.invoke(voiceId, pct)
                }
                tmpFile.renameTo(onnxFile)

                // Step 3: Copy shared assets into voice directory
                onProgress?.invoke(voiceId, -1) // "setting up..."
                val sharedDir = getSharedDir(ctx)
                copyFileIfMissing(File(sharedDir, "tokens.txt"), File(voiceDir, "tokens.txt"))
                copyDirIfMissing(File(sharedDir, "espeak-ng-data"), File(voiceDir, "espeak-ng-data"))

                if (isVoiceReady(ctx, voiceId)) {
                    Log.d(TAG, "Voice $voiceId ready")
                    updateState(voiceId, State.READY)
                } else {
                    voiceErrors[voiceId] = "Setup incomplete"
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

    fun deleteVoice(ctx: Context, voiceId: String) {
        getVoiceDir(ctx, voiceId).deleteRecursively()
        voiceStates.remove(voiceId)
        voiceProgress.remove(voiceId)
        voiceErrors.remove(voiceId)
        updateState(voiceId, State.NOT_DOWNLOADED)
    }

    // ── Ensure voice ready (blocking) ───────────────────────────────────────

    /**
     * Checks if a voice is ready. Called from AudioPipeline before synthesis.
     */
    fun ensureVoiceReady(ctx: Context, voiceId: String): Boolean {
        return isVoiceReady(ctx, voiceId)
    }

    // ── Shared assets ───────────────────────────────────────────────────────

    /**
     * Downloads tokens.txt and espeak-ng-data if not already present.
     * These are shared across all Piper voices.
     */
    @Synchronized
    private fun ensureSharedAssets(ctx: Context, voiceId: String) {
        val sharedDir = getSharedDir(ctx)
        val tokensFile = File(sharedDir, "tokens.txt")
        val espeakDir = File(sharedDir, "espeak-ng-data")

        // Download tokens.txt
        if (!tokensFile.exists()) {
            Log.d(TAG, "Downloading shared tokens.txt")
            onProgress?.invoke(voiceId, -1)
            val tmpTokens = File(sharedDir, "tokens.txt.tmp")
            download(PiperVoices.tokensUrl(), tmpTokens) {}
            tmpTokens.renameTo(tokensFile)
        }

        // Download espeak-ng-data (7MB tar.bz2 from k2-fsa)
        if (!espeakDir.isDirectory) {
            Log.d(TAG, "Downloading shared espeak-ng-data")
            onProgress?.invoke(voiceId, -1)

            val espeakArchive = File(sharedDir, "espeak-ng-data.tar.bz2")
            try {
                download(PiperVoices.espeakUrl(), espeakArchive) {}
                Log.d(TAG, "Extracting espeak-ng-data (${espeakArchive.length() / 1024}KB)")
                extractTarBz2(espeakArchive, sharedDir)
                espeakArchive.delete()
            } catch (e: Exception) {
                espeakArchive.delete()
                throw Exception("Failed to download espeak-ng-data: ${e.message}", e)
            }

            if (!espeakDir.isDirectory) {
                throw Exception("espeak-ng-data extraction failed — directory not found")
            }
            Log.d(TAG, "espeak-ng-data ready (${espeakDir.listFiles()?.size ?: 0} entries)")
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private fun updateState(voiceId: String, state: State) {
        voiceStates[voiceId] = state
        onStateChange?.invoke(voiceId, state)
    }

    private fun download(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        var currentUrl = urlStr
        var conn: HttpURLConnection
        var redirects = 0

        // Manually follow redirects (HttpURLConnection doesn't follow cross-host redirects)
        while (true) {
            conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.connect()

            if (conn.responseCode in 300..399 && redirects++ < 10) {
                val location = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                currentUrl = location
            } else {
                break
            }
        }

        if (conn.responseCode != 200) {
            throw Exception("HTTP ${conn.responseCode} for $urlStr")
        }

        val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
        var downloadedBytes = 0L
        var lastPct = -1

        try {
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloadedBytes += n
                        if (totalBytes > 0) {
                            val pct = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractTarBz2(archive: File, destDir: File) {
        archive.inputStream().buffered().use { raw ->
            BZip2CompressorInputStream(raw).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out ->
                                tar.copyTo(out, bufferSize = 32 * 1024)
                            }
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
        }
    }

    private fun copyFileIfMissing(src: File, dest: File) {
        if (!dest.exists() && src.exists()) {
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = false)
        }
    }

    private fun copyDirIfMissing(src: File, dest: File) {
        if (!dest.exists() && src.isDirectory) {
            src.copyRecursively(dest, overwrite = false)
        }
    }
}

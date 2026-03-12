package com.kokoro.reader

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Piper TTS voice packages — both bundled (APK assets) and downloaded.
 *
 * Bundled voices live in assets/piper_voices/{voiceId}/ and are extracted
 * to the filesystem on first use (sherpa-onnx needs file paths).
 *
 * Downloaded voices come as tar.bz2 from k2-fsa/sherpa-onnx releases.
 *
 * Storage: filesDir/sherpa/piper/{voiceId}/
 *
 * Thread-safe: downloads and extractions run on background threads.
 */
object PiperDownloadManager {

    private const val TAG = "PiperDownload"
    private const val ASSET_DIR = "piper_voices"
    private const val VERSION_MARKER = ".extracted_v1"

    enum class State { NOT_DOWNLOADED, BUNDLED, DOWNLOADING, EXTRACTING, READY, ERROR }

    // Per-voice state tracking
    private val voiceStates = mutableMapOf<String, State>()
    private val voiceProgress = mutableMapOf<String, Int>()
    private val voiceErrors = mutableMapOf<String, String>()
    private val downloading = mutableSetOf<String>()

    // Cache of bundled voice IDs (populated once)
    private var bundledVoiceIds: Set<String>? = null

    @Volatile var onStateChange: ((voiceId: String, State) -> Unit)? = null
    @Volatile var onProgress: ((voiceId: String, Int) -> Unit)? = null

    // ── Paths ───────────────────────────────────────────────────────────────

    fun getPiperDir(ctx: Context): File =
        File(ctx.filesDir, "sherpa/piper").also { it.mkdirs() }

    fun getVoiceDir(ctx: Context, voiceId: String): File =
        File(getPiperDir(ctx), voiceId)

    // ── Bundled voice detection ─────────────────────────────────────────────

    /**
     * Returns the set of voice IDs bundled inside the APK assets.
     * Cached after first call.
     */
    fun getBundledVoiceIds(ctx: Context): Set<String> {
        bundledVoiceIds?.let { return it }
        val ids = try {
            ctx.assets.list(ASSET_DIR)?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.w(TAG, "Could not list bundled voices", e)
            emptySet()
        }
        bundledVoiceIds = ids
        Log.d(TAG, "Bundled voices: $ids")
        return ids
    }

    fun isBundled(ctx: Context, voiceId: String): Boolean =
        voiceId in getBundledVoiceIds(ctx)

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
        return voiceDir.listFiles()?.firstOrNull {
            it.extension == "onnx" && !it.name.endsWith(".onnx.json")
        }
    }

    fun isDownloading(voiceId: String): Boolean =
        synchronized(downloading) { voiceId in downloading }

    fun getState(ctx: Context, voiceId: String): State {
        if (isVoiceReady(ctx, voiceId)) return State.READY
        if (isDownloading(voiceId)) return voiceStates[voiceId] ?: State.DOWNLOADING
        if (isBundled(ctx, voiceId)) return State.BUNDLED
        return voiceStates[voiceId] ?: State.NOT_DOWNLOADED
    }

    fun getProgress(voiceId: String): Int =
        voiceProgress[voiceId] ?: 0

    fun getError(voiceId: String): String =
        voiceErrors[voiceId] ?: ""

    // ── Extract bundled voice from APK assets ───────────────────────────────

    /**
     * Extracts a bundled voice from APK assets to the filesystem.
     * This is called lazily when the voice is first used.
     * Returns true if extraction succeeded (or voice already extracted).
     */
    fun extractBundledVoice(ctx: Context, voiceId: String): Boolean {
        if (isVoiceReady(ctx, voiceId)) return true
        if (!isBundled(ctx, voiceId)) return false

        synchronized(downloading) {
            if (voiceId in downloading) return false
            downloading.add(voiceId)
        }

        updateState(voiceId, State.EXTRACTING)

        try {
            val voiceDir = getVoiceDir(ctx, voiceId)
            val marker = File(voiceDir, VERSION_MARKER)

            // Skip if already extracted with current version
            if (marker.exists() && isVoiceReady(ctx, voiceId)) {
                updateState(voiceId, State.READY)
                return true
            }

            // Clean and re-extract
            voiceDir.deleteRecursively()
            voiceDir.mkdirs()

            val assetPath = "$ASSET_DIR/$voiceId"
            val files = ctx.assets.list(assetPath) ?: emptyArray()
            Log.d(TAG, "Extracting bundled voice $voiceId (${files.size} entries)")

            for (name in files) {
                extractAssetRecursive(ctx, "$assetPath/$name", File(voiceDir, name))
            }

            // Write version marker for future APK updates
            marker.writeText(android.os.Build.TIME.toString())

            if (isVoiceReady(ctx, voiceId)) {
                Log.d(TAG, "Bundled voice $voiceId extracted successfully")
                updateState(voiceId, State.READY)
                return true
            } else {
                voiceErrors[voiceId] = "Extraction incomplete"
                updateState(voiceId, State.ERROR)
                return false
            }
        } catch (e: Exception) {
            voiceErrors[voiceId] = e.message ?: "Extraction failed"
            Log.e(TAG, "Failed to extract bundled voice $voiceId", e)
            updateState(voiceId, State.ERROR)
            return false
        } finally {
            synchronized(downloading) { downloading.remove(voiceId) }
        }
    }

    /**
     * Extract bundled voice in background thread with progress callbacks.
     */
    fun extractBundledVoiceAsync(ctx: Context, voiceId: String) {
        Thread {
            extractBundledVoice(ctx, voiceId)
        }.start()
    }

    private fun extractAssetRecursive(ctx: Context, assetPath: String, dest: File) {
        val children = try { ctx.assets.list(assetPath) } catch (_: Exception) { null }
        if (children != null && children.isNotEmpty()) {
            // It's a directory
            dest.mkdirs()
            for (child in children) {
                extractAssetRecursive(ctx, "$assetPath/$child", File(dest, child))
            }
        } else {
            // It's a file
            dest.parentFile?.mkdirs()
            ctx.assets.open(assetPath).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            }
        }
    }

    // ── Download (for non-bundled voices) ────────────────────────────────────

    fun downloadVoice(ctx: Context, voiceId: String) {
        // If it's a bundled voice, extract from assets instead
        if (isBundled(ctx, voiceId)) {
            extractBundledVoiceAsync(ctx, voiceId)
            return
        }

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
            val tmpFile = File(getPiperDir(ctx), "$voiceId.tar.bz2.tmp")
            try {
                val url = PiperVoices.downloadUrl(voiceId)
                Log.d(TAG, "Downloading $voiceId from $url")

                download(url, tmpFile) { pct ->
                    voiceProgress[voiceId] = pct
                    onProgress?.invoke(voiceId, pct)
                }

                Log.d(TAG, "Extracting $voiceId")
                onProgress?.invoke(voiceId, -1)
                extract(tmpFile, getPiperDir(ctx), voiceId)

                tmpFile.delete()

                if (isVoiceReady(ctx, voiceId)) {
                    Log.d(TAG, "Voice $voiceId ready")
                    updateState(voiceId, State.READY)
                } else {
                    voiceErrors[voiceId] = "Extraction incomplete"
                    updateState(voiceId, State.ERROR)
                }
            } catch (e: Exception) {
                tmpFile.delete()
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
     * Ensures a voice is extracted and ready. Blocks if extraction is needed.
     * Called from AudioPipeline's background thread before synthesis.
     */
    fun ensureVoiceReady(ctx: Context, voiceId: String): Boolean {
        if (isVoiceReady(ctx, voiceId)) return true
        if (isBundled(ctx, voiceId)) return extractBundledVoice(ctx, voiceId)
        return false
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun updateState(voiceId: String, state: State) {
        voiceStates[voiceId] = state
        onStateChange?.invoke(voiceId, state)
    }

    private fun download(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        val url = URL(urlStr)
        var conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()

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
            throw Exception("HTTP ${conn.responseCode}")
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

    private fun extract(tarBz2: File, destDir: File, voiceId: String) {
        val archiveDir = PiperVoices.archiveDirName(voiceId)
        val targetDir = File(destDir, voiceId)
        targetDir.mkdirs()

        tarBz2.inputStream().buffered().use { raw ->
            BZip2CompressorInputStream(raw).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        val relativePath = entry.name.removePrefix("$archiveDir/")
                            .removePrefix(archiveDir)
                        if (relativePath.isNotBlank()) {
                            val outFile = File(targetDir, relativePath)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    tar.copyTo(out, bufferSize = 32 * 1024)
                                }
                            }
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
        }
    }
}

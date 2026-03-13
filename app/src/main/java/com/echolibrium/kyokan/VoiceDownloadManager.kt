package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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

    enum class State { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    @Volatile var state: State = State.NOT_DOWNLOADED
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

    // ── Download ──────────────────────────────────────────────────────────────

    fun downloadModel(ctx: Context) {
        if (state == State.DOWNLOADING) return
        if (isModelReady(ctx)) { updateState(State.READY); return }

        updateState(State.DOWNLOADING)
        progressPercent = 0

        Thread {
            val tmpFile = File(getSherpaDir(ctx), "download.tar.bz2.tmp")
            try {
                Log.d(TAG, "Downloading from $DOWNLOAD_URL")
                download(DOWNLOAD_URL, tmpFile) { pct ->
                    progressPercent = pct
                    progressCallback?.invoke(pct)
                }

                Log.d(TAG, "Extracting to ${getSherpaDir(ctx)}")
                progressCallback?.invoke(-1)  // signal "extracting"
                extract(tmpFile, getSherpaDir(ctx))

                tmpFile.delete()

                if (isModelReady(ctx)) {
                    Log.d(TAG, "Model ready at ${getModelDir(ctx)}")
                    updateState(State.READY)
                } else {
                    updateState(State.ERROR)
                    errorMessage = "Extraction incomplete — missing required files"
                }

            } catch (e: Exception) {
                tmpFile.delete()
                errorMessage = e.message ?: "Unknown error"
                Log.e(TAG, "Download failed", e)
                updateState(State.ERROR)
            }
        }.start()
    }

    fun deleteModel(ctx: Context) {
        getModelDir(ctx).deleteRecursively()
        updateState(State.NOT_DOWNLOADED)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun updateState(s: State) {
        state = s
        stateCallback?.invoke(s)
    }

    private fun download(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        val url = URL(urlStr)
        var conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout    = 30_000
        conn.connect()

        // Follow redirects manually (GitHub releases redirect)
        var redirects = 0
        while (conn.responseCode in 300..399 && redirects++ < 5) {
            val location = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = URL(location).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 30_000
            conn.connect()
        }

        if (conn.responseCode != 200) {
            throw Exception("HTTP ${conn.responseCode} from $urlStr")
        }

        val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
        var downloadedBytes = 0L
        var lastReportedPct = -1

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
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extract(tarBz2: File, destDir: File) {
        tarBz2.inputStream().buffered().use { raw ->
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
}

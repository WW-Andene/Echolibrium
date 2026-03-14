package com.echolibrium.kyokan

import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared download and extraction utilities for VoiceDownloadManager and PiperDownloadManager.
 *
 * Handles HTTP download with redirect following and tar.bz2 extraction.
 * Supports resume-on-disconnect via HTTP Range headers.
 */
object DownloadUtil {

    private const val TAG = "DownloadUtil"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000
    private const val MAX_REDIRECTS = 5
    private const val BUFFER_SIZE = 32 * 1024
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 2000L

    /**
     * Download a file from urlStr to dest with progress reporting.
     * Retries on network failure and supports resuming partial downloads.
     */
    fun download(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        var attempt = 0
        while (true) {
            try {
                val existingBytes = if (dest.exists()) dest.length() else 0L
                downloadRange(urlStr, dest, existingBytes, onProgress)
                return
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_RETRIES) throw e
                Log.w(TAG, "Download attempt $attempt failed, retrying in ${RETRY_DELAY_MS * attempt}ms", e)
                Thread.sleep(RETRY_DELAY_MS * attempt)
            }
        }
    }

    private fun downloadRange(urlStr: String, dest: File, resumeFrom: Long, onProgress: (Int) -> Unit) {
        var conn = openConnection(urlStr)

        // Request resume if we have partial data
        if (resumeFrom > 0) {
            conn.setRequestProperty("Range", "bytes=$resumeFrom-")
        }

        conn.connect()
        conn = followRedirects(conn)

        val responseCode = conn.responseCode
        val isResuming = responseCode == 206 && resumeFrom > 0
        if (responseCode != 200 && !isResuming) {
            conn.disconnect()
            throw Exception("HTTP $responseCode from $urlStr")
        }

        val contentLength = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
        val totalBytes = if (isResuming && contentLength > 0) resumeFrom + contentLength else contentLength
        var downloadedBytes = if (isResuming) resumeFrom else 0L
        var lastReportedPct = -1

        // If server doesn't support Range (200 instead of 206), start fresh
        val append = isResuming

        try {
            conn.inputStream.use { input ->
                dest.outputStream(append).use { output ->
                    val buf = ByteArray(BUFFER_SIZE)
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

    private fun openConnection(urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        return conn
    }

    private fun followRedirects(initial: HttpURLConnection): HttpURLConnection {
        var conn = initial
        var redirects = 0
        while (conn.responseCode in 300..399 && redirects++ < MAX_REDIRECTS) {
            val location = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = openConnection(location)
            conn.connect()
        }
        return conn
    }

    /**
     * Extract a tar.bz2 archive to destDir.
     * If stripPrefix is provided, that prefix is removed from entry paths.
     */
    fun extractTarBz2(tarBz2: File, destDir: File, stripPrefix: String? = null) {
        tarBz2.inputStream().buffered().use { raw ->
            BZip2CompressorInputStream(raw).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        var relativePath = entry.name
                        if (stripPrefix != null) {
                            relativePath = relativePath.removePrefix("$stripPrefix/")
                                .removePrefix(stripPrefix)
                        }
                        if (relativePath.isNotBlank()) {
                            val outFile = File(destDir, relativePath)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    tar.copyTo(out, bufferSize = BUFFER_SIZE)
                                }
                            }
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
        }
    }

    /** Extension to open FileOutputStream in append mode */
    private fun File.outputStream(append: Boolean): java.io.FileOutputStream =
        java.io.FileOutputStream(this, append)
}

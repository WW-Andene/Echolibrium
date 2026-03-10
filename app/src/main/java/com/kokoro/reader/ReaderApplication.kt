package com.kokoro.reader

import android.app.Application
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Application class that installs a global uncaught exception handler.
 *
 * Every crash (including daemon thread crashes) writes a log file to
 * Kyōkan/Logs/ on external storage (and app-private storage as fallback)
 * so the user can inspect crash details even after the app restarts.
 */
class ReaderApplication : Application() {

    companion object {
        private const val TAG = "ReaderApplication"
        /** User-visible crash log folder on shared storage */
        private const val CRASH_LOG_DIR = "Kyōkan/Logs"
    }

    override fun onCreate() {
        super.onCreate()
        ensureLogDirectories()
        installUncaughtExceptionHandler()
    }

    /**
     * Creates the Kyōkan/Logs/ directory structure on first launch.
     * - App-private external storage (always works, no permission needed)
     * - Shared storage Kyōkan/Logs/ (works if MANAGE_EXTERNAL_STORAGE is granted)
     */
    private fun ensureLogDirectories() {
        try {
            // Internal storage (always available, no permission needed)
            File(filesDir, "Kyōkan/Logs").mkdirs()

            // App-private external: /storage/emulated/0/Android/data/<pkg>/files/Kyōkan/Logs/
            try {
                val appPrivateDir = getExternalFilesDir(null)
                if (appPrivateDir != null) {
                    File(appPrivateDir, "Kyōkan/Logs").mkdirs()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not create app-private log directory", e)
            }

            // Shared storage: /storage/emulated/0/Kyōkan/Logs/
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                    File(Environment.getExternalStorageDirectory(), CRASH_LOG_DIR).mkdirs()
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    File(Environment.getExternalStorageDirectory(), CRASH_LOG_DIR).mkdirs()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not create shared log directory", e)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not create log directories", e)
        }
    }

    private fun installUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread '${thread.name}'", throwable)

            // Always attempt to write crash log to file
            writeCrashLog(thread, throwable)

            // For daemon/background threads, absorb the crash instead of killing the app.
            if (thread.isDaemon) {
                Log.w(TAG, "Daemon thread '${thread.name}' crashed — absorbing to keep app alive")
                return@setDefaultUncaughtExceptionHandler
            }
            // For the main thread and non-daemon threads, delegate to the default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Writes crash details to Kyōkan/Logs/crash_<timestamp>.log
     * Tries shared storage first, then falls back to app-private storage.
     * This runs inside the crash handler so it must never throw.
     */
    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
            val filename = "crash_$timestamp.log"

            val content = buildCrashContent(thread, throwable)

            // Try 1: Shared storage /storage/emulated/0/Kyōkan/Logs/
            val sharedDir = File(Environment.getExternalStorageDirectory(), CRASH_LOG_DIR)
            if (tryWriteLog(sharedDir, filename, content)) return

            // Try 2: App-private external storage
            val appPrivateDir = getExternalFilesDir(null)
            if (appPrivateDir != null) {
                val privateLogDir = File(appPrivateDir, "Kyōkan/Logs")
                if (tryWriteLog(privateLogDir, filename, content)) return
            }

            // Try 3: Internal storage (always available)
            val internalDir = File(filesDir, "Kyōkan/Logs")
            tryWriteLog(internalDir, filename, content)

        } catch (e: Throwable) {
            // Last resort — crash handler must never throw
            Log.e(TAG, "Failed to write crash log file", e)
        }
    }

    private fun tryWriteLog(dir: File, filename: String, content: String): Boolean {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            file.writeText(content)
            Log.i(TAG, "Crash log written to ${file.absolutePath}")
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun buildCrashContent(thread: Thread, throwable: Throwable): String {
        val stackTrace = StringWriter().also { sw ->
            PrintWriter(sw).use { throwable.printStackTrace(it) }
        }.toString()

        return buildString {
            appendLine("=== Kyōkan Crash Log ===")
            appendLine("Time       : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            appendLine("Thread     : ${thread.name} (daemon=${thread.isDaemon})")
            appendLine("Exception  : ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("Device     : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }}")
            appendLine()
            appendLine("--- Stack Trace ---")
            appendLine(stackTrace)

            // Include cause chain
            var cause = throwable.cause
            while (cause != null) {
                val currentCause = cause
                val causeTrace = StringWriter().also { sw ->
                    PrintWriter(sw).use { currentCause.printStackTrace(it) }
                }.toString()
                appendLine("--- Caused by ---")
                appendLine(causeTrace)
                cause = currentCause.cause
            }
        }
    }
}

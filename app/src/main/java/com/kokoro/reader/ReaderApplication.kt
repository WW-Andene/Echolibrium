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
        private const val LOG_SUBDIR = "Kyokan/Logs"

        /** Resolved log directory — accessible from other components for "view logs" UI */
        @Volatile var resolvedLogDir: File? = null
            private set

        /** Human description of where logs are stored */
        @Volatile var logLocationDescription: String = "not initialized"
            private set
    }

    override fun onCreate() {
        super.onCreate()
        resolvedLogDir = resolveLogDirectory()
        installUncaughtExceptionHandler()

        // Start loading the TTS engine immediately on app launch.
        // By the time the user grants notification permission and the service
        // starts, the engine will already be warm and ready — zero delay.
        SherpaEngine.warmUp(this)
    }

    /**
     * Finds the best writable directory for crash logs and creates it.
     * Returns the first directory that was successfully created.
     *
     * Priority:
     *   1. Shared storage /storage/emulated/0/Kyokan/Logs/ (user-visible in file manager)
     *   2. App-private external /Android/data/<pkg>/files/Kyokan/Logs/ (visible via file manager on some devices)
     *   3. Internal storage (always available, needs adb to access)
     */
    private fun resolveLogDirectory(): File? {
        // Try shared storage (visible in file manager)
        try {
            val canWriteShared = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true // WRITE_EXTERNAL_STORAGE is in manifest
            }
            if (canWriteShared) {
                val dir = File(Environment.getExternalStorageDirectory(), LOG_SUBDIR)
                if (dir.mkdirs() || dir.isDirectory) {
                    // Verify we can actually write
                    val testFile = File(dir, ".write_test")
                    if (testFile.createNewFile()) {
                        testFile.delete()
                        Log.i(TAG, "Log directory: ${dir.absolutePath}")
                        logLocationDescription = dir.absolutePath
                        return dir
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Shared storage log dir unavailable", e)
        }

        // Try app-private external storage (visible in some file managers)
        try {
            val appExt = getExternalFilesDir(null)
            if (appExt != null) {
                val dir = File(appExt, LOG_SUBDIR)
                if (dir.mkdirs() || dir.isDirectory) {
                    Log.i(TAG, "Log directory: ${dir.absolutePath}")
                    logLocationDescription = dir.absolutePath
                    return dir
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "App-private external log dir unavailable", e)
        }

        // Fallback: internal storage (always works, needs adb)
        return try {
            val dir = File(filesDir, LOG_SUBDIR)
            dir.mkdirs()
            Log.i(TAG, "Log directory (internal): ${dir.absolutePath}")
            logLocationDescription = "${dir.absolutePath} (internal — use adb to access)"
            dir
        } catch (e: Throwable) {
            Log.e(TAG, "Cannot create any log directory", e)
            logLocationDescription = "unavailable"
            null
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
     * Writes crash details to Kyokan/Logs/crash_<timestamp>.log
     * Uses the directory resolved during onCreate.
     * This runs inside the crash handler so it must never throw.
     */
    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val dir = resolvedLogDir ?: return
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
            val file = File(dir, "crash_$timestamp.log")
            file.writeText(buildCrashContent(thread, throwable))
            Log.i(TAG, "Crash log written to ${file.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write crash log file", e)
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

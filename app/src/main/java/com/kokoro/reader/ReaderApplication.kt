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
 * This prevents silent crashes from background threads (e.g. native library errors,
 * OutOfMemoryError during model loading) from killing the app without any log output.
 * The handler logs the error and then delegates to the default handler so Android
 * still shows the standard crash dialog.
 *
 * Every crash (including daemon thread crashes) writes a log file to
 * /storage/emulated/0/WW_Andene/ so the user can inspect crash details
 * even after the app restarts.
 */
class ReaderApplication : Application() {

    companion object {
        private const val TAG = "ReaderApplication"
        private const val CRASH_LOG_DIR = "WW_Andene"
    }

    override fun onCreate() {
        super.onCreate()
        installUncaughtExceptionHandler()
    }

    private fun installUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread '${thread.name}'", throwable)

            // Always attempt to write crash log to file
            writeCrashLog(thread, throwable)

            // For daemon/background threads, absorb the crash instead of killing the app.
            // These threads (e.g. AudioPipeline-loop, SherpaEngine-warmup, PiperPreload-*)
            // are isolated workers — their failure should not take down the UI.
            if (thread.isDaemon) {
                Log.w(TAG, "Daemon thread '${thread.name}' crashed — absorbing to keep app alive")
                return@setDefaultUncaughtExceptionHandler
            }
            // For the main thread and non-daemon threads, delegate to the default handler
            // so Android shows the standard crash dialog.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Writes crash details to /storage/emulated/0/WW_Andene/crash_<timestamp>.log
     * This runs inside the crash handler so it must never throw.
     */
    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val dir = File(Environment.getExternalStorageDirectory(), CRASH_LOG_DIR)
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
            val file = File(dir, "crash_$timestamp.log")

            val stackTrace = StringWriter().also { sw ->
                PrintWriter(sw).use { throwable.printStackTrace(it) }
            }.toString()

            val content = buildString {
                appendLine("=== Echolibrium Crash Log ===")
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
                    val causeTrace = StringWriter().also { sw ->
                        PrintWriter(sw).use { cause!!.printStackTrace(it) }
                    }.toString()
                    appendLine("--- Caused by ---")
                    appendLine(causeTrace)
                    cause = cause.cause
                }
            }

            file.writeText(content)
            Log.i(TAG, "Crash log written to ${file.absolutePath}")
        } catch (e: Throwable) {
            // Last resort — crash handler must never throw
            Log.e(TAG, "Failed to write crash log file", e)
        }
    }
}

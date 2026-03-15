package com.echolibrium.kyokan

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local crash logger — captures uncaught exceptions to a log file.
 *
 * Writes crash reports to filesDir/crash_logs/ with timestamps.
 * Keeps the last 10 crash logs and auto-prunes older ones.
 * Chains to the default handler so the system still shows the crash dialog.
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LOG_DIR = "crash_logs"
    private const val MAX_LOGS = 10

    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            }
            // Chain to default handler (shows crash dialog, kills process)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.d(TAG, "Crash logger installed")
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val logDir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val logFile = File(logDir, "crash_$timestamp.txt")

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("=== Echolibrium Crash Report ===")
        pw.println("Time: $timestamp")
        pw.println("Thread: ${thread.name} (id=${thread.id})")
        pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(pInfo)
            pw.println("App: ${pInfo.versionName} (code $versionCode)")
        } catch (_: Exception) {}
        pw.println()
        pw.println("=== Stack Trace ===")
        throwable.printStackTrace(pw)
        pw.flush()

        // C-03: Sanitize sensitive data before writing to disk
        val sanitized = sanitize(sw.toString())
        logFile.writeText(sanitized)
        Log.e(TAG, "Crash log written to ${logFile.absolutePath}")

        // Prune old logs
        val logs = logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (logs.size > MAX_LOGS) {
            logs.drop(MAX_LOGS).forEach { it.delete() }
        }
    }

    /** Read the most recent crash log, or null if none exists */
    fun getLastCrashLog(context: Context): String? {
        val logDir = File(context.filesDir, LOG_DIR)
        return logDir.listFiles()
            ?.maxByOrNull { it.lastModified() }
            ?.readText()
    }

    /**
     * C-03: Redact potential API keys and auth tokens from crash log text.
     * Protects against accidental exposure when users share crash reports.
     */
    private fun sanitize(text: String): String {
        return text
            .replace(Regex("Bearer [^\\s\"']+"), "Bearer [REDACTED]")
            .replace(Regex("[A-Za-z0-9_-]{24,}"), "[REDACTED]")
    }
}

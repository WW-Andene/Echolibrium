package com.kokoro.reader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Application class that installs a global uncaught exception handler
 * and detects native crashes (SIGSEGV etc.) via session tracking.
 *
 * Crash detection strategy:
 *   - Sets "session_active" flag in SharedPreferences on every launch
 *   - Clears the flag on clean exit (onTrimMemory COMPLETE)
 *   - If the flag is still set on next launch → previous session crashed
 *   - For native crashes (no Java exception), captures logcat from previous session
 *
 * Crash logs are written to app-private storage and optionally shared storage.
 * On next launch after a crash, CrashReportActivity is shown with a
 * "Report to GitHub" button that opens a pre-filled GitHub issue.
 */
class ReaderApplication : Application() {

    companion object {
        private const val TAG = "ReaderApplication"
        private const val LOG_SUBDIR = "Kyokan/Logs"
        private const val PREFS_NAME = "crash_tracker"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_LAST_CRASH_LOG = "last_crash_log"
        private const val KEY_CRASH_PENDING = "crash_pending"

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
        detectPreviousCrash()
        markSessionActive()

        // Delay TTS engine warm-up to avoid a race between ONNX Runtime's native
        // memory allocation and HWUI's CommonPool initialization. On some devices
        // (notably Xiaomi/MediaTek), simultaneous init causes ONNX's large mmap to
        // corrupt HWUI's pthread mutexes, resulting in:
        //   FORTIFY: pthread_mutex_lock called on a destroyed mutex
        //   Fatal signal 6 (SIGABRT) in hwuiTask threads
        // Posting with a delay lets HWUI finish its first frame and thread pool
        // setup before ONNX starts allocating.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                SherpaEngine.warmUp(this)
            } catch (e: Throwable) {
                Log.e(TAG, "Engine warm-up failed — native library may be missing", e)
                storeCrashForReport(Thread.currentThread(), e)
            }
        }, 1500)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Mark clean exit when system is about to kill the process
        if (level >= TRIM_MEMORY_COMPLETE) {
            markSessionClean()
        }
    }

    // ── Session crash detection ──────────────────────────────────────────────

    private fun markSessionActive() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SESSION_ACTIVE, true).apply()
    }

    private fun markSessionClean() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SESSION_ACTIVE, false).apply()
    }

    /**
     * Checks if the previous session crashed (session_active flag still set).
     * If so, tries to recover the crash log from file or logcat, and stores it
     * for CrashReportActivity to show.
     */
    private fun detectPreviousCrash() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasActive = prefs.getBoolean(KEY_SESSION_ACTIVE, false)

        if (!wasActive) return

        Log.w(TAG, "Previous session did not exit cleanly — recovering crash info")

        // Run crash recovery off the main thread — captureLogcat() spawns subprocesses
        // and reads their output, which blocks the main thread during startup and
        // adds pressure during the critical HWUI initialization window.
        Thread {
            val crashLog = recoverCrashLog()
            if (crashLog != null) {
                prefs.edit()
                    .putString(KEY_LAST_CRASH_LOG, crashLog)
                    .putBoolean(KEY_CRASH_PENDING, true)
                    .apply()
            }
        }.apply { name = "crash-recovery"; isDaemon = true; start() }
    }

    /**
     * Tries to recover crash information:
     * 1. From the most recent crash log file
     * 2. From logcat (captures native crash traces like SIGSEGV)
     */
    private fun recoverCrashLog(): String? {
        // Try crash log files first
        val fileLog = readMostRecentCrashFile()
        if (fileLog != null) return fileLog

        // Fall back to logcat for native crashes
        return captureLogcat()
    }

    private fun readMostRecentCrashFile(): String? {
        return try {
            val dir = resolvedLogDir ?: return null
            val files = dir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".log") }
                ?: return null
            val newest = files.maxByOrNull { it.lastModified() } ?: return null
            // Only use crash files from the last 5 minutes (likely from this crash)
            if (System.currentTimeMillis() - newest.lastModified() > 5 * 60 * 1000) return null
            newest.readText()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to read crash log file", e)
            null
        }
    }

    /**
     * Captures recent logcat output filtered for crash-related messages.
     * This catches native crashes (SIGSEGV, SIGABRT) that Java's
     * UncaughtExceptionHandler cannot intercept.
     */
    private fun captureLogcat(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-d",        // dump and exit
                "-t", "200",           // last 200 lines
                "--pid=${android.os.Process.myPid()}" // won't match old PID, so use broader filter
            ))
            // The PID changed, so get all recent crash-related lines instead
            val broadProcess = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-d", "-t", "300",
                "-s", "DEBUG:*", "AndroidRuntime:*", "FATAL:*", "libc:*", "ReaderApplication:*"
            ))
            process.destroy()
            val output = broadProcess.inputStream.bufferedReader().readText()
            broadProcess.destroy()

            if (output.isBlank()) return null

            buildString {
                appendLine("=== Kyōkan Crash Log (recovered from logcat) ===")
                appendLine("Time       : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("Device     : ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("App version: ${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }}")
                appendLine("Note       : Native crash — recovered from system log")
                appendLine()
                appendLine("--- System Log ---")
                appendLine(output)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to capture logcat", e)
            null
        }
    }

    /**
     * Called from MainActivity to check if there's a pending crash report.
     * Returns the Intent to launch CrashReportActivity, or null.
     */
    fun consumePendingCrashReport(): Intent? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CRASH_PENDING, false)) return null

        val log = prefs.getString(KEY_LAST_CRASH_LOG, null)
        prefs.edit()
            .putBoolean(KEY_CRASH_PENDING, false)
            .remove(KEY_LAST_CRASH_LOG)
            .apply()

        if (log.isNullOrBlank()) return null

        return Intent(this, CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_CRASH_LOG, log)
        }
    }

    // ── Log directory resolution ────────────────────────────────────────────

    /**
     * Finds the best writable directory for crash logs and creates it.
     * Priority:
     *   1. App-private external (always works, visible in some file managers)
     *   2. Shared storage /storage/emulated/0/Kyokan/Logs/ (user-visible, needs permission)
     *   3. Internal storage (always available, needs adb to access)
     */
    private fun resolveLogDirectory(): File? {
        // Try app-private external storage first (always works, no permission needed)
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

        // Try shared storage (visible in file manager, needs MANAGE_EXTERNAL_STORAGE on API 30+)
        try {
            val canWriteShared = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
            if (canWriteShared) {
                val dir = File(Environment.getExternalStorageDirectory(), LOG_SUBDIR)
                if (dir.mkdirs() || dir.isDirectory) {
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

    // ── Uncaught exception handler ──────────────────────────────────────────

    private fun installUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread '${thread.name}'", throwable)

            // Always attempt to write crash log to file
            writeCrashLog(thread, throwable)

            // Also store in SharedPreferences for the crash report screen
            storeCrashForReport(thread, throwable)

            // For daemon/background threads, absorb the crash instead of killing the app
            if (thread.isDaemon) {
                Log.w(TAG, "Daemon thread '${thread.name}' crashed — absorbing to keep app alive")
                return@setDefaultUncaughtExceptionHandler
            }
            // For the main thread and non-daemon threads, delegate to the default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Stores crash info in SharedPreferences so CrashReportActivity can show it
     * on the next launch. This is more reliable than depending on crash log files.
     */
    private fun storeCrashForReport(thread: Thread, throwable: Throwable) {
        try {
            val content = buildCrashContent(thread, throwable)
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CRASH_LOG, content)
                .putBoolean(KEY_CRASH_PENDING, true)
                .commit() // commit() not apply() — we're about to die
        } catch (_: Throwable) {
            // Last resort — can't do anything here
        }
    }

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

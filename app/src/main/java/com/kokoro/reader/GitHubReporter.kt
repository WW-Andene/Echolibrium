package com.kokoro.reader

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Automatic crash/error reporter and remote config fetcher via GitHub.
 *
 * 1. **Auto-report**: After 30s of app running with an engine error, creates a
 *    GitHub Issue with full diagnostics (device info, init log, crash reason).
 *    Requires a fine-grained PAT with `issues:write` stored in BuildConfig.
 *    Rate-limited: one report per error per install.
 *
 * 2. **Remote ping**: Fetches a JSON config file from the repo that can contain
 *    instructions (force retry, config overrides, user messages). The app
 *    already fetches from GitHub releases — this extends that to a config channel.
 */
object GitHubReporter {

    private const val TAG = "GitHubReporter"
    private const val GITHUB_REPO = "WW-Andene/Echolibrium"
    private const val GITHUB_API = "https://api.github.com"
    private const val PREFS_NAME = "github_reporter"
    private const val KEY_LAST_REPORT_HASH = "last_report_hash"
    private const val KEY_LAST_REPORT_TIME = "last_report_time"
    private const val KEY_LAST_PING_TIME = "last_ping_time"
    private const val KEY_REMOTE_CONFIG = "remote_config"

    /** Minimum interval between reports (1 hour) — prevents spam if user keeps retrying. */
    private const val REPORT_COOLDOWN_MS = 3_600_000L

    /** How often to check for remote config (6 hours). */
    private const val PING_INTERVAL_MS = 6 * 3_600_000L

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Auto-report to GitHub Issues ──────────────────────────────────────────

    /**
     * Schedules an automatic error report after [delayMs]. Called from the main
     * process after the app starts. If the engine is in error state when the
     * timer fires, creates a GitHub Issue with full diagnostics.
     */
    fun scheduleAutoReport(ctx: Context, delayMs: Long = 30_000L) {
        Thread {
            try {
                Thread.sleep(delayMs)
                checkAndReport(ctx)
            } catch (_: InterruptedException) {}
        }.apply { name = "GitHubReporter-autoReport"; isDaemon = true; start() }
    }

    /**
     * Manually triggered report — bypasses rate limiting and delay.
     * Called from "Force report to GitHub" button.
     * Returns a result message for the UI.
     */
    fun forceReport(ctx: Context): String {
        val token = BuildConfig.GITHUB_ISSUES_TOKEN
        if (token.isEmpty()) {
            return "No GitHub token configured (set github.issues.token in local.properties)"
        }

        val status = TtsBridge.readStatus(ctx)

        // Build diagnostics even if no error — user wants to force it
        val title = buildTitle(ctx, status)
        val body = buildBody(ctx, status)

        val success = createGitHubIssue(token, title, body, listOf("manual-report"))
        return if (success) {
            val p = prefs(ctx)
            p.edit()
                .putString(KEY_LAST_REPORT_HASH, (status.error ?: "manual").hashCode().toString())
                .putLong(KEY_LAST_REPORT_TIME, System.currentTimeMillis())
                .apply()
            "Report sent to GitHub Issues"
        } else {
            "Failed to create GitHub issue — check token/network"
        }
    }

    private fun checkAndReport(ctx: Context) {
        // Read TTS status
        val status = TtsBridge.readStatus(ctx) ?: return
        if (status.error.isNullOrEmpty()) return  // No error — nothing to report

        val token = BuildConfig.GITHUB_ISSUES_TOKEN
        if (token.isEmpty()) {
            Log.d(TAG, "No GitHub token configured — skipping auto-report")
            return
        }

        // Rate limit: don't report the same error twice
        val errorHash = status.error.hashCode().toString()
        val p = prefs(ctx)
        val lastHash = p.getString(KEY_LAST_REPORT_HASH, "")
        val lastTime = p.getLong(KEY_LAST_REPORT_TIME, 0)
        if (errorHash == lastHash && System.currentTimeMillis() - lastTime < REPORT_COOLDOWN_MS) {
            Log.d(TAG, "Same error already reported recently — skipping")
            return
        }

        // Build the issue
        val title = buildTitle(ctx, status)
        val body = buildBody(ctx, status)

        // Post to GitHub
        val success = createGitHubIssue(token, title, body, listOf("auto-report", "crash"))
        if (success) {
            p.edit()
                .putString(KEY_LAST_REPORT_HASH, errorHash)
                .putLong(KEY_LAST_REPORT_TIME, System.currentTimeMillis())
                .apply()
            Log.i(TAG, "Auto-reported error to GitHub Issues")
        }
    }

    private fun buildTitle(ctx: Context, status: TtsBridge.EngineStatus): String {
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        return "[Auto] TTS Error — v$version on ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private fun buildBody(ctx: Context, status: TtsBridge.EngineStatus): String {
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        return buildString {
            appendLine("## Auto-generated error report")
            appendLine("*This issue was created automatically by the app after 30s of running with an engine error.*")
            appendLine()
            appendLine("## Device")
            appendLine("| Field | Value |")
            appendLine("|-------|-------|")
            appendLine("| Manufacturer | ${Build.MANUFACTURER} |")
            appendLine("| Model | ${Build.MODEL} |")
            appendLine("| Android | ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) |")
            appendLine("| Hardware | ${Build.HARDWARE} |")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appendLine("| SoC | ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL} |")
            }
            appendLine("| App version | $version |")
            appendLine("| ABI | ${Build.SUPPORTED_ABIS.joinToString()} |")
            appendLine()
            appendLine("## Error")
            appendLine("```")
            appendLine("Status : ${status.status}")
            appendLine("Error  : ${status.error}")
            appendLine("Ready  : ${status.ready}")
            appendLine("Alive  : ${status.alive}")
            appendLine("```")
            appendLine()

            // Include init log if available
            val initLog = readLatestInitLog(ctx)
            if (initLog != null) {
                appendLine("## Init Log")
                appendLine("```")
                appendLine(initLog.take(8000))
                if (initLog.length > 8000) appendLine("... (truncated)")
                appendLine("```")
            }

            // Include process log from SherpaEngine
            val processLog = SherpaEngine.dumpProcessLog()
            if (processLog.isNotEmpty()) {
                appendLine()
                appendLine("## Process Log")
                appendLine("```")
                appendLine(processLog.take(8000))
                if (processLog.length > 8000) appendLine("... (truncated)")
                appendLine("```")
            }
        }
    }

    private fun readLatestInitLog(ctx: Context): String? {
        return try {
            val dirs = listOfNotNull(
                ReaderApplication.resolvedLogDir,
                File(ctx.applicationContext.filesDir, "logs")
            )
            dirs.flatMap { dir ->
                dir.listFiles { f -> f.name.startsWith("engine_init_") }?.toList() ?: emptyList()
            }
                .maxByOrNull { it.lastModified() }
                ?.readText()
        } catch (_: Throwable) { null }
    }

    private fun createGitHubIssue(token: String, title: String, body: String, labels: List<String>): Boolean {
        return try {
            val url = URL("$GITHUB_API/repos/$GITHUB_REPO/issues")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject().apply {
                put("title", title)
                put("body", body)
                put("labels", JSONArray(labels))
            }

            conn.outputStream.use { it.write(json.toString().toByteArray()) }

            val code = conn.responseCode
            if (code in 200..299) {
                Log.i(TAG, "GitHub issue created successfully (HTTP $code)")
                true
            } else {
                val error = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Throwable) { "?" }
                Log.w(TAG, "GitHub issue creation failed: HTTP $code — $error")
                false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "GitHub issue creation failed: ${e.message}")
            false
        }
    }

    // ── Remote config / ping mechanism ────────────────────────────────────────
    // Fetches a JSON file from the repo's releases. The app owner can update this
    // file to send instructions to all running app instances:
    //   - "force_retry": true → reset crash counter and retry TTS init
    //   - "message": "..." → show a message to the user
    //   - "min_version": "4.1" → suggest update

    data class RemoteConfig(
        val forceRetry: Boolean = false,
        val message: String? = null,
        val minVersion: String? = null,
        val raw: String = ""
    )

    /**
     * Fetches remote config from the repo. Called periodically.
     * Uses raw GitHub content API (no auth needed for public repos,
     * or falls back to release asset).
     */
    fun fetchRemoteConfig(ctx: Context): RemoteConfig? {
        val p = prefs(ctx)
        val lastPing = p.getLong(KEY_LAST_PING_TIME, 0)
        if (System.currentTimeMillis() - lastPing < PING_INTERVAL_MS) {
            // Use cached config
            return try {
                val cached = p.getString(KEY_REMOTE_CONFIG, null) ?: return null
                parseRemoteConfig(cached)
            } catch (_: Throwable) { null }
        }

        return try {
            // Try raw content first (works for public repos, no auth)
            val urls = listOf(
                "https://raw.githubusercontent.com/$GITHUB_REPO/main/remote-config.json",
                "$GITHUB_API/repos/$GITHUB_REPO/contents/remote-config.json"
            )

            for (rawUrl in urls) {
                try {
                    val conn = URL(rawUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = CONNECT_TIMEOUT
                    conn.readTimeout = READ_TIMEOUT
                    conn.setRequestProperty("Accept", "application/vnd.github.raw+json")
                    val token = BuildConfig.GITHUB_ISSUES_TOKEN
                    if (token.isNotEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer $token")
                    }

                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        p.edit()
                            .putLong(KEY_LAST_PING_TIME, System.currentTimeMillis())
                            .putString(KEY_REMOTE_CONFIG, body)
                            .apply()
                        Log.d(TAG, "Remote config fetched from $rawUrl")
                        return parseRemoteConfig(body)
                    }
                } catch (_: Throwable) {}
            }
            Log.d(TAG, "No remote config available")
            p.edit().putLong(KEY_LAST_PING_TIME, System.currentTimeMillis()).apply()
            null
        } catch (e: Throwable) {
            Log.w(TAG, "Remote config fetch failed: ${e.message}")
            null
        }
    }

    private fun parseRemoteConfig(json: String): RemoteConfig {
        val obj = JSONObject(json)
        return RemoteConfig(
            forceRetry = obj.optBoolean("force_retry", false),
            message = obj.optString("message", "").ifEmpty { null },
            minVersion = obj.optString("min_version", "").ifEmpty { null },
            raw = json
        )
    }

    /**
     * Applies remote config actions. Called from the main process.
     */
    fun applyRemoteConfig(ctx: Context, config: RemoteConfig) {
        if (config.forceRetry) {
            Log.i(TAG, "Remote config: force_retry=true — resetting crash counter")
            TtsBridge.retryEngineInit(ctx)
        }
        // Message display is handled by the UI layer (HomeFragment reads it)
    }

    /**
     * One-shot: fetch remote config and apply it. Called from a background thread.
     */
    fun checkRemoteConfig(ctx: Context) {
        Thread {
            try {
                val config = fetchRemoteConfig(ctx)
                if (config != null) {
                    applyRemoteConfig(ctx, config)
                }
            } catch (_: Throwable) {}
        }.apply { name = "GitHubReporter-ping"; isDaemon = true; start() }
    }
}

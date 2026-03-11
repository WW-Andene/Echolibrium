package com.kokoro.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Shown on launch when the previous session crashed.
 * Displays the crash log and offers to open a pre-filled GitHub issue.
 */
class CrashReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
        private const val GITHUB_REPO = "WW-Andene/Echolibrium"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        val crashLog = intent.getStringExtra(EXTRA_CRASH_LOG) ?: "No crash details available."

        findViewById<TextView>(R.id.crash_log_text).text = crashLog

        // Scroll to bottom where the most relevant info usually is
        findViewById<ScrollView>(R.id.crash_scroll).post {
            findViewById<ScrollView>(R.id.crash_scroll).fullScroll(ScrollView.FOCUS_DOWN)
        }

        findViewById<Button>(R.id.btn_report_github).setOnClickListener {
            openGitHubIssue(crashLog)
        }

        findViewById<Button>(R.id.btn_copy_log).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", crashLog))
            Toast.makeText(this, "Crash log copied", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_dismiss).setOnClickListener {
            launchMainAndFinish()
        }
    }

    override fun onBackPressed() {
        launchMainAndFinish()
    }

    private fun launchMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun openGitHubIssue(crashLog: String) {
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }

        // Truncate crash log for URL safety (GitHub URLs have practical limits)
        val truncatedLog = if (crashLog.length > 4000) {
            crashLog.take(4000) + "\n\n... (truncated — paste full log from clipboard)"
        } else {
            crashLog
        }

        val title = "Crash Report — v$appVersion on ${Build.MODEL}"
        val body = buildString {
            appendLine("## Device")
            appendLine("- **Device**: $deviceInfo")
            appendLine("- **App version**: $appVersion")
            appendLine()
            appendLine("## What happened")
            appendLine("<!-- Describe what you were doing when the crash occurred -->")
            appendLine()
            appendLine("## Crash log")
            appendLine("```")
            appendLine(truncatedLog)
            appendLine("```")
        }

        // Copy full log to clipboard first (in case URL truncates it)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", crashLog))

        val url = Uri.parse("https://github.com/$GITHUB_REPO/issues/new").buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body)
            .appendQueryParameter("labels", "crash")
            .build()

        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
            Toast.makeText(this, "Full crash log copied to clipboard", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found — crash log copied to clipboard", Toast.LENGTH_LONG).show()
        }
    }
}

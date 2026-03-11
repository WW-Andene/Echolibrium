package com.kokoro.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Cross-process bridge between the UI (main process) and the TTS engine (:tts process).
 *
 * Commands (UI → TTS): sent as explicit broadcasts received by [TtsCommandReceiver].
 * Status  (TTS → UI): written to a JSON file by the TTS process, read by the UI process.
 */
object TtsBridge {

    // ── Action constants for command broadcasts ──────────────────────────────
    const val ACTION_TEST_SPEAK     = "com.kokoro.reader.action.TEST_SPEAK"
    const val ACTION_STOP           = "com.kokoro.reader.action.STOP"
    const val ACTION_PRELOAD_VOICE  = "com.kokoro.reader.action.PRELOAD_VOICE"
    const val ACTION_VOICE_CMD_START = "com.kokoro.reader.action.VOICE_CMD_START"
    const val ACTION_VOICE_CMD_STOP  = "com.kokoro.reader.action.VOICE_CMD_STOP"
    const val ACTION_RETRY_INIT      = "com.kokoro.reader.action.RETRY_INIT"
    const val ACTION_DUMP_DEBUG_LOG  = "com.kokoro.reader.action.DUMP_DEBUG_LOG"

    private const val STATUS_FILE = "tts_status.json"

    // ── Commands (called from main process UI) ───────────────────────────────

    fun testSpeak(ctx: Context, text: String, profileId: String) {
        val intent = Intent(ACTION_TEST_SPEAK)
        intent.setPackage(ctx.packageName)
        intent.putExtra("text", text)
        intent.putExtra("profile_id", profileId)
        ctx.sendBroadcast(intent)
    }

    fun stop(ctx: Context) {
        val intent = Intent(ACTION_STOP)
        intent.setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    }

    fun preloadVoice(ctx: Context, voiceId: String) {
        val intent = Intent(ACTION_PRELOAD_VOICE)
        intent.setPackage(ctx.packageName)
        intent.putExtra("voice_id", voiceId)
        ctx.sendBroadcast(intent)
    }

    fun startVoiceCommands(ctx: Context) {
        val intent = Intent(ACTION_VOICE_CMD_START)
        intent.setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    }

    fun stopVoiceCommands(ctx: Context) {
        val intent = Intent(ACTION_VOICE_CMD_STOP)
        intent.setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    }

    fun retryEngineInit(ctx: Context) {
        val intent = Intent(ACTION_RETRY_INIT)
        intent.setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    }

    fun dumpDebugLog(ctx: Context) {
        val intent = Intent(ACTION_DUMP_DEBUG_LOG)
        intent.setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    }

    // ── Status (written by TTS process, read by UI process) ──────────────────

    data class EngineStatus(
        val ready: Boolean = false,
        val status: String = "idle",
        val error: String? = null,
        val alive: Boolean = false,
        val voiceCmdListening: Boolean = false,
        val voiceCmdWakeWord: String = "",
        val initProgress: Int = 0
    )

    fun readStatus(ctx: Context): EngineStatus {
        return try {
            // Read directly — avoid exists() + readText() TOCTOU race where
            // the :tts process could be mid-rename between the two calls.
            val text = File(ctx.filesDir, STATUS_FILE).readText()
            val json = JSONObject(text)
            EngineStatus(
                ready = json.optBoolean("ready"),
                status = json.optString("status", "idle"),
                error = json.optString("error", "").ifEmpty { null },
                alive = json.optBoolean("alive"),
                voiceCmdListening = json.optBoolean("voiceCmdListening"),
                voiceCmdWakeWord = json.optString("voiceCmdWakeWord", ""),
                initProgress = json.optInt("initProgress", 0)
            )
        } catch (_: Throwable) {
            EngineStatus()
        }
    }

    /**
     * Writes engine status to a file. Called from the TTS process.
     * Uses atomic write (tmp + rename) to prevent partial reads.
     */
    fun writeStatus(
        ctx: Context,
        ready: Boolean,
        status: String,
        error: String?,
        alive: Boolean,
        voiceCmdListening: Boolean = false,
        voiceCmdWakeWord: String = "",
        initProgress: Int = 0
    ) {
        try {
            val json = JSONObject().apply {
                put("ready", ready)
                put("status", status)
                put("error", error ?: "")
                put("alive", alive)
                put("voiceCmdListening", voiceCmdListening)
                put("voiceCmdWakeWord", voiceCmdWakeWord)
                put("initProgress", initProgress)
                put("ts", System.currentTimeMillis())
            }
            val file = File(ctx.filesDir, STATUS_FILE)
            val tmp = File(ctx.filesDir, "$STATUS_FILE.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                // renameTo can silently fail on some Android filesystems — fall back to copy
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (_: Throwable) {
            // Best-effort — don't crash the TTS process over status reporting
        }
    }

    // ── TTS process staleness detection ──────────────────────────────────────

    /** Max age (ms) of the status timestamp before the TTS process is considered stale/dead. */
    private const val STALENESS_THRESHOLD_MS = 30_000L  // 30 seconds

    /**
     * Checks if the TTS process is alive by comparing the status file timestamp
     * to the current time. If the timestamp is older than [STALENESS_THRESHOLD_MS],
     * the process is likely dead or hung (killed by MIUI/HyperOS, OOM, etc.).
     *
     * @return true if the TTS process appears healthy, false if stale/dead
     */
    fun isTtsProcessHealthy(ctx: Context): Boolean {
        return try {
            val text = File(ctx.filesDir, STATUS_FILE).readText()
            val json = JSONObject(text)
            val ts = json.optLong("ts", 0)
            if (ts == 0L) return false
            val age = System.currentTimeMillis() - ts
            age < STALENESS_THRESHOLD_MS
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns the age in ms of the last TTS status update, or -1 if unknown.
     */
    fun getTtsProcessAgeMs(ctx: Context): Long {
        return try {
            val text = File(ctx.filesDir, STATUS_FILE).readText()
            val json = JSONObject(text)
            val ts = json.optLong("ts", 0)
            if (ts == 0L) -1L else System.currentTimeMillis() - ts
        } catch (_: Throwable) {
            -1L
        }
    }

    // ── Battery optimization exemption (Xiaomi/MIUI/HyperOS) ──────────────────

    private const val TAG = "TtsBridge"

    /**
     * Checks if this app is exempt from battery optimizations.
     * On Xiaomi MIUI/HyperOS, battery optimization aggressively kills background
     * processes including our :tts process. Requesting exemption tells the OS to not kill us.
     */
    fun isBatteryOptimized(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /**
     * Requests battery optimization exemption via system dialog.
     * This is the only way to get exemption without being a system app.
     * The system shows a dialog — no scary permission prompt.
     *
     * Call this from an Activity context (won't work from a Service).
     */
    fun requestBatteryExemption(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (!isBatteryOptimized(ctx)) {
            Log.d(TAG, "Already exempt from battery optimization")
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            Log.i(TAG, "Requested battery optimization exemption")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to request battery exemption: ${e.message}")
        }
    }

    // ── Xiaomi AutoStart permission ─────────────────────────────────────────────

    /**
     * Checks if this is a Xiaomi device (Xiaomi, Redmi, POCO).
     */
    fun isXiaomiDevice(): Boolean {
        val mfr = Build.MANUFACTURER.lowercase()
        return mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco")
    }

    /**
     * Attempts to open Xiaomi's AutoStart settings page so the user can allow
     * our app to auto-start. Without this, MIUI/HyperOS will prevent the :tts
     * process from restarting after being killed.
     *
     * Tries multiple known activity paths because Xiaomi changes these across
     * MIUI versions and HyperOS.
     *
     * @return true if an AutoStart settings page was successfully launched
     */
    fun requestAutoStart(ctx: Context): Boolean {
        if (!isXiaomiDevice()) return false

        val intents = listOf(
            // HyperOS / MIUI 14+
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // Older MIUI
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            ),
            // Fallback: open app info page where user can find AutoStart
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(intent)
                    Log.i(TAG, "Opened Xiaomi AutoStart settings: ${intent.component ?: intent.action}")
                    return true
                }
            } catch (e: Throwable) {
                Log.d(TAG, "AutoStart intent failed: ${e.message}")
            }
        }

        Log.w(TAG, "Could not open any Xiaomi AutoStart settings page")
        return false
    }
}

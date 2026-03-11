package com.kokoro.reader

import android.content.Context
import android.content.Intent
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
        val file = File(ctx.filesDir, STATUS_FILE)
        if (!file.exists()) return EngineStatus()
        return try {
            val json = JSONObject(file.readText())
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
            tmp.renameTo(file)
        } catch (_: Throwable) {
            // Best-effort — don't crash the TTS process over status reporting
        }
    }
}

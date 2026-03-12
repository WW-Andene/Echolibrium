package com.kokoro.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Receives commands from the UI (main process) in the TTS process (:tts).
 * Declared in AndroidManifest with android:process=":tts".
 */
class TtsCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TtsCommandReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                TtsBridge.ACTION_TEST_SPEAK -> handleTestSpeak(context, intent)
                TtsBridge.ACTION_STOP -> AudioPipeline.stop()
                TtsBridge.ACTION_PRELOAD_VOICE -> handlePreload(context, intent)
                TtsBridge.ACTION_VOICE_CMD_START -> VoiceCommandListener.start(context.applicationContext)
                TtsBridge.ACTION_VOICE_CMD_STOP -> VoiceCommandListener.stop()
                TtsBridge.ACTION_RETRY_INIT -> {
                    Log.d(TAG, "Retry engine init requested from UI")
                    SherpaEngine.forceRetry(context.applicationContext)
                }
                TtsBridge.ACTION_DUMP_DEBUG_LOG -> {
                    Log.d(TAG, "Debug log dump requested from UI")
                    val path = SherpaEngine.dumpDebugLog(context.applicationContext)
                    Log.i(TAG, "Debug log written to: $path")
                }
                TtsBridge.ACTION_DUMP_PROCESS_LOG -> {
                    val log = SherpaEngine.dumpProcessLog()
                    TtsBridge.writeProcessLog(context.applicationContext, log)
                }
                else -> Log.w(TAG, "Unknown action: ${intent.action}")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error handling command: ${intent.action}", e)
        }
    }

    private fun handleTestSpeak(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        val profileId = intent.getStringExtra("profile_id") ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val profiles = VoiceProfile.loadAll(prefs)
        val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
        val rules = loadWordingRules(prefs)
        AudioPipeline.start(context)
        AudioPipeline.testSpeak(context, text, profile, rules)
    }

    private fun handlePreload(context: Context, intent: Intent) {
        val voiceId = intent.getStringExtra("voice_id") ?: return
        Thread {
            SherpaEngine.preloadPiperVoice(context, voiceId)
        }.apply { name = "PiperPreload-$voiceId"; isDaemon = true; start() }
    }

    private fun loadWordingRules(prefs: android.content.SharedPreferences): List<Pair<String, String>> {
        val json = prefs.getString("wording_rules", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Pair(o.optString("find"), o.optString("replace"))
            }
        } catch (_: Exception) { emptyList() }
    }
}

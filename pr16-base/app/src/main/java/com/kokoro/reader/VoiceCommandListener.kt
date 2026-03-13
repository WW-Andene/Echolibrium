package com.kokoro.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Continuous voice command listener using Android's SpeechRecognizer.
 *
 * The mic only triggers action when the active profile name (wake word)
 * is heard first, preventing false positives.
 *
 * Commands: "repeat", "how long ago?", "stop", "what time?", "how are you feeling?"
 */
object VoiceCommandListener {

    private const val TAG = "VoiceCommandListener"
    private const val RESTART_DELAY_MS = 800L

    private var recognizer: SpeechRecognizer? = null
    @Volatile var isListening = false
        private set

    @Volatile var wakeWord: String = ""
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    var onStatusChanged: ((Boolean) -> Unit)? = null

    fun start(ctx: Context) {
        synchronized(this) {
            if (isListening) return
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                Log.w(TAG, "Speech recognition not available on this device")
                return
            }
            isListening = true
        }

        loadWakeWord(ctx)

        mainHandler.post {
            try {
                val sr = SpeechRecognizer.createSpeechRecognizer(ctx.applicationContext)
                sr.setRecognitionListener(CommandRecognitionListener(ctx.applicationContext))
                recognizer = sr
                onStatusChanged?.invoke(true)
                startListeningInternal()
                Log.d(TAG, "Voice command listener started (wake word: '$wakeWord')")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start voice command listener", e)
                isListening = false
                onStatusChanged?.invoke(false)
            }
        }
    }

    fun stop() {
        isListening = false
        mainHandler.post {
            try {
                recognizer?.cancel()
                recognizer?.destroy()
                recognizer = null
                onStatusChanged?.invoke(false)
                Log.d(TAG, "Voice command listener stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice command listener", e)
            }
        }
    }

    fun loadWakeWord(ctx: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val profiles = VoiceProfile.loadAll(prefs)
        val activeId = prefs.getString("active_profile_id", "") ?: ""
        val profile = profiles.find { it.id == activeId }
        wakeWord = (profile?.name ?: "").lowercase().trim()
        Log.d(TAG, "Wake word set to: '$wakeWord'")
    }

    private fun startListeningInternal() {
        val sr = recognizer ?: return
        if (!isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        }
        try {
            sr.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!isListening) return
        mainHandler.postDelayed({
            if (isListening) startListeningInternal()
        }, RESTART_DELAY_MS)
    }

    private class CommandRecognitionListener(private val ctx: Context) : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Listening for wake word '$wakeWord'…")
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                scheduleRestart()
                return
            }
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                Log.d(TAG, "Recognition client error — skipping restart")
                return
            }
            Log.w(TAG, "Recognition error: $error")
            if (isListening) {
                mainHandler.postDelayed({
                    if (isListening) {
                        try { startListeningInternal() } catch (e: Exception) {
                            Log.e(TAG, "Error restarting after error", e)
                        }
                    }
                }, 3000L)
            }
        }

        override fun onResults(results: Bundle?) {
            try {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    Log.d(TAG, "Heard: ${matches.joinToString(" | ")}")

                    val wake = wakeWord
                    if (wake.isNotBlank()) {
                        val heardWakeWord = matches.any { it.lowercase().contains(wake) }
                        if (!heardWakeWord) {
                            Log.d(TAG, "Wake word '$wake' not detected — ignoring")
                            scheduleRestart()
                            return
                        }
                        Log.d(TAG, "Wake word '$wake' detected!")
                    }

                    VoiceCommandHandler.handleCommand(ctx, matches)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing speech results", e)
            }
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

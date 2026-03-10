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
 * Continuous voice command listener that uses Android's SpeechRecognizer
 * to detect a wake word (the profile name) followed by a command.
 *
 * The mic only triggers action when the profile name is heard first,
 * preventing the constant beep from SpeechRecognizer's ready sound.
 *
 * Commands: "repeat", "how long ago?", "stop", "what time?", "how are you feeling?"
 */
object VoiceCommandListener {

    private const val TAG = "VoiceCommandListener"
    private const val RESTART_DELAY_MS = 800L

    private var recognizer: SpeechRecognizer? = null
    @Volatile var isListening = false
        private set

    /** The wake word that must be spoken before a command is processed */
    @Volatile var wakeWord: String = ""
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Callback for UI status updates — set from UI thread, invoked on main thread */
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

        // Load the active profile name as the wake word
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

    /** Reload the wake word from the active profile name */
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
            // Longer silence window — reduces how often the recognizer restarts (less beeping)
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

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected")
        }

        override fun onError(error: Int) {
            val errorName = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
                SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
                SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
                SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
                SpeechRecognizer.ERROR_SERVER -> "SERVER"
                else -> "UNKNOWN($error)"
            }

            // NO_MATCH and SPEECH_TIMEOUT are normal — just restart silently
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                scheduleRestart()
                return
            }

            // CLIENT errors often occur when recognizer is destroyed during stop —
            // avoid restarting in that case to prevent crash loops
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                Log.d(TAG, "Recognition client error — skipping restart")
                return
            }

            Log.w(TAG, "Recognition error: $errorName")
            // Longer delay for real errors
            if (isListening) {
                mainHandler.postDelayed({
                    if (isListening) {
                        try {
                            startListeningInternal()
                        } catch (e: Exception) {
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

                    // Only process if the wake word is detected in the speech
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

                    val handled = VoiceCommandHandler.handleCommand(ctx, matches)
                    if (handled) {
                        Log.d(TAG, "Command handled successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing speech results", e)
            }
            // Continue listening
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Wait for final results only
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

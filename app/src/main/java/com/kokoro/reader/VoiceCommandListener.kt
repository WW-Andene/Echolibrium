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

/**
 * Continuous voice command listener that uses Android's SpeechRecognizer
 * to detect trigger phrases like "can you repeat?" and "how long ago?"
 *
 * When a command is recognized, it's dispatched to [VoiceCommandHandler]
 * which generates an appropriate spoken response via the AudioPipeline.
 *
 * The listener automatically restarts after each recognition cycle to
 * provide continuous listening. Uses RECORD_AUDIO permission.
 */
object VoiceCommandListener {

    private const val TAG = "VoiceCommandListener"
    private const val RESTART_DELAY_MS = 500L

    private var recognizer: SpeechRecognizer? = null
    @Volatile var isListening = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Callback for UI status updates — set from UI thread, invoked on main thread */
    var onStatusChanged: ((Boolean) -> Unit)? = null

    fun start(ctx: Context) {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }

        mainHandler.post {
            try {
                val sr = SpeechRecognizer.createSpeechRecognizer(ctx.applicationContext)
                sr.setRecognitionListener(CommandRecognitionListener(ctx.applicationContext))
                recognizer = sr
                isListening = true
                onStatusChanged?.invoke(true)
                startListeningInternal()
                Log.d(TAG, "Voice command listener started")
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

    private fun startListeningInternal() {
        val sr = recognizer ?: return
        if (!isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Shorter silence detection for responsive command recognition
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
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
            Log.d(TAG, "Listening for voice commands…")
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

            // NO_MATCH and SPEECH_TIMEOUT are normal — just restart
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                scheduleRestart()
                return
            }

            Log.w(TAG, "Recognition error: $errorName")
            // Longer delay for real errors
            if (isListening) {
                mainHandler.postDelayed({
                    if (isListening) startListeningInternal()
                }, 2000L)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                Log.d(TAG, "Heard: ${matches.joinToString(" | ")}")
                val handled = VoiceCommandHandler.handleCommand(ctx, matches)
                if (handled) {
                    Log.d(TAG, "Command handled successfully")
                }
            }
            // Continue listening
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Could be used for real-time feedback, but we wait for final results
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

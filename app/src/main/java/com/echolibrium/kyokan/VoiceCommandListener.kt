package com.echolibrium.kyokan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Continuous voice command listener using Android's SpeechRecognizer.
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class VoiceCommandListener(
    private val voiceCommandHandler: VoiceCommandHandler
) {

    companion object {
        private const val TAG = "VoiceCommandListener"
        private const val RESTART_DELAY_MS = 800L
        private const val NORMAL_RESTART_DELAY_MS = 300L
        private const val MAX_CONSECUTIVE_ERRORS = 10
        private const val MAX_BACKOFF_MS = 60_000L
    }

    @Volatile private var recognizer: SpeechRecognizer? = null
    @Volatile var isListening = false
        private set

    @Volatile var wakeWord: String = ""
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val consecutiveErrors = AtomicInteger(0)

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
            recognizer?.let {
                try {
                    it.stopListening()
                    it.cancel()
                    it.destroy()
                } catch (_: Exception) {}
            }
            recognizer = null
            onStatusChanged?.invoke(false)
            Log.d(TAG, "Voice command listener stopped")
        }
    }

    fun loadWakeWord(ctx: Context) {
        val repo = ctx.container.repo
        val profiles = repo.getProfiles()
        val activeId = repo.activeProfileId
        val profile = profiles.find { it.id == activeId }
        wakeWord = (profile?.name ?: "").lowercase().trim()
        Log.d(TAG, "Wake word set to: '$wakeWord'")
    }

    private fun startListeningInternal() {
        val rec = recognizer ?: return
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
            rec.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            scheduleRestart(isError = true)
        }
    }

    private fun scheduleRestart(isError: Boolean = false) {
        if (!isListening) return
        val errCount: Int
        if (isError) {
            errCount = consecutiveErrors.incrementAndGet()
            if (errCount > MAX_CONSECUTIVE_ERRORS) {
                Log.w(TAG, "Too many consecutive errors ($errCount), stopping listener")
                isListening = false
                onStatusChanged?.invoke(false)
                return
            }
        } else {
            consecutiveErrors.set(0)
            errCount = 0
        }
        // Exponential backoff on errors: 800ms, 1.6s, 3.2s, ... up to 60s
        val delay = if (isError) {
            (RESTART_DELAY_MS * (1L shl (errCount - 1).coerceAtMost(6)))
                .coerceAtMost(MAX_BACKOFF_MS)
        } else {
            NORMAL_RESTART_DELAY_MS
        }
        mainHandler.postDelayed({
            if (isListening) startListeningInternal()
        }, delay)
    }

    private inner class CommandRecognitionListener(private val ctx: Context) : RecognitionListener {

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
                scheduleRestart(isError = false)
                return
            }
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                Log.d(TAG, "Recognition client error — skipping restart")
                return
            }
            Log.w(TAG, "Recognition error: $error (consecutive: ${consecutiveErrors.get() + 1})")
            scheduleRestart(isError = true)
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

                    voiceCommandHandler.handleCommand(ctx, matches)
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

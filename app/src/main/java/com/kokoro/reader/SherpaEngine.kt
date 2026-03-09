package com.kokoro.reader

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File

/**
 * Singleton wrapper around sherpa-onnx OfflineTts.
 *
 * Initialization is lazy — the model is large (~120MB), loading takes a few seconds.
 * Keep the instance alive as long as the service is running.
 *
 * Thread-safe: synthesize() is called from the AudioPipeline background thread.
 */
object SherpaEngine {

    private const val TAG = "SherpaEngine"

    private var tts: OfflineTts? = null
    var lastSampleRate: Int = 22050
        private set

    @Volatile var isReady = false
        private set

    // ── Initialize ────────────────────────────────────────────────────────────

    @Synchronized
    fun initialize(ctx: Context): Boolean {
        if (isReady && tts != null) return true
        if (!VoiceDownloadManager.isModelReady(ctx)) {
            Log.w(TAG, "Model not downloaded yet")
            return false
        }

        return try {
            val modelDir = VoiceDownloadManager.getModelDir(ctx)
            Log.d(TAG, "Loading model from $modelDir")

            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model  = File(modelDir, "model.onnx").absolutePath,
                voices = File(modelDir, "voices.bin").absolutePath,
                tokens = File(modelDir, "tokens.txt").absolutePath,
                dataDir = File(modelDir, "espeak-ng-data").absolutePath
            )

            val modelConfig = OfflineTtsModelConfig(
                kokoro     = kokoroConfig,
                numThreads = 2,
                debug      = false,
                provider   = "cpu"
            )

            val config = OfflineTtsConfig(model = modelConfig)
            tts = OfflineTts(config = config)
            isReady = true
            Log.d(TAG, "SherpaEngine ready")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SherpaEngine", e)
            tts = null
            isReady = false
            false
        }
    }

    // ── Synthesize ────────────────────────────────────────────────────────────

    /**
     * Synthesize text → (PCM samples, sample rate)
     * @param text   The text to speak (after all transforms applied)
     * @param sid    Speaker ID (from KokoroVoice.sid)
     * @param speed  Playback speed (1.0 = normal)
     */
    @Synchronized
    fun synthesize(text: String, sid: Int = 0, speed: Float = 1.0f): Pair<FloatArray, Int>? {
        val engine = tts ?: return null
        return try {
            val audio = engine.generate(text = text, sid = sid, speed = speed)
            lastSampleRate = audio.sampleRate
            Pair(audio.samples, audio.sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            null
        }
    }

    // ── Release ───────────────────────────────────────────────────────────────

    @Synchronized
    fun release() {
        try { tts?.release() } catch (e: Exception) { /* ignore */ }
        tts = null
        isReady = false
        Log.d(TAG, "SherpaEngine released")
    }
}

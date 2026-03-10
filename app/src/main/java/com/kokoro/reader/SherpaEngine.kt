package com.kokoro.reader

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * Singleton wrapper around sherpa-onnx OfflineTts.
 *
 * Supports two synthesis backends, both running locally:
 *   • Kokoro — 30 voices in a single model (multi-lang-v1_0), selected by speaker ID
 *   • Piper/VITS — one model per voice, loaded on demand
 *
 * Models are bundled in the APK's assets and extracted on first launch.
 * Use [warmUp] to eagerly initialize on a background thread so the first
 * synthesis call has no perceivable lag.
 *
 * Thread-safe: synthesize methods are called from the AudioPipeline background thread.
 */
object SherpaEngine {

    private const val TAG = "SherpaEngine"

    // ── Kokoro engine (single model, 30 speakers) ─────────────────────────────
    private var kokoroTts: OfflineTts? = null

    // ── Piper engine (one model per voice, cached) ────────────────────────────
    private var piperTts: OfflineTts? = null
    private var piperLoadedVoiceId: String? = null

    var lastSampleRate: Int = 22050
        private set

    @Volatile var isReady = false
        private set

    /**
     * Callback fired (on any thread) when the Kokoro engine becomes ready.
     * Useful for updating UI status.
     */
    @Volatile var onReadyCallback: (() -> Unit)? = null

    @Volatile private var isWarmingUp = false

    // ── Eager warm-up ─────────────────────────────────────────────────────────

    /**
     * Extracts models from assets (if needed) and initializes the Kokoro engine
     * on a background thread. Also extracts bundled Piper voices.
     * Guarded against duplicate concurrent calls.
     */
    fun warmUp(ctx: Context) {
        if (isReady && kokoroTts != null) { onReadyCallback?.invoke(); return }
        if (isWarmingUp) return
        isWarmingUp = true
        Thread {
            try {
                // Step 1: extract Kokoro model from assets
                VoiceDownloadManager.ensureModelSync(ctx)
                // Step 2: extract bundled Piper voices from assets
                PiperVoiceManager.extractBundledVoicesSync(ctx)
                // Step 3: load the Kokoro engine
                if (initializeKokoro(ctx)) {
                    onReadyCallback?.invoke()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Warm-up failed", e)
            } finally {
                isWarmingUp = false
            }
        }.apply { isDaemon = true; start() }
    }

    // ── Kokoro initialization ─────────────────────────────────────────────────

    @Synchronized
    fun initializeKokoro(ctx: Context): Boolean {
        if (isReady && kokoroTts != null) return true
        if (!VoiceDownloadManager.isModelReady(ctx)) {
            Log.w(TAG, "Kokoro model not extracted yet")
            return false
        }

        return try {
            val modelDir = VoiceDownloadManager.getModelDir(ctx)
            Log.d(TAG, "Loading Kokoro model from $modelDir")

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
            kokoroTts = OfflineTts(config = config)
            isReady = true
            Log.d(TAG, "Kokoro engine ready")
            true

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Kokoro engine", e)
            kokoroTts = null
            isReady = false
            false
        }
    }

    /** Kept for backward compat — delegates to initializeKokoro */
    @Synchronized
    fun initialize(ctx: Context): Boolean = initializeKokoro(ctx)

    // ── Kokoro synthesis ──────────────────────────────────────────────────────

    /**
     * Synthesize with Kokoro engine (30 bundled voices).
     * @param sid Speaker ID (from KokoroVoice.sid)
     */
    @Synchronized
    fun synthesize(text: String, sid: Int = 0, speed: Float = 1.0f): Pair<FloatArray, Int>? {
        val engine = kokoroTts ?: return null
        return try {
            val audio = engine.generate(text = text, sid = sid, speed = speed)
            lastSampleRate = audio.sampleRate
            Pair(audio.samples, audio.sampleRate)
        } catch (e: Throwable) {
            Log.e(TAG, "Kokoro synthesis failed", e)
            null
        }
    }

    // ── Piper/VITS synthesis ──────────────────────────────────────────────────

    /**
     * Synthesize with a Piper/VITS voice. Loads the voice model on demand
     * and caches it for reuse (avoids reload if same voice is used again).
     */
    @Synchronized
    fun synthesizePiper(ctx: Context, text: String, voiceId: String, speed: Float = 1.0f): Pair<FloatArray, Int>? {
        if (text.isBlank()) return null

        // Reuse cached engine if same voice
        if (piperTts == null || piperLoadedVoiceId != voiceId) {
            if (!loadPiperVoice(ctx, voiceId)) return null
        }

        val engine = piperTts ?: return null
        return try {
            val audio = engine.generate(text = text, sid = 0, speed = speed)
            lastSampleRate = audio.sampleRate
            Pair(audio.samples, audio.sampleRate)
        } catch (e: Throwable) {
            Log.e(TAG, "Piper synthesis failed for $voiceId", e)
            null
        }
    }

    private fun loadPiperVoice(ctx: Context, voiceId: String): Boolean {
        try {
            // Release previous Piper engine
            piperTts?.let { try { it.release() } catch (e: Exception) { Log.w(TAG, "Error releasing previous Piper engine", e) } }
            piperTts = null
            piperLoadedVoiceId = null

            val modelPath = PiperVoiceManager.getModelPath(ctx, voiceId)
            val tokensPath = PiperVoiceManager.getTokensPath(ctx)
            val dataDir = PiperVoiceManager.getEspeakDataDir(ctx)

            if (!File(modelPath).exists()) {
                Log.w(TAG, "Piper model not found: $modelPath")
                return false
            }
            if (!File(tokensPath).exists()) {
                Log.w(TAG, "Piper tokens.txt not found: $tokensPath")
                return false
            }
            if (!File(dataDir).exists()) {
                Log.w(TAG, "Piper espeak-ng-data not found: $dataDir")
                return false
            }

            Log.d(TAG, "Loading Piper voice: $voiceId")

            val vitsConfig = OfflineTtsVitsModelConfig(
                model   = modelPath,
                tokens  = tokensPath,
                dataDir = dataDir
            )

            val modelConfig = OfflineTtsModelConfig(
                vits       = vitsConfig,
                numThreads = 2,
                debug      = false,
                provider   = "cpu"
            )

            val config = OfflineTtsConfig(model = modelConfig)
            piperTts = OfflineTts(config = config)
            piperLoadedVoiceId = voiceId
            Log.d(TAG, "Piper voice loaded: $voiceId")
            return true

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load Piper voice $voiceId", e)
            return false
        }
    }

    /**
     * Pre-load a Piper voice model without synthesizing.
     * Call from a background thread to eliminate first-synthesis lag.
     */
    @Synchronized
    fun preloadPiperVoice(ctx: Context, voiceId: String): Boolean {
        if (piperTts != null && piperLoadedVoiceId == voiceId) return true
        return loadPiperVoice(ctx, voiceId)
    }

    // ── Release ───────────────────────────────────────────────────────────────

    @Synchronized
    fun release() {
        try { kokoroTts?.release() } catch (e: Throwable) { /* ignore */ }
        try { piperTts?.release() } catch (e: Throwable) { /* ignore */ }
        kokoroTts = null
        piperTts = null
        piperLoadedVoiceId = null
        isReady = false
        Log.d(TAG, "SherpaEngine released (Kokoro + Piper)")
    }
}

package com.kokoro.reader

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton wrapper around sherpa-onnx OfflineTts.
 *
 * Supports two synthesis backends, both running locally:
 *   • Kokoro — 30 voices in a single model (multi-lang-v1_0), selected by speaker ID
 *   • Piper/VITS — one model per voice, loaded on demand
 *
 * Models are bundled in the APK's assets and loaded directly via AssetManager —
 * no extraction to internal storage is needed.
 *
 * Thread-safe: synthesize methods are called from the AudioPipeline background thread.
 */
object SherpaEngine {

    private const val TAG = "SherpaEngine"

    // Asset paths (relative to assets/)
    private const val KOKORO_DIR = "kokoro-model"
    private const val PIPER_DIR  = "piper-models"

    /** Maximum time (ms) to wait for native OfflineTts constructor before giving up. */
    private const val INIT_TIMEOUT_MS = 45_000L

    // ── Kokoro engine (single model, 30 speakers) ─────────────────────────────
    private var kokoroTts: OfflineTts? = null

    // ── Piper engine (one model per voice, cached) ────────────────────────────
    private var piperTts: OfflineTts? = null
    private var piperLoadedVoiceId: String? = null

    var lastSampleRate: Int = 22050
        private set

    @Volatile var isReady = false
        private set

    /** Non-null when the engine failed to initialize — contains the error reason */
    @Volatile var errorMessage: String? = null
        private set

    /** Human-readable status of what the engine is currently doing */
    @Volatile var statusMessage: String = "idle"
        private set

    /** Timestamp (ms) when initialization started, 0 if not initializing */
    private val initStartTime = AtomicLong(0)

    /**
     * Callback fired (on any thread) when the Kokoro engine becomes ready.
     * Useful for updating UI status.
     */
    @Volatile var onReadyCallback: (() -> Unit)? = null

    @Volatile private var isWarmingUp = false
    private val warmUpLock = Object()

    // ── Eager warm-up ─────────────────────────────────────────────────────────

    /**
     * Initializes the Kokoro engine on a background thread.
     * Models are read directly from APK assets — no extraction step.
     * Guarded against duplicate concurrent calls.
     */
    fun warmUp(ctx: Context) {
        if (isReady && kokoroTts != null) { onReadyCallback?.invoke(); return }
        synchronized(warmUpLock) {
            if (isWarmingUp) return
            isWarmingUp = true
        }
        Thread {
            try {
                if (initializeKokoro(ctx)) {
                    onReadyCallback?.invoke()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Warm-up failed", e)
                errorMessage = e.message ?: "Unknown error during warm-up"
                statusMessage = "error: ${errorMessage}"
            } finally {
                synchronized(warmUpLock) { isWarmingUp = false }
            }
        }.apply { name = "SherpaEngine-warmup"; isDaemon = true; start() }
    }

    // ── Kokoro initialization ─────────────────────────────────────────────────

    @Synchronized
    fun initializeKokoro(ctx: Context): Boolean {
        if (isReady && kokoroTts != null) return true

        return try {
            statusMessage = "preparing Kokoro config…"
            Log.i(TAG, "┌── Kokoro init START ──────────────────────────")
            Log.i(TAG, "│ Model dir: assets/$KOKORO_DIR")

            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model   = "$KOKORO_DIR/model.onnx",
                voices  = "$KOKORO_DIR/voices.bin",
                tokens  = "$KOKORO_DIR/tokens.txt",
                dataDir = "$KOKORO_DIR/espeak-ng-data"
            )

            val modelConfig = OfflineTtsModelConfig(
                kokoro     = kokoroConfig,
                numThreads = 2,
                debug      = false,
                provider   = "cpu"
            )

            val config = OfflineTtsConfig(model = modelConfig)

            statusMessage = "loading native model (this may take 10-30s)…"
            Log.i(TAG, "│ Calling OfflineTts() — native JNI constructor…")
            initStartTime.set(System.currentTimeMillis())

            // Run the native constructor on a separate thread with a timeout.
            // OfflineTts() can SIGSEGV or hang indefinitely on some devices.
            val resultHolder = arrayOfNulls<OfflineTts>(1)
            val errorHolder = arrayOfNulls<Throwable>(1)
            val latch = CountDownLatch(1)

            val initThread = Thread {
                try {
                    resultHolder[0] = OfflineTts(assetManager = ctx.assets, config = config)
                } catch (e: Throwable) {
                    errorHolder[0] = e
                } finally {
                    latch.countDown()
                }
            }.apply { name = "SherpaEngine-native-init"; isDaemon = true; start() }

            val completed = latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val elapsed = System.currentTimeMillis() - initStartTime.get()
            initStartTime.set(0)

            if (!completed) {
                // Native init hung — don't crash, but report clearly
                Log.e(TAG, "│ OfflineTts() TIMED OUT after ${elapsed}ms")
                Log.e(TAG, "└── Kokoro init FAILED (timeout) ─────────────")
                statusMessage = "error: init timed out after ${elapsed / 1000}s"
                errorMessage = "Engine initialization timed out after ${elapsed / 1000}s. " +
                    "The model may be too large for this device's memory."
                isReady = false
                // Interrupt the hung thread (best effort — native code may ignore this)
                try { initThread.interrupt() } catch (_: Throwable) {}
                return false
            }

            // Check if native init threw
            errorHolder[0]?.let { throw it }

            kokoroTts = resultHolder[0]
            if (kokoroTts == null) {
                Log.e(TAG, "│ OfflineTts() returned null")
                Log.e(TAG, "└── Kokoro init FAILED ────────────────────────")
                statusMessage = "error: engine returned null"
                errorMessage = "Engine constructor returned null"
                isReady = false
                return false
            }

            isReady = true
            errorMessage = null
            statusMessage = "ready"
            Log.i(TAG, "│ Kokoro engine ready in ${elapsed}ms")
            Log.i(TAG, "└── Kokoro init SUCCESS ───────────────────────")
            true

        } catch (e: Throwable) {
            val elapsed = System.currentTimeMillis() - initStartTime.getAndSet(0)
            Log.e(TAG, "│ Exception after ${elapsed}ms: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "└── Kokoro init FAILED ────────────────────────", e)
            kokoroTts = null
            isReady = false
            errorMessage = e.message ?: "Failed to initialize Kokoro engine"
            statusMessage = "error: ${errorMessage}"
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

            Log.d(TAG, "Loading Piper voice from assets: $voiceId")

            val vitsConfig = OfflineTtsVitsModelConfig(
                model   = "$PIPER_DIR/$voiceId.onnx",
                tokens  = "$PIPER_DIR/tokens.txt",
                dataDir = "$KOKORO_DIR/espeak-ng-data"
            )

            val modelConfig = OfflineTtsModelConfig(
                vits       = vitsConfig,
                numThreads = 2,
                debug      = false,
                provider   = "cpu"
            )

            val config = OfflineTtsConfig(model = modelConfig)
            piperTts = OfflineTts(assetManager = ctx.assets, config = config)
            piperLoadedVoiceId = voiceId
            Log.d(TAG, "Piper voice loaded from assets: $voiceId")
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
        statusMessage = "released"
        Log.d(TAG, "SherpaEngine released (Kokoro + Piper)")
    }
}

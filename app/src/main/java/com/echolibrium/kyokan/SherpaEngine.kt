package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * Dual-engine TTS wrapper around sherpa-onnx OfflineTts.
 *
 * Supports two engine types:
 *   • Kokoro — single model with multiple speaker IDs (sid 0-10)
 *   • Piper  — one VITS model per voice (sid always 0)
 *
 * Kokoro is initialized once and shared across all Kokoro voices.
 * Piper instances are created per-voice and cached (LRU eviction).
 *
 * Thread-safe: all public methods are synchronized.
 */
class SherpaEngine(
    private val voiceDownloadManager: VoiceDownloadManager,
    private val piperDownloadManager: PiperDownloadManager
) {

    companion object {
        private const val TAG = "SherpaEngine"
        private const val MAX_PIPER_CACHE = 1
    }

    // Separate locks for Kokoro and Piper to avoid cross-engine blocking (E2)
    private val kokoroLock = Object()
    private val piperLock = Object()

    // ── Kokoro engine ───────────────────────────────────────────────────────

    private var kokoroTts: OfflineTts? = null

    @Volatile var isKokoroReady = false
        private set

    // ── Piper engine cache ──────────────────────────────────────────────────

    private val piperCache = LinkedHashMap<String, OfflineTts>(4, 0.75f, true)

    // ── Public state ────────────────────────────────────────────────────────

    var lastSampleRate: Int = 22050
        private set

    /** True if Kokoro is initialized (for backward compat with AudioPipeline) */
    fun isReady(): Boolean = synchronized(kokoroLock) { isKokoroReady && kokoroTts != null }

    // ── Kokoro: Initialize ──────────────────────────────────────────────────

    fun initialize(ctx: Context): Boolean = synchronized(kokoroLock) {
        if (isKokoroReady && kokoroTts != null) return true
        if (!voiceDownloadManager.isModelReady(ctx)) {
            Log.w(TAG, "Kokoro model not downloaded yet")
            return false
        }

        return try {
            val modelDir = voiceDownloadManager.getModelDir(ctx)
            Log.d(TAG, "Loading Kokoro from $modelDir")

            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model   = File(modelDir, "model.onnx").absolutePath,
                voices  = File(modelDir, "voices.bin").absolutePath,
                tokens  = File(modelDir, "tokens.txt").absolutePath,
                dataDir = File(modelDir, "espeak-ng-data").absolutePath
            )

            val modelConfig = OfflineTtsModelConfig(
                kokoro     = kokoroConfig,
                numThreads = 2,
                debug      = false,
                provider   = "cpu"
            )

            kokoroTts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
            isKokoroReady = true
            Log.d(TAG, "Kokoro engine ready")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Kokoro", e)
            kokoroTts = null
            isKokoroReady = false
            false
        }
    }

    // ── Kokoro: Synthesize ──────────────────────────────────────────────────

    fun synthesize(text: String, sid: Int = 0, speed: Float = 1.0f): Pair<FloatArray, Int>? = synchronized(kokoroLock) {
        val engine = kokoroTts ?: return null
        return try {
            val audio = engine.generate(text = text, sid = sid, speed = speed)
            lastSampleRate = audio.sampleRate
            Pair(audio.samples, audio.sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Kokoro synthesis failed", e)
            null
        }
    }

    // ── Piper: Initialize ───────────────────────────────────────────────────

    fun initPiper(ctx: Context, voiceId: String): Boolean = synchronized(piperLock) {
        if (piperCache.containsKey(voiceId)) return true
        if (!piperDownloadManager.isVoiceReady(ctx, voiceId)) {
            Log.w(TAG, "Piper voice $voiceId not downloaded yet")
            return false
        }

        return try {
            val voiceDir = piperDownloadManager.getVoiceDir(ctx, voiceId)
            val modelFile = piperDownloadManager.getModelFile(voiceDir, voiceId)
                ?: throw IllegalStateException("No .onnx model found in $voiceDir")
            val tokensFile = File(voiceDir, "tokens.txt")
            val espeakDir = File(voiceDir, "espeak-ng-data")

            // Defensive: verify all required files exist before passing to native code
            if (!tokensFile.exists()) {
                Log.e(TAG, "tokens.txt missing for $voiceId in $voiceDir")
                return false
            }
            if (!espeakDir.exists() || !espeakDir.isDirectory) {
                Log.e(TAG, "espeak-ng-data missing for $voiceId in $voiceDir")
                return false
            }
            if (modelFile.length() < 1024) {
                Log.e(TAG, "Model file too small for $voiceId: ${modelFile.length()} bytes")
                return false
            }

            Log.d(TAG, "Loading Piper voice $voiceId from ${modelFile.name}")

            val vitsConfig = OfflineTtsVitsModelConfig(
                model   = modelFile.absolutePath,
                tokens  = tokensFile.absolutePath,
                dataDir = espeakDir.absolutePath
            )

            val modelConfig = OfflineTtsModelConfig(
                vits       = vitsConfig,
                numThreads = 2,
                debug      = false,
                provider   = "cpu"
            )

            val tts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))

            // Evict oldest if cache is full
            evictPiperCache()
            piperCache[voiceId] = tts
            Log.d(TAG, "Piper voice $voiceId ready (cache: ${piperCache.size})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Piper voice $voiceId", e)
            false
        }
    }

    // ── Piper: Synthesize ───────────────────────────────────────────────────

    fun synthesizePiper(
        voiceId: String, text: String, speed: Float = 1.0f
    ): Pair<FloatArray, Int>? = synchronized(piperLock) {
        val engine = piperCache[voiceId] ?: return null
        return try {
            val audio = engine.generate(text = text, sid = 0, speed = speed)
            lastSampleRate = audio.sampleRate
            Pair(audio.samples, audio.sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Piper synthesis failed for $voiceId", e)
            null
        }
    }

    // ── Release ─────────────────────────────────────────────────────────────

    fun release() {
        synchronized(kokoroLock) {
            try { kokoroTts?.release() } catch (_: Exception) {}
            kokoroTts = null
            isKokoroReady = false
        }
        synchronized(piperLock) {
            piperCache.values.forEach { tts ->
                try { tts.release() } catch (_: Exception) {}
            }
            piperCache.clear()
        }
        Log.d(TAG, "SherpaEngine released (Kokoro + Piper)")
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun evictPiperCache() {
        while (piperCache.size >= MAX_PIPER_CACHE) {
            val oldest = piperCache.entries.firstOrNull() ?: break
            Log.d(TAG, "Evicting Piper voice ${oldest.key} from cache")
            try { oldest.value.release() } catch (_: Exception) {}
            piperCache.remove(oldest.key)
        }
    }
}

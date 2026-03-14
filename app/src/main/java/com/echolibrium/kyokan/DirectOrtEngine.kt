package com.echolibrium.kyokan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DirectOrtEngine — Direct ONNX Runtime inference for tensor-level control.
 *
 * Bypasses sherpa-onnx's high-level API to give direct access to model inputs/outputs.
 * We can manipulate the style vector, scales, and any other tensor before synthesis.
 *
 * MODEL PATHS: Uses the same model files as SherpaEngine:
 *   - Kokoro model: VoiceDownloadManager.getModelDir(ctx) / "model.onnx"
 *   - Voices:       VoiceDownloadManager.getModelDir(ctx) / "voices.bin"
 *   - Piper models: PiperDownloadManager voice directories
 *
 * THREADING: Runs on AudioPipeline's synthesis thread. All methods are synchronous.
 *
 * FALLBACK: If direct ORT fails, SherpaEngine's existing cascade catches it.
 * The crash recovery in :tts process still applies.
 */
class DirectOrtEngine(private val context: Context) {

    companion object {
        private const val TAG = "DirectOrtEngine"
        private const val MAX_PIPER_CACHE = 2
    }

    // ─── State ──────────────────────────────────────────────────────────

    private var ortEnv: OrtEnvironment? = null
    private var kokoroSession: OrtSession? = null
    private var piperSession: OrtSession? = null

    // Kokoro voices data (loaded from voices.bin)
    private var voicesData: FloatArray? = null
    private var voiceCount: Int = 0
    private var tokensPerVoice: Int = 0  // usually 510 or 512

    @Volatile var isKokoroReady = false
        private set
    @Volatile var isPiperReady = false
        private set

    // LRU cache of Piper ORT sessions — one per voice, max 2 in memory
    private val piperCache = LinkedHashMap<String, OrtSession>(4, 0.75f, true)
    private var activePiperVoiceId: String? = null
    private val cacheLock = Any()

    // ─── Initialization ─────────────────────────────────────────────────

    /**
     * Initialize the ORT environment and load models.
     *
     * @param kokoroModelPath Path to model.onnx (same as SherpaEngine uses)
     * @param voicesBinPath Path to voices.bin
     * @param piperModelPath Path to Piper .onnx model (optional)
     * @param numThreads ORT inference threads
     */
    fun initialize(
        kokoroModelPath: String,
        voicesBinPath: String,
        piperModelPath: String? = null,
        numThreads: Int = 2
    ): Boolean {
        return try {
            // ── Load voices.bin (no ORT dependency needed) ──
            loadVoices(voicesBinPath)

            // ── Load Kokoro model via ORT ──
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            if (File(kokoroModelPath).exists()) {
                kokoroSession = ortEnv!!.createSession(kokoroModelPath, sessionOptions)
                isKokoroReady = true
                Log.i(TAG, "Kokoro loaded via direct ORT")
                validateKokoroInterface()
            }

            if (piperModelPath != null && File(piperModelPath).exists()) {
                piperSession = ortEnv!!.createSession(piperModelPath, sessionOptions)
                isPiperReady = true
                Log.i(TAG, "Piper loaded via direct ORT")
            }

            Log.i(TAG, "DirectOrtEngine initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Direct ORT init failed: ${e.message}", e)
            cleanup()
            false
        }
    }

    private fun loadVoices(voicesBinPath: String) {
        val file = File(voicesBinPath)
        if (!file.exists()) {
            Log.e(TAG, "voices.bin not found at $voicesBinPath")
            return
        }

        val bytes = file.readBytes()
        voicesData = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(voicesData!!)

        val totalFloats = voicesData!!.size
        val STYLE_DIM = 256

        // Primary: calculate voice count from known style dimension
        if (totalFloats % STYLE_DIM == 0) {
            voiceCount = totalFloats / STYLE_DIM
            tokensPerVoice = STYLE_DIM
            Log.i(TAG, "Loaded $voiceCount voices × $tokensPerVoice style dims")
            return
        }

        // Fallback: try common voice counts
        val totalVectors = totalFloats / STYLE_DIM
        for (numVoices in listOf(82, 54, 26, 108, 11)) {
            if (totalVectors % numVoices == 0) {
                voiceCount = numVoices
                tokensPerVoice = totalVectors / numVoices
                Log.i(TAG, "Loaded $voiceCount voices, $tokensPerVoice tokens each (heuristic)")
                return
            }
        }

        // Last resort
        tokensPerVoice = STYLE_DIM
        voiceCount = totalFloats / tokensPerVoice
        Log.w(TAG, "Could not determine voice layout, guessing: $voiceCount voices × $tokensPerVoice")
    }

    private fun validateKokoroInterface() {
        val session = kokoroSession ?: return
        val inputNames = session.inputNames.toList()
        val expectedInputs = listOf("input_ids", "style", "speed")

        Log.i(TAG, "Kokoro inputs: $inputNames")
        for (name in expectedInputs) {
            if (name !in inputNames) {
                Log.w(TAG, "Expected input '$name' not found! Available: $inputNames")
                Log.w(TAG, "Run inspect_graph.py to get actual tensor names")
            }
        }
    }

    // ─── Piper Lazy Init (per-voice, LRU cached) ──────────────────────

    /**
     * Lazy-load a Piper voice model into the direct ORT pipeline.
     *
     * Mirrors SherpaEngine.initPiper() but loads into an OrtSession for
     * tensor-level control (ScaleMapper can set noise_scale, length_scale, noise_w).
     *
     * @param ctx Android context for path resolution
     * @param voiceId Piper voice ID (e.g. "en_US-lessac-medium")
     * @return true if the voice is now ready for direct ORT synthesis
     */
    fun initPiper(ctx: Context, voiceId: String): Boolean {
        synchronized(cacheLock) {
            if (piperCache.containsKey(voiceId)) {
                activePiperVoiceId = voiceId
                piperSession = piperCache[voiceId]
                isPiperReady = true
                return true
            }
        }

        if (!PiperDownloadManager.isVoiceReady(ctx, voiceId)) {
            Log.w(TAG, "Piper voice $voiceId not downloaded yet")
            return false
        }

        val env = ortEnv ?: return false

        return try {
            val voiceDir = PiperDownloadManager.getVoiceDir(ctx, voiceId)
            val modelFile = PiperDownloadManager.getModelFile(voiceDir, voiceId)
                ?: throw IllegalStateException("No .onnx model in $voiceDir")

            Log.d(TAG, "Loading Piper voice $voiceId via direct ORT: ${modelFile.name}")

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            val session = env.createSession(modelFile.absolutePath, sessionOptions)

            synchronized(cacheLock) {
                // LRU eviction — keep at most MAX_PIPER_CACHE sessions
                while (piperCache.size >= MAX_PIPER_CACHE) {
                    val oldest = piperCache.entries.firstOrNull() ?: break
                    Log.d(TAG, "Evicting Piper voice ${oldest.key} from direct ORT cache")
                    try { oldest.value.close() } catch (_: Exception) {}
                    piperCache.remove(oldest.key)
                }

                piperCache[voiceId] = session
                activePiperVoiceId = voiceId
                piperSession = session
                isPiperReady = true
            }

            Log.d(TAG, "Piper voice $voiceId ready via direct ORT (cache: ${piperCache.size})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Piper voice $voiceId via direct ORT", e)
            false
        }
    }

    // ─── Kokoro Synthesis (with style sculpting) ────────────────────────

    /**
     * Synthesize with Kokoro using a pre-sculpted style vector.
     *
     * @param inputIds Phoneme/token IDs (0-padded, from tokenizer)
     * @param sculptedStyle 256-dim style vector from StyleSculptor
     * @param speed Speed multiplier
     * @return Pair(PCM samples, sample rate) or null
     */
    fun synthesizeKokoro(
        inputIds: LongArray,
        sculptedStyle: FloatArray,
        speed: Float = 1.0f
    ): Pair<FloatArray, Int>? {
        val session = kokoroSession ?: return null
        val env = ortEnv ?: return null

        return try {
            val inputIdsTensor = OnnxTensor.createTensor(
                env,
                java.nio.LongBuffer.wrap(inputIds),
                longArrayOf(1, inputIds.size.toLong())
            )
            val styleTensor = OnnxTensor.createTensor(
                env,
                java.nio.FloatBuffer.wrap(sculptedStyle),
                longArrayOf(1, tokensPerVoice.toLong())
            )
            val speedTensor = OnnxTensor.createTensor(
                env,
                java.nio.FloatBuffer.wrap(floatArrayOf(speed)),
                longArrayOf(1)
            )

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "style" to styleTensor,
                "speed" to speedTensor,
            )

            val results = session.run(inputs)
            val outputTensor = results[0] as OnnxTensor
            val rawData = outputTensor.floatBuffer
            val samples = FloatArray(rawData.remaining())
            rawData.get(samples)

            inputIdsTensor.close()
            styleTensor.close()
            speedTensor.close()
            results.close()

            Pair(samples, 24000)  // Kokoro outputs at 24kHz
        } catch (e: Exception) {
            Log.e(TAG, "Kokoro synthesis failed: ${e.message}", e)
            null
        }
    }

    // ─── Piper Synthesis (with mapped scales) ───────────────────────────

    /**
     * Synthesize with Piper using mapped scale parameters.
     *
     * @param phonemeIds Phoneme IDs from eSpeak/phonemizer
     * @param scales [noise_scale, length_scale, noise_w] from ScaleMapper
     * @param speakerId Speaker ID for multi-speaker models (-1 for single-speaker)
     * @return Pair(PCM samples, sample rate) or null
     */
    fun synthesizePiper(
        phonemeIds: LongArray,
        scales: FloatArray,
        speakerId: Long = 0
    ): Pair<FloatArray, Int>? {
        val session = synchronized(cacheLock) { piperSession } ?: return null
        val env = ortEnv ?: return null

        return try {
            val inputTensor = OnnxTensor.createTensor(
                env,
                java.nio.LongBuffer.wrap(phonemeIds),
                longArrayOf(1, phonemeIds.size.toLong())
            )
            val lengthsTensor = OnnxTensor.createTensor(
                env,
                java.nio.LongBuffer.wrap(longArrayOf(phonemeIds.size.toLong())),
                longArrayOf(1)
            )
            val scalesTensor = OnnxTensor.createTensor(
                env,
                java.nio.FloatBuffer.wrap(scales),
                longArrayOf(3)
            )

            val inputs = mutableMapOf<String, OnnxTensor>(
                "input" to inputTensor,
                "input_lengths" to lengthsTensor,
                "scales" to scalesTensor,
            )

            val sidTensor: OnnxTensor?
            if (speakerId >= 0 && "sid" in session.inputNames) {
                sidTensor = OnnxTensor.createTensor(
                    env,
                    java.nio.LongBuffer.wrap(longArrayOf(speakerId)),
                    longArrayOf(1)
                )
                inputs["sid"] = sidTensor
            } else {
                sidTensor = null
            }

            val results = session.run(inputs)
            val outputTensor = results[0] as OnnxTensor
            val rawData = outputTensor.floatBuffer
            val samples = FloatArray(rawData.remaining())
            rawData.get(samples)

            inputTensor.close()
            lengthsTensor.close()
            scalesTensor.close()
            sidTensor?.close()
            results.close()

            Pair(samples, 22050)  // Piper default sample rate
        } catch (e: Exception) {
            Log.e(TAG, "Piper synthesis failed: ${e.message}", e)
            null
        }
    }

    // ─── Voice Data Access ──────────────────────────────────────────────

    /**
     * Get the raw style vector for a Kokoro voice at a given token count.
     * This is the base vector that StyleSculptor will then modify.
     *
     * @param voiceIndex KokoroVoice.sid (0-based index in voices.bin)
     * @param numTokens Token count of the current utterance
     */
    fun getKokoroStyle(voiceIndex: Int, numTokens: Int): FloatArray? {
        val data = voicesData ?: return null
        if (voiceIndex >= voiceCount) return null

        val tokenIdx = numTokens.coerceIn(0, tokensPerVoice - 1)
        val offset = (voiceIndex * tokensPerVoice + tokenIdx) * 256
        if (offset + 256 > data.size) return null

        return data.copyOfRange(offset, offset + 256)
    }

    /**
     * Resolve a KokoroVoice ID to its speaker index for style lookup.
     * Uses the existing KokoroVoices catalog.
     */
    fun resolveVoiceIndex(voiceId: String): Int {
        return KokoroVoices.byId(voiceId)?.sid ?: 0
    }

    fun getAllVoicesData(): FloatArray? = voicesData?.copyOf()
    fun getVoiceCount(): Int = voiceCount
    fun getTokensPerVoice(): Int = tokensPerVoice

    // ─── Lifecycle ──────────────────────────────────────────────────────

    fun cleanup() {
        try {
            kokoroSession?.close()
            // Close all cached Piper sessions
            synchronized(cacheLock) {
                piperCache.values.forEach { session ->
                    try { session.close() } catch (_: Exception) {}
                }
                piperCache.clear()
                piperSession = null
                activePiperVoiceId = null
            }
            ortEnv?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error: ${e.message}")
        }
        kokoroSession = null
        piperSession = null
        ortEnv = null
        isKokoroReady = false
        isPiperReady = false
        voicesData = null
    }
}

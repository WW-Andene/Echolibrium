package com.kokoro.reader

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
 * DEPENDENCY: Requires onnxruntime-android in build.gradle:
 *   implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.0'
 *
 * NOTE: sherpa_onnx.aar bundles libonnxruntime.so. If you get duplicate native lib
 * errors, exclude ORT's native lib and rely on sherpa's bundled version:
 *   implementation('com.microsoft.onnxruntime:onnxruntime-android:1.17.0') {
 *       exclude group: 'com.microsoft.onnxruntime', module: 'onnxruntime-android'
 *   }
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
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * IMPORTANT: This file uses ai.onnxruntime.* which is NOT yet in build.gradle.
 * The ORT classes (OrtEnvironment, OrtSession, OnnxTensor) are commented out
 * to prevent compilation errors. Uncomment when the dependency is added.
 * ──────────────────────────────────────────────────────────────────────────────
 */
class DirectOrtEngine(private val context: Context) {

    companion object {
        private const val TAG = "DirectOrtEngine"
    }

    // ─── State ──────────────────────────────────────────────────────────

    // Uncomment when onnxruntime-android dependency is added:
    // private var ortEnv: OrtEnvironment? = null
    // private var kokoroSession: OrtSession? = null
    // private var piperSession: OrtSession? = null

    // Kokoro voices data (loaded from voices.bin)
    private var voicesData: FloatArray? = null
    private var voiceCount: Int = 0
    private var tokensPerVoice: Int = 0  // usually 510 or 512

    @Volatile var isKokoroReady = false
        private set
    @Volatile var isPiperReady = false
        private set

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
            // Uncomment when onnxruntime-android dependency is added:
            /*
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
            */

            Log.i(TAG, "DirectOrtEngine initialized (voices loaded, ORT sessions pending dependency)")
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

        // Detect structure: total_floats / 256 = total_vectors
        val totalVectors = voicesData!!.size / 256

        // Try known Kokoro voice counts (11 voices in kokoro-en-v0_19, matches KokoroVoices.ALL)
        for (numVoices in listOf(11, 26, 54, 108)) {
            if (totalVectors % numVoices == 0) {
                voiceCount = numVoices
                tokensPerVoice = totalVectors / numVoices
                break
            }
        }
        if (voiceCount == 0) {
            tokensPerVoice = 512
            voiceCount = totalVectors / tokensPerVoice
        }

        Log.i(TAG, "Loaded voices: $voiceCount voices, $tokensPerVoice token variants each")
    }

    // Uncomment when onnxruntime-android dependency is added:
    /*
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
    */

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
        // Uncomment when onnxruntime-android dependency is added:
        /*
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
                longArrayOf(1, 256)
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
        */
        Log.w(TAG, "synthesizeKokoro called but ORT dependency not yet added")
        return null
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
        speakerId: Long = -1
    ): Pair<FloatArray, Int>? {
        // Uncomment when onnxruntime-android dependency is added:
        /*
        val session = piperSession ?: return null
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
        */
        Log.w(TAG, "synthesizePiper called but ORT dependency not yet added")
        return null
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
        // Uncomment when onnxruntime-android dependency is added:
        /*
        try {
            kokoroSession?.close()
            piperSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error: ${e.message}")
        }
        kokoroSession = null
        piperSession = null
        ortEnv = null
        */
        isKokoroReady = false
        isPiperReady = false
        voicesData = null
    }
}

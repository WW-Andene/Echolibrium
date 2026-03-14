package com.echolibrium.kyokan

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Cloud TTS engine backed by DeepInfra's OpenAI-compatible /audio/speech API.
 *
 * Supports three engines:
 *   - Orpheus 3B:       highest fidelity, emotion tags (<laugh>, <sigh>, etc.)
 *   - Chatterbox Turbo: fastest/cheapest, paralinguistic tags ([laugh], [cough])
 *   - Qwen3-TTS 1.7B:  natural language voice instructions ("speak calmly")
 *
 * Returns PCM FloatArray + sample rate, matching the existing SherpaEngine contract
 * so it plugs directly into AudioPipeline.processItem().
 */
object CloudTtsEngine {

    private const val TAG = "CloudTtsEngine"
    private const val BASE_URL = "https://api.deepinfra.com/v1/openai/audio/speech"
    private const val SAMPLE_RATE = 24000

    enum class Engine(val modelId: String) {
        ORPHEUS("canopylabs/orpheus-3b-0.1-ft"),
        CHATTERBOX("ResembleAI/chatterbox-turbo"),
        QWEN3_TTS("Qwen/Qwen3-TTS"),
    }

    // Orpheus voices
    private val ORPHEUS_VOICES = setOf("tara", "leah", "jess", "leo", "dan", "mia", "zac", "zoe")
    private const val ORPHEUS_DEFAULT = "tara"

    // Qwen3 voices
    private val QWEN3_VOICES = setOf(
        "Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric",
        "Ryan", "Aiden", "Ono_Anna", "Sohee"
    )
    private const val QWEN3_DEFAULT = "Vivian"

    // Emotion tag sets for routing
    private val ORPHEUS_TAGS = listOf("<laugh>", "<sigh>", "<gasp>", "<cough>",
        "<chuckle>", "<groan>", "<yawn>", "<sniffle>")
    private val CHATTERBOX_TAGS = listOf("[laugh]", "[cough]", "[chuckle]", "[sniffle]")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var apiKey: String? = null

    @Volatile
    private var enabled = false

    fun configure(key: String) {
        apiKey = key
        enabled = key.isNotBlank()
        Log.i(TAG, "Cloud TTS ${if (enabled) "enabled" else "disabled"}")
    }

    fun isEnabled(): Boolean = enabled && apiKey != null

    /**
     * Select the best engine for the given text and priority.
     *
     * Routing rules (matching the Python TtsRouter):
     *   1. Voice instruction present? → Qwen3-TTS
     *   2. Priority HIGH? → Orpheus (maximum fidelity)
     *   3. Orpheus emotion tags in text? → Orpheus
     *   4. Chatterbox tags in text? → Chatterbox
     *   5. Default → Chatterbox Turbo (best cost/quality for notifications)
     */
    fun selectEngine(
        text: String,
        priority: Boolean = false,
        voiceInstruction: String? = null
    ): Engine {
        if (voiceInstruction != null) return Engine.QWEN3_TTS
        if (priority) return Engine.ORPHEUS
        if (ORPHEUS_TAGS.any { it in text }) return Engine.ORPHEUS
        if (CHATTERBOX_TAGS.any { it in text }) return Engine.CHATTERBOX
        return Engine.CHATTERBOX
    }

    /**
     * Synthesize text to PCM audio via DeepInfra.
     *
     * @return Pair(pcm, sampleRate) or null on failure
     */
    fun synthesize(
        text: String,
        engine: Engine = Engine.CHATTERBOX,
        voice: String? = null,
        voiceInstruction: String? = null
    ): Pair<FloatArray, Int>? {
        val key = apiKey
        if (key.isNullOrBlank()) {
            Log.w(TAG, "No API key configured")
            return null
        }

        val payload = JSONObject().apply {
            put("model", engine.modelId)
            put("input", text)
            put("response_format", "pcm")

            when (engine) {
                Engine.ORPHEUS -> {
                    val v = if (voice in ORPHEUS_VOICES) voice else ORPHEUS_DEFAULT
                    put("voice", v)
                }
                Engine.QWEN3_TTS -> {
                    val v = if (voice in QWEN3_VOICES) voice else QWEN3_DEFAULT
                    put("voice", v)
                    if (voiceInstruction != null) {
                        put("instruction", voiceInstruction)
                    }
                }
                Engine.CHATTERBOX -> {
                    // Chatterbox Turbo uses default voice on DeepInfra
                }
            }
        }

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val start = System.nanoTime()

        return try {
            client.newCall(request).execute().use { response ->
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                if (!response.isSuccessful) {
                    val body = response.body?.string()?.take(200) ?: "no body"
                    Log.e(TAG, "[${engine.name}] HTTP ${response.code}: $body")
                    return null
                }

                val audioBytes = response.body?.bytes() ?: return null
                val pcm = pcmBytesToFloat(audioBytes)
                Log.d(TAG, "[${engine.name}] ${text.length} chars → ${pcm.size} samples, ${elapsedMs}ms")
                Pair(pcm, SAMPLE_RATE)
            }
        } catch (e: IOException) {
            Log.e(TAG, "[${engine.name}] Network error: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "[${engine.name}] Synthesis failed", e)
            null
        }
    }

    /**
     * Convert raw PCM bytes (16-bit signed LE from DeepInfra) to FloatArray [-1.0, 1.0].
     */
    private fun pcmBytesToFloat(bytes: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val floats = FloatArray(shortBuffer.remaining())
        for (i in floats.indices) {
            floats[i] = shortBuffer.get(i) / 32768f
        }
        return floats
    }
}

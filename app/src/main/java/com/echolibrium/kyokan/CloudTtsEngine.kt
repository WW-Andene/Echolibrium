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
 * Cloud TTS engine — Orpheus 3B via DeepInfra's OpenAI-compatible /audio/speech API.
 *
 * Returns PCM FloatArray + sample rate, matching SherpaEngine's contract.
 */
object CloudTtsEngine {

    private const val TAG = "CloudTtsEngine"
    private const val BASE_URL = "https://api.deepinfra.com/v1/openai/audio/speech"
    private const val SAMPLE_RATE = 24000
    private const val MODEL_ID = "canopylabs/orpheus-3b-0.1-ft"

    val VOICES = setOf("tara", "leah", "jess", "leo", "dan", "mia", "zac", "zoe")
    private const val DEFAULT_VOICE = "tara"

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

    fun updateApiKey(key: String) {
        apiKey = key
        enabled = key.isNotBlank()
        Log.i(TAG, "Cloud TTS API key updated: ${if (enabled) "enabled" else "disabled"}")
    }

    fun isEnabled(): Boolean = enabled && apiKey != null

    private const val MAX_RETRIES = 1
    private const val RETRY_DELAY_MS = 1500L

    /**
     * Synthesize text to PCM audio via DeepInfra Orpheus.
     * Retries once on transient failures (5xx, network errors).
     *
     * @return Pair(pcm, sampleRate) or null on failure
     */
    fun synthesize(
        text: String,
        voice: String? = null,
        language: String? = null
    ): Pair<FloatArray, Int>? {
        val key = apiKey
        if (key.isNullOrBlank()) {
            Log.w(TAG, "No API key configured")
            return null
        }

        val v = if (voice in VOICES) voice else DEFAULT_VOICE

        val payload = JSONObject().apply {
            put("model", MODEL_ID)
            put("input", text)
            put("response_format", "pcm")
            put("voice", v)
        }

        for (attempt in 0..MAX_RETRIES) {
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val start = System.nanoTime()

            try {
                client.newCall(request).execute().use { response ->
                    val elapsedMs = (System.nanoTime() - start) / 1_000_000
                    if (!response.isSuccessful) {
                        val body = response.body?.string()?.take(200) ?: "no body"
                        Log.e(TAG, "HTTP ${response.code}: $body (${elapsedMs}ms)")
                        // Retry on 5xx server errors
                        if (response.code in 500..599 && attempt < MAX_RETRIES) {
                            Log.w(TAG, "Retrying in ${RETRY_DELAY_MS}ms (attempt ${attempt + 1})")
                            Thread.sleep(RETRY_DELAY_MS)
                            return@use
                        }
                        return null
                    }

                    val audioBytes = response.body?.bytes() ?: return null
                    val pcm = pcmBytesToFloat(audioBytes)
                    Log.d(TAG, "${text.length} chars → ${pcm.size} samples, ${elapsedMs}ms")
                    return Pair(pcm, SAMPLE_RATE)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error: ${e.message}")
                if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Retrying in ${RETRY_DELAY_MS}ms (attempt ${attempt + 1})")
                    try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) { return null }
                    continue
                }
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Synthesis failed", e)
                return null
            }
        }
        return null
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

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
class CloudTtsEngine {

    companion object {
        private const val TAG = "CloudTtsEngine"
        private const val DIRECT_URL = "https://api.deepinfra.com/v1/openai/audio/speech"
        private const val SAMPLE_RATE = 24000
        private const val MODEL_ID = "canopylabs/orpheus-3b-0.1-ft"
        private const val DEFAULT_VOICE = "tara"
        private const val MAX_RETRIES = 1
        private const val RETRY_DELAY_MS = 1500L
    }

    val VOICES = setOf("tara", "leah", "jess", "leo", "dan", "mia", "zac", "zoe")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var apiKey: String? = null

    /** Cloudflare Worker proxy URL — when set, requests go through the proxy (no API key needed). */
    @Volatile
    private var proxyUrl: String? = null

    @Volatile
    private var enabled = false

    /**
     * Configure with optional proxy URL and/or direct API key.
     * Proxy takes priority: if set, cloud TTS is enabled without a local key.
     */
    fun configure(key: String, proxy: String = "") {
        apiKey = key
        proxyUrl = proxy.ifBlank { null }
        enabled = proxyUrl != null || key.isNotBlank()
        Log.i(TAG, "Cloud TTS ${if (enabled) "enabled" else "disabled"}" +
                if (proxyUrl != null) " (via proxy)" else "")
    }

    fun updateApiKey(key: String) {
        apiKey = key
        enabled = proxyUrl != null || key.isNotBlank()
        Log.i(TAG, "Cloud TTS API key updated: ${if (enabled) "enabled" else "disabled"}")
    }

    fun isEnabled(): Boolean = enabled

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
        val proxy = proxyUrl
        val key = apiKey
        if (proxy == null && key.isNullOrBlank()) {
            Log.w(TAG, "No proxy or API key configured")
            return null
        }

        val v = if (voice in VOICES) voice else DEFAULT_VOICE

        val payload = JSONObject().apply {
            put("model", MODEL_ID)
            put("input", text)
            put("response_format", "pcm")
            put("voice", v)
        }

        // Try proxy first, then fall back to direct API with user's key
        val urlsToTry = mutableListOf<Pair<String, Boolean>>()
        if (proxy != null) urlsToTry.add(Pair(proxy, false))  // proxy: no auth header
        if (!key.isNullOrBlank()) urlsToTry.add(Pair(DIRECT_URL, true))  // direct: with auth

        for ((targetUrl, useAuth) in urlsToTry) {
            for (attempt in 0..MAX_RETRIES) {
                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))

                if (useAuth) {
                    requestBuilder.addHeader("Authorization", "Bearer $key")
                }

                val request = requestBuilder.build()
                val start = System.nanoTime()

                try {
                    client.newCall(request).execute().use { response ->
                        val elapsedMs = (System.nanoTime() - start) / 1_000_000
                        if (!response.isSuccessful) {
                            val body = response.body?.string()?.take(200) ?: "no body"
                            Log.e(TAG, "HTTP ${response.code} from $targetUrl: $body (${elapsedMs}ms)")
                            if (response.code in 500..599 && attempt < MAX_RETRIES) {
                                Log.w(TAG, "Retrying in ${RETRY_DELAY_MS}ms (attempt ${attempt + 1})")
                                Thread.sleep(RETRY_DELAY_MS)
                                return@use
                            }
                            // Non-retryable error — try next URL
                            return@use
                        }

                        val audioBytes = response.body?.bytes() ?: return@use
                        val pcm = pcmBytesToFloat(audioBytes)
                        Log.d(TAG, "${text.length} chars → ${pcm.size} samples, ${elapsedMs}ms")
                        return Pair(pcm, SAMPLE_RATE)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Network error from $targetUrl: ${e.message}")
                    if (attempt < MAX_RETRIES) {
                        Log.w(TAG, "Retrying in ${RETRY_DELAY_MS}ms (attempt ${attempt + 1})")
                        try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) { return null }
                        continue
                    }
                    // Exhausted retries for this URL — try next
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Synthesis failed from $targetUrl", e)
                    break
                }
            }
            Log.w(TAG, "Failed with $targetUrl, trying next endpoint")
        }
        Log.e(TAG, "All endpoints exhausted")
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

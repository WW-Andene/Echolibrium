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
        /** C-01: Maximum input characters sent to DeepInfra — prevents uncapped API costs. */
        private const val MAX_INPUT_LENGTH = 2000
        /** K-01: Default daily character limit (~$0.35/day at DeepInfra pricing). */
        private const val DEFAULT_DAILY_CHAR_LIMIT = 50_000
        private const val PREF_DAILY_CHARS = "cloud_tts_daily_chars"
        private const val PREF_DAILY_CHARS_DATE = "cloud_tts_daily_date"
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

    /** K-01: SettingsRepository for daily usage tracking. Set via configure(). */
    @Volatile
    private var repo: SettingsRepository? = null

    /** K-01: Get today's character usage count, resetting if day changed. */
    fun dailyCharsUsed(): Int {
        val r = repo ?: return 0
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val storedDate = r.getString(PREF_DAILY_CHARS_DATE)
        if (storedDate != today) {
            r.putInt(PREF_DAILY_CHARS, 0)
            r.putString(PREF_DAILY_CHARS_DATE, today)
            return 0
        }
        return r.getInt(PREF_DAILY_CHARS, 0)
    }

    private fun addDailyChars(count: Int) {
        val r = repo ?: return
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val storedDate = r.getString(PREF_DAILY_CHARS_DATE)
        val current = if (storedDate == today) r.getInt(PREF_DAILY_CHARS, 0) else 0
        r.putInt(PREF_DAILY_CHARS, current + count)
        r.putString(PREF_DAILY_CHARS_DATE, today)
    }

    /**
     * Configure with optional proxy URL and/or direct API key.
     * Proxy takes priority: if set, cloud TTS is enabled without a local key.
     */
    fun configure(key: String, proxy: String = "", settingsRepo: SettingsRepository? = null) {
        apiKey = key
        proxyUrl = proxy.ifBlank { null }
        enabled = proxyUrl != null || key.isNotBlank()
        if (settingsRepo != null) repo = settingsRepo
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

        // K-01: Check daily character limit
        val dailyUsed = dailyCharsUsed()
        if (dailyUsed >= DEFAULT_DAILY_CHAR_LIMIT) {
            Log.w(TAG, "Daily character limit reached ($dailyUsed/$DEFAULT_DAILY_CHAR_LIMIT)")
            return null
        }

        val v = if (voice in VOICES) voice else DEFAULT_VOICE
        val trimmedText = text.take(MAX_INPUT_LENGTH)

        val payload = JSONObject().apply {
            put("model", MODEL_ID)
            put("input", trimmedText)
            put("response_format", "pcm")
            put("voice", v)
        }

        // Try proxy first, then fall back to direct API with user's key
        val urlsToTry = mutableListOf<Pair<String, Boolean>>()
        if (proxy != null) urlsToTry.add(Pair(proxy, false))  // proxy: no auth header
        if (!key.isNullOrBlank()) urlsToTry.add(Pair(DIRECT_URL, true))  // direct: with auth

        for ((targetUrl, useAuth) in urlsToTry) {
            var shouldBreakRetryLoop = false
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
                            // Non-retryable error (4xx) — break retry loop, try next URL
                            shouldBreakRetryLoop = true
                            return@use
                        }

                        val audioBytes = response.body?.bytes() ?: return@use
                        val pcm = pcmBytesToFloat(audioBytes)
                        addDailyChars(trimmedText.length)  // K-01: track usage
                        Log.d(TAG, "${trimmedText.length} chars → ${pcm.size} samples, ${elapsedMs}ms (daily: ${dailyCharsUsed()}/$DEFAULT_DAILY_CHAR_LIMIT)")
                        return Pair(pcm, SAMPLE_RATE)
                    }
                    if (shouldBreakRetryLoop) break
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

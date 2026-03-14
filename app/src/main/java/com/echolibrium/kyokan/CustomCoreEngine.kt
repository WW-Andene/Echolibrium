package com.echolibrium.kyokan

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Custom Core observation engine — periodically analyzes ObservationDb stats
 * via the Groq API and returns behavior patches for self-improving personality.
 *
 * The engine collects:
 *   - DSP node timing averages (identifies bottlenecks)
 *   - PersonalityEvent frequency distribution
 *   - Utterance stats (volume, avg processing time)
 *
 * Sends a structured prompt to Groq's fast inference API, which returns
 * a JSON behavior patch. The patch adjusts VoiceModulator sensitivities,
 * DSP chain parameters, and flood tier thresholds.
 *
 * Runs on a background scheduler, default interval: every 6 hours.
 * Requires a Groq API key stored in EncryptedSharedPreferences ("groq_api_key").
 */
object CustomCoreEngine {

    private const val TAG = "CustomCore"
    private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"
    private const val INTERVAL_HOURS = 6L
    private const val PREF_LAST_PATCH = "custom_core_last_patch"
    private const val PREF_API_KEY = "groq_api_key"

    @Volatile var lastPatch: BehaviorPatch? = null
        private set
    @Volatile var lastError: String? = null
        private set

    private var scheduler: ScheduledExecutorService? = null

    private fun getSecurePrefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            "kyokan_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun start(context: Context) {
        if (scheduler != null) return
        val appCtx = context.applicationContext

        // Load cached patch
        val prefs = PreferenceManager.getDefaultSharedPreferences(appCtx)
        val cached = prefs.getString(PREF_LAST_PATCH, null)
        if (cached != null) {
            try {
                lastPatch = BehaviorPatch.fromJson(JSONObject(cached))
                Log.i(TAG, "Restored cached behavior patch")
            } catch (e: Exception) {
                Log.w(TAG, "Corrupt cached patch, clearing", e)
                prefs.edit().remove(PREF_LAST_PATCH).apply()
            }
        }

        scheduler = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({ runAnalysis(appCtx) }, 1, INTERVAL_HOURS, TimeUnit.HOURS)
        }
        Log.d(TAG, "Custom Core engine started (interval: ${INTERVAL_HOURS}h)")
    }

    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
        Log.d(TAG, "Custom Core engine stopped")
    }

    /** Force an immediate analysis cycle */
    fun runNow(context: Context) {
        val appCtx = context.applicationContext
        scheduler?.execute { runAnalysis(appCtx) }
            ?: Thread { runAnalysis(appCtx) }.start()
    }

    private fun runAnalysis(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val securePrefs = try { getSecurePrefs(context) } catch (e: Exception) {
            Log.e(TAG, "Failed to open secure prefs", e)
            return
        }

        // Migrate API key from plain prefs to encrypted prefs (one-time)
        val plainKey = prefs.getString(PREF_API_KEY, "") ?: ""
        if (plainKey.isNotBlank() && (securePrefs.getString(PREF_API_KEY, "") ?: "").isBlank()) {
            securePrefs.edit().putString(PREF_API_KEY, plainKey).apply()
            prefs.edit().remove(PREF_API_KEY).apply()
            Log.i(TAG, "Migrated API key to encrypted storage")
        }

        val apiKey = securePrefs.getString(PREF_API_KEY, "") ?: ""
        if (apiKey.isBlank()) {
            Log.d(TAG, "No Groq API key configured — skipping analysis")
            return
        }

        try {
            val db = ObservationDb.getInstance(context)
            val stats = collectStats(db)
            val patch = queryGroq(apiKey, stats)
            lastPatch = patch
            lastError = null

            // Cache the patch
            prefs.edit().putString(PREF_LAST_PATCH, patch.toJson().toString()).apply()
            Log.i(TAG, "Behavior patch applied: $patch")
        } catch (e: Exception) {
            lastError = e.message
            Log.e(TAG, "Analysis failed", e)
        }
    }

    private fun collectStats(db: ObservationDb): AnalysisInput {
        val nodeTimings = db.avgNodeTimings(100)
        val eventCounts = db.eventCountsByType(24)
        val utteranceStats = db.utteranceStats(24)
        return AnalysisInput(nodeTimings, eventCounts, utteranceStats)
    }

    private fun queryGroq(apiKey: String, input: AnalysisInput): BehaviorPatch {
        val prompt = buildPrompt(input)

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", """You are a voice personality tuning engine. Given observation stats from a TTS notification reader app, return a JSON behavior patch to optimize the voice personality. Respond with ONLY valid JSON, no markdown or explanation.""")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 512)
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val conn = URL(GROQ_API_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        conn.doOutput = true

        try {
            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            if (conn.responseCode != 200) {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("Groq API error ${conn.responseCode}: $error")
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            return BehaviorPatch.fromJson(JSONObject(content))
        } finally {
            conn.disconnect()
        }
    }

    private fun buildPrompt(input: AnalysisInput): String = buildString {
        appendLine("Analyze these TTS notification reader stats and suggest parameter adjustments.")
        appendLine()
        appendLine("## DSP Node Timings (avg microseconds, last 100 utterances)")
        if (input.nodeTimings.isEmpty()) {
            appendLine("No data yet.")
        } else {
            input.nodeTimings.forEach { (node, avgUs) -> appendLine("- $node: ${avgUs}us") }
        }
        appendLine()
        appendLine("## Personality Events (last 24h)")
        if (input.eventCounts.isEmpty()) {
            appendLine("No events.")
        } else {
            input.eventCounts.forEach { (type, count) -> appendLine("- $type: $count") }
        }
        appendLine()
        appendLine("## Utterance Stats (last 24h)")
        appendLine("- Count: ${input.utteranceStats.count}")
        appendLine("- Avg DSP time: ${input.utteranceStats.avgDspUs}us")
        appendLine("- Avg text length: ${input.utteranceStats.avgTextLength} chars")
        appendLine("- Avg PCM samples: ${input.utteranceStats.avgPcmSamples}")
        appendLine()
        appendLine("""Return a JSON object with these optional fields:
            |{
            |  "warmth_sensitivity": 0.0-2.0 (multiplier for warmth modulation),
            |  "urgency_sensitivity": 0.0-2.0 (multiplier for urgency response),
            |  "flood_damping": 0.0-1.0 (reduce voice variation when flooded),
            |  "dsp_bypass_nodes": ["node_name"] (disable slow DSP nodes),
            |  "pitch_range_factor": 0.5-1.5 (scale pitch variation),
            |  "rate_range_factor": 0.5-1.5 (scale rate variation),
            |  "reasoning": "brief explanation"
            |}""".trimMargin())
    }

    data class AnalysisInput(
        val nodeTimings: Map<String, Long>,
        val eventCounts: Map<String, Int>,
        val utteranceStats: UtteranceStats
    )
}

/**
 * Behavior patch — parameter adjustments from Custom Core analysis.
 * Applied by VoiceModulator as multipliers on its output.
 */
data class BehaviorPatch(
    val warmthSensitivity: Float = 1.0f,
    val urgencySensitivity: Float = 1.0f,
    val floodDamping: Float = 0.0f,
    val dspBypassNodes: List<String> = emptyList(),
    val pitchRangeFactor: Float = 1.0f,
    val rateRangeFactor: Float = 1.0f,
    val reasoning: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("warmth_sensitivity", warmthSensitivity)
        put("urgency_sensitivity", urgencySensitivity)
        put("flood_damping", floodDamping)
        put("dsp_bypass_nodes", org.json.JSONArray(dspBypassNodes))
        put("pitch_range_factor", pitchRangeFactor)
        put("rate_range_factor", rateRangeFactor)
        put("reasoning", reasoning)
    }

    companion object {
        fun fromJson(json: JSONObject) = BehaviorPatch(
            warmthSensitivity = json.optDouble("warmth_sensitivity", 1.0).toFloat(),
            urgencySensitivity = json.optDouble("urgency_sensitivity", 1.0).toFloat(),
            floodDamping = json.optDouble("flood_damping", 0.0).toFloat(),
            dspBypassNodes = json.optJSONArray("dsp_bypass_nodes")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            pitchRangeFactor = json.optDouble("pitch_range_factor", 1.0).toFloat(),
            rateRangeFactor = json.optDouble("rate_range_factor", 1.0).toFloat(),
            reasoning = json.optString("reasoning", "")
        )
    }
}

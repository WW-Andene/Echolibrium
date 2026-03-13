package com.echolibrium.kyokan

import org.json.JSONObject

/**
 * Data-driven voice configuration — Section 6.7.
 *
 * Moves emotion→DSP mappings, gimmick parameters, and voice selection logic
 * from hardcoded Kotlin into JSON structures owned by VoiceProfile.
 *
 * A VoiceConfig is a JSON blob that fully describes how a voice profile
 * behaves — swap the config, get different behavior, no code changes.
 *
 * Structure:
 *   {
 *     "curves": { ... ExpressionCurveSet ... },
 *     "dspOverrides": { "volume": true, "breathiness": true, ... },
 *     "dspOrder": ["volume", "saturation", "formant_smoothing", ...],
 *     "emotionMap": {
 *       "anger":   { "pitch": "anger_pitch", "speed": "linear", ... },
 *       "sadness": { "pitch": "linear", "speed": "sadness_speed", ... },
 *       ...
 *     },
 *     "durationPreset": "neutral" | "sad" | "excited" | "custom"
 *   }
 */
data class VoiceConfig(
    /** Expression curves for non-linear parameter mapping */
    val curves: ExpressionCurveSet = ExpressionCurveSet.default(),
    /** Which DSP nodes are enabled (by node name) */
    val dspEnabled: Map<String, Boolean> = DEFAULT_DSP_ENABLED,
    /** DSP node processing order (by node name) */
    val dspOrder: List<String> = DEFAULT_DSP_ORDER,
    /** Emotion→curve name mappings per parameter */
    val emotionMap: Map<String, Map<String, String>> = DEFAULT_EMOTION_MAP,
    /** Duration preset name or "custom" for per-phoneme overrides */
    val durationPreset: String = "neutral"
) {
    /**
     * Apply this config to a DspChain — enable/disable nodes and reorder.
     */
    fun applyTo(chain: DspChain) {
        // Enable/disable nodes
        for ((name, enabled) in dspEnabled) {
            chain.find(name)?.enabled = enabled
        }
        // Reorder nodes
        for ((index, name) in dspOrder.withIndex()) {
            chain.reorder(name, index)
        }
    }

    /**
     * Evaluate the appropriate expression curve for a given emotion and parameter.
     *
     * @param emotion Emotion name (e.g., "anger", "sadness")
     * @param parameter Parameter name (e.g., "pitch", "speed", "breathiness")
     * @param intensity Emotion intensity [0..1]
     * @return Mapped parameter value
     */
    fun evaluateCurve(emotion: String, parameter: String, intensity: Float): Float {
        val curveName = emotionMap[emotion]?.get(parameter) ?: return intensity
        return curves.evaluate(curveName, intensity)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("curves", curves.toJson())

        val dspEn = JSONObject()
        for ((k, v) in dspEnabled) dspEn.put(k, v)
        put("dspEnabled", dspEn)

        val order = org.json.JSONArray()
        for (n in dspOrder) order.put(n)
        put("dspOrder", order)

        val emap = JSONObject()
        for ((emotion, params) in emotionMap) {
            val pj = JSONObject()
            for ((param, curve) in params) pj.put(param, curve)
            emap.put(emotion, pj)
        }
        put("emotionMap", emap)

        put("durationPreset", durationPreset)
    }

    companion object {
        val DEFAULT_DSP_ORDER = listOf(
            "volume", "saturation", "formant_smoothing", "breathiness",
            "spectral_tilt", "jitter", "vocal_fry", "trailing_off", "soft_limiter"
        )

        val DEFAULT_DSP_ENABLED = mapOf(
            "volume" to true,
            "saturation" to true,
            "formant_smoothing" to true,
            "breathiness" to true,
            "spectral_tilt" to true,
            "jitter" to true,
            "vocal_fry" to true,
            "trailing_off" to true,
            "soft_limiter" to true,
        )

        val DEFAULT_EMOTION_MAP = mapOf(
            "anger" to mapOf(
                "pitch" to "anger_pitch",
                "speed" to "linear",
                "breathiness" to "breathiness",
                "jitter" to "jitter"
            ),
            "sadness" to mapOf(
                "pitch" to "linear",
                "speed" to "sadness_speed",
                "breathiness" to "breathiness",
                "jitter" to "jitter"
            ),
            "excitement" to mapOf(
                "pitch" to "linear",
                "speed" to "linear",
                "volume" to "excitement_volume",
                "breathiness" to "breathiness"
            ),
            "neutral" to mapOf(
                "pitch" to "linear",
                "speed" to "linear",
                "breathiness" to "breathiness",
                "jitter" to "jitter"
            ),
        )

        fun fromJson(j: JSONObject): VoiceConfig {
            val curves = j.optJSONObject("curves")?.let {
                ExpressionCurveSet.fromJson(it)
            } ?: ExpressionCurveSet.default()

            val dspEn = mutableMapOf<String, Boolean>()
            j.optJSONObject("dspEnabled")?.let { de ->
                for (key in de.keys()) dspEn[key] = de.optBoolean(key, true)
            }

            val dspOrd = mutableListOf<String>()
            j.optJSONArray("dspOrder")?.let { arr ->
                for (i in 0 until arr.length()) dspOrd.add(arr.getString(i))
            }

            val emap = mutableMapOf<String, Map<String, String>>()
            j.optJSONObject("emotionMap")?.let { em ->
                for (emotion in em.keys()) {
                    val params = mutableMapOf<String, String>()
                    em.optJSONObject(emotion)?.let { pj ->
                        for (param in pj.keys()) params[param] = pj.getString(param)
                    }
                    emap[emotion] = params
                }
            }

            return VoiceConfig(
                curves = curves,
                dspEnabled = dspEn.ifEmpty { DEFAULT_DSP_ENABLED },
                dspOrder = dspOrd.ifEmpty { DEFAULT_DSP_ORDER },
                emotionMap = emap.ifEmpty { DEFAULT_EMOTION_MAP },
                durationPreset = j.optString("durationPreset", "neutral")
            )
        }

        fun default() = VoiceConfig()
    }
}

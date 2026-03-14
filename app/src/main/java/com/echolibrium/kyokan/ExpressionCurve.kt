package com.echolibrium.kyokan

import org.json.JSONArray
import org.json.JSONObject

/**
 * Non-linear 8-point lookup table for mapping emotion intensity to DSP parameters.
 *
 * Human vocal expression has thresholds and plateaus — whisper→normal→shout are
 * distinct registers, not a linear gradient. ExpressionCurve captures this by
 * defining 8 control points with linear interpolation between them.
 *
 * Each curve maps an input intensity [0.0..1.0] to an output value [arbitrary range].
 * Points are defined as (input, output) pairs sorted by input.
 *
 * Used by StyleSculptor (Kokoro path) and ScaleMapper (Piper path) as the
 * universal mechanism for emotion→parameter mapping.
 */
data class ExpressionCurve(
    val name: String,
    val points: List<CurvePoint>
) {
    init {
        require(points.size in 2..8) { "ExpressionCurve needs 2-8 points, got ${points.size}" }
    }

    data class CurvePoint(val input: Float, val output: Float)

    /**
     * Evaluate the curve at a given intensity.
     * Clamps to first/last point outside range, linearly interpolates between.
     */
    fun evaluate(intensity: Float): Float {
        if (points.isEmpty()) return 0f
        val t = intensity.coerceIn(0f, 1f)

        // Before first point
        if (t <= points.first().input) return points.first().output
        // After last point
        if (t >= points.last().input) return points.last().output

        // Find surrounding points and interpolate
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (t in a.input..b.input) {
                val span = b.input - a.input
                if (span < 0.0001f) return a.output
                val frac = (t - a.input) / span
                return a.output + (b.output - a.output) * frac
            }
        }
        return points.last().output
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        val arr = JSONArray()
        for (p in points) {
            arr.put(JSONObject().apply {
                put("in", p.input.toDouble())
                put("out", p.output.toDouble())
            })
        }
        put("points", arr)
    }

    companion object {
        fun fromJson(j: JSONObject): ExpressionCurve {
            val arr = j.getJSONArray("points")
            val pts = (0 until arr.length()).map { i ->
                val pt = arr.getJSONObject(i)
                CurvePoint(pt.getDouble("in").toFloat(), pt.getDouble("out").toFloat())
            }
            return ExpressionCurve(j.optString("name", ""), pts)
        }

        /** Linear identity curve: output = input */
        fun linear(name: String): ExpressionCurve = ExpressionCurve(
            name, listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        )

        // ── Preset curves for common emotion→parameter mappings ────────────

        /** Anger pitch: minimal change 0-0.3, jumps to higher register at 0.4+ */
        val ANGER_PITCH = ExpressionCurve("anger_pitch", listOf(
            CurvePoint(0.0f, 1.0f),
            CurvePoint(0.2f, 1.02f),
            CurvePoint(0.3f, 1.03f),
            CurvePoint(0.4f, 1.10f),   // register shift
            CurvePoint(0.6f, 1.18f),
            CurvePoint(0.8f, 1.22f),
            CurvePoint(0.9f, 1.24f),
            CurvePoint(1.0f, 1.25f),   // plateau near max
        ))

        /** Sadness speed: slow roll-off with a floor */
        val SADNESS_SPEED = ExpressionCurve("sadness_speed", listOf(
            CurvePoint(0.0f, 1.0f),
            CurvePoint(0.2f, 0.96f),
            CurvePoint(0.4f, 0.90f),
            CurvePoint(0.6f, 0.84f),
            CurvePoint(0.8f, 0.80f),
            CurvePoint(0.9f, 0.78f),
            CurvePoint(1.0f, 0.76f),   // floor — never slower than 0.76x
        ))

        /** Excitement: compresses dynamic range at high intensity */
        val EXCITEMENT_VOLUME = ExpressionCurve("excitement_volume", listOf(
            CurvePoint(0.0f, 1.0f),
            CurvePoint(0.3f, 1.05f),
            CurvePoint(0.5f, 1.12f),
            CurvePoint(0.7f, 1.18f),
            CurvePoint(0.85f, 1.20f),  // plateau — compressed
            CurvePoint(1.0f, 1.22f),
        ))

        /** Breathiness curve: gentle onset, steep mid-range, soft ceiling */
        val BREATHINESS = ExpressionCurve("breathiness", listOf(
            CurvePoint(0.0f, 0.0f),
            CurvePoint(0.1f, 0.02f),
            CurvePoint(0.3f, 0.08f),
            CurvePoint(0.5f, 0.25f),   // steep
            CurvePoint(0.7f, 0.55f),
            CurvePoint(0.85f, 0.75f),
            CurvePoint(0.95f, 0.88f),
            CurvePoint(1.0f, 0.92f),   // soft ceiling
        ))

        /** Jitter: stays near zero until moderate intensity, then gradual rise */
        val JITTER = ExpressionCurve("jitter", listOf(
            CurvePoint(0.0f, 0.0f),
            CurvePoint(0.3f, 0.005f),
            CurvePoint(0.5f, 0.02f),
            CurvePoint(0.7f, 0.06f),
            CurvePoint(0.85f, 0.10f),
            CurvePoint(1.0f, 0.14f),
        ))

        /** Noise_w (Piper rhythm variation): shaped for natural feel */
        val PIPER_NOISE_W = ExpressionCurve("piper_noise_w", listOf(
            CurvePoint(0.0f, 0.6f),    // flat/monotone baseline
            CurvePoint(0.2f, 0.7f),
            CurvePoint(0.4f, 0.8f),    // default
            CurvePoint(0.6f, 0.95f),
            CurvePoint(0.8f, 1.1f),
            CurvePoint(1.0f, 1.3f),
        ))

        /** Noise_scale (Piper breathiness): matches BREATHINESS shape */
        val PIPER_NOISE_SCALE = ExpressionCurve("piper_noise_scale", listOf(
            CurvePoint(0.0f, 0.4f),
            CurvePoint(0.2f, 0.5f),
            CurvePoint(0.4f, 0.667f),  // default
            CurvePoint(0.6f, 0.8f),
            CurvePoint(0.8f, 0.95f),
            CurvePoint(1.0f, 1.1f),
        ))

        /** All presets as a map for lookup by name */
        val PRESETS: Map<String, ExpressionCurve> = mapOf(
            "anger_pitch" to ANGER_PITCH,
            "sadness_speed" to SADNESS_SPEED,
            "excitement_volume" to EXCITEMENT_VOLUME,
            "breathiness" to BREATHINESS,
            "jitter" to JITTER,
            "piper_noise_w" to PIPER_NOISE_W,
            "piper_noise_scale" to PIPER_NOISE_SCALE,
        )
    }
}

/**
 * A collection of ExpressionCurves used by a VoiceProfile's ExpressionMap.
 * Maps parameter names to their response curves.
 */
data class ExpressionCurveSet(
    val curves: Map<String, ExpressionCurve> = ExpressionCurve.PRESETS
) {
    fun evaluate(curveName: String, intensity: Float): Float =
        curves[curveName]?.evaluate(intensity) ?: intensity

    fun toJson(): JSONObject = JSONObject().apply {
        for ((key, curve) in curves) put(key, curve.toJson())
    }

    companion object {
        fun fromJson(j: JSONObject): ExpressionCurveSet {
            val map = mutableMapOf<String, ExpressionCurve>()
            for (key in j.keys()) {
                map[key] = ExpressionCurve.fromJson(j.getJSONObject(key))
            }
            return ExpressionCurveSet(map)
        }

        /** Default set using all built-in presets */
        fun default() = ExpressionCurveSet(ExpressionCurve.PRESETS)
    }
}

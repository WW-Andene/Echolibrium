package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI

/**
 * First-class per-utterance parameter trajectory curves.
 *
 * A ParameterTrajectory describes how a DSP parameter evolves over the
 * duration of a single utterance. Instead of applying a constant value,
 * the parameter value is sampled at each point in time.
 *
 * This turns the existing Trajectory enum (FLAT, BUILDING, PEAKED, COLLAPSED)
 * into concrete time-varying curves that modulate DSP parameters like
 * breathiness, jitter, volume, and pitch across the utterance.
 *
 * Usage:
 *   val traj = ParameterTrajectory.fromSignal(signal)
 *   for (i in samples.indices) {
 *       val t = i.toFloat() / samples.size   // 0.0 → 1.0
 *       val mod = traj.evaluate("breathiness", t)
 *       samples[i] *= mod
 *   }
 */
data class ParameterTrajectory(
    /** The base trajectory shape */
    val shape: Trajectory,
    /** Per-parameter curve definitions */
    val curves: Map<String, TrajectoryShape>
) {
    /**
     * Evaluate a parameter's trajectory value at normalized time t [0..1].
     * Returns a multiplier (1.0 = no change).
     */
    fun evaluate(parameter: String, t: Float): Float {
        val curve = curves[parameter] ?: return 1f
        return curve.evaluate(t.coerceIn(0f, 1f))
    }

    companion object {
        private const val TAG = "ParameterTrajectory"
        private var configJson: JSONObject? = null

        /** Load trajectory config from assets. Call once during init. */
        fun loadConfig(ctx: Context) {
            try {
                val json = ctx.assets.open("trajectory_config.json")
                    .bufferedReader().use { it.readText() }
                configJson = JSONObject(json)
                Log.i(TAG, "Loaded trajectory config from assets")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load trajectory_config.json, using defaults", e)
            }
        }

        /** Build a ParameterTrajectory from the current signal context */
        fun fromSignal(signal: SignalMap, modulated: ModulatedVoice): ParameterTrajectory {
            val shape = signal.trajectory
            val curves = mutableMapOf<String, TrajectoryShape>()

            val cfg = configJson?.optJSONObject(shape.name.lowercase())
            if (cfg != null) {
                // Load from config file
                for (key in cfg.keys()) {
                    val paramCfg = cfg.getJSONObject(key)
                    parseCurve(paramCfg)?.let { curves[key] = it }
                }
            } else {
                // Hardcoded fallback
                when (shape) {
                    Trajectory.FLAT -> { }
                    Trajectory.BUILDING -> {
                        curves["volume"] = TrajectoryShape.Ramp(0.92f, 1.08f)
                        curves["breathiness"] = TrajectoryShape.Ramp(0.7f, 1.3f)
                        curves["jitter"] = TrajectoryShape.Ramp(0.5f, 1.5f)
                        curves["pitch"] = TrajectoryShape.Ramp(0.98f, 1.04f)
                        curves["speed"] = TrajectoryShape.Ramp(0.95f, 1.1f)
                    }
                    Trajectory.PEAKED -> {
                        curves["volume"] = TrajectoryShape.Peak(0.6f, 1.15f, 0.95f)
                        curves["breathiness"] = TrajectoryShape.Peak(0.5f, 1.4f, 0.8f)
                        curves["jitter"] = TrajectoryShape.Peak(0.7f, 1.6f, 0.6f)
                        curves["pitch"] = TrajectoryShape.Peak(0.6f, 1.06f, 0.98f)
                    }
                    Trajectory.COLLAPSED -> {
                        curves["volume"] = TrajectoryShape.Decay(1.1f, 0.75f, 3f)
                        curves["breathiness"] = TrajectoryShape.Ramp(0.8f, 1.5f)
                        curves["speed"] = TrajectoryShape.Decay(1.05f, 0.8f, 2f)
                        curves["jitter"] = TrajectoryShape.Ramp(0.5f, 1.8f)
                        curves["pitch"] = TrajectoryShape.Decay(1.02f, 0.94f, 2.5f)
                    }
                }
            }

            // Intensity scaling: stronger signal = more pronounced trajectories
            if (signal.intensityLevel > 0.5f) {
                val boost = 1f + (signal.intensityLevel - 0.5f) * 0.5f
                for ((key, curve) in curves) {
                    curves[key] = curve.scaled(boost)
                }
            }

            return ParameterTrajectory(shape, curves)
        }

        private fun parseCurve(json: JSONObject): TrajectoryShape? {
            return when (json.optString("type")) {
                "ramp" -> TrajectoryShape.Ramp(
                    json.optDouble("start", 1.0).toFloat(),
                    json.optDouble("end", 1.0).toFloat()
                )
                "peak" -> TrajectoryShape.Peak(
                    json.optDouble("peakPosition", 0.5).toFloat(),
                    json.optDouble("peakValue", 1.0).toFloat(),
                    json.optDouble("restValue", 1.0).toFloat(),
                    json.optDouble("width", 0.3).toFloat()
                )
                "decay" -> TrajectoryShape.Decay(
                    json.optDouble("start", 1.0).toFloat(),
                    json.optDouble("end", 1.0).toFloat(),
                    json.optDouble("decayRate", 3.0).toFloat()
                )
                "oscillation" -> TrajectoryShape.Oscillation(
                    json.optDouble("center", 1.0).toFloat(),
                    json.optDouble("amplitude", 0.05).toFloat(),
                    json.optDouble("frequency", 3.0).toFloat()
                )
                "constant" -> TrajectoryShape.Constant(
                    json.optDouble("value", 1.0).toFloat()
                )
                else -> null
            }
        }

        /** No trajectory — all parameters constant */
        val FLAT = ParameterTrajectory(Trajectory.FLAT, emptyMap())
    }
}

/**
 * Shape of a single parameter's evolution over time [0..1].
 */
sealed class TrajectoryShape {
    abstract fun evaluate(t: Float): Float

    /** Scale the effect magnitude while keeping center at 1.0 */
    abstract fun scaled(factor: Float): TrajectoryShape

    /** Linear ramp from start to end value */
    data class Ramp(val startValue: Float, val endValue: Float) : TrajectoryShape() {
        override fun evaluate(t: Float): Float = startValue + (endValue - startValue) * t
        override fun scaled(factor: Float): TrajectoryShape = Ramp(
            1f + (startValue - 1f) * factor,
            1f + (endValue - 1f) * factor
        )
    }

    /** Bell curve peaking at peakPosition */
    data class Peak(
        val peakPosition: Float,
        val peakValue: Float,
        val restValue: Float,
        val width: Float = 0.3f
    ) : TrajectoryShape() {
        override fun evaluate(t: Float): Float {
            val dist = (t - peakPosition) / width
            val bell = exp((-dist * dist).toDouble()).toFloat()
            return restValue + (peakValue - restValue) * bell
        }
        override fun scaled(factor: Float): TrajectoryShape = Peak(
            peakPosition, 1f + (peakValue - 1f) * factor,
            1f + (restValue - 1f) * factor, width
        )
    }

    /** Exponential decay from startValue toward endValue */
    data class Decay(
        val startValue: Float,
        val endValue: Float,
        val decayRate: Float = 3f
    ) : TrajectoryShape() {
        override fun evaluate(t: Float): Float {
            val decay = exp((-decayRate * t).toDouble()).toFloat()
            return endValue + (startValue - endValue) * decay
        }
        override fun scaled(factor: Float): TrajectoryShape = Decay(
            1f + (startValue - 1f) * factor,
            1f + (endValue - 1f) * factor,
            decayRate
        )
    }

    /** Sine wave oscillation around center */
    data class Oscillation(
        val center: Float = 1f,
        val amplitude: Float = 0.05f,
        val frequency: Float = 3f // cycles per utterance
    ) : TrajectoryShape() {
        override fun evaluate(t: Float): Float =
            center + amplitude * sin(2.0 * PI * frequency * t).toFloat()
        override fun scaled(factor: Float): TrajectoryShape = Oscillation(
            center, amplitude * factor, frequency
        )
    }

    /** Constant value — no time variation */
    data class Constant(val value: Float = 1f) : TrajectoryShape() {
        override fun evaluate(t: Float): Float = value
        override fun scaled(factor: Float): TrajectoryShape = Constant(
            1f + (value - 1f) * factor
        )
    }
}

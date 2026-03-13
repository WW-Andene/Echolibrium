package com.echolibrium.kyokan

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
        /** Build a ParameterTrajectory from the current signal context */
        fun fromSignal(signal: SignalMap, modulated: ModulatedVoice): ParameterTrajectory {
            val shape = signal.trajectory
            val curves = mutableMapOf<String, TrajectoryShape>()

            when (shape) {
                Trajectory.FLAT -> {
                    // Constant — no time variation
                }
                Trajectory.BUILDING -> {
                    // Intensity builds over the utterance
                    curves["volume"] = TrajectoryShape.Ramp(0.92f, 1.08f)
                    curves["breathiness"] = TrajectoryShape.Ramp(0.7f, 1.3f)
                    curves["jitter"] = TrajectoryShape.Ramp(0.5f, 1.5f)
                    curves["pitch"] = TrajectoryShape.Ramp(0.98f, 1.04f)
                    curves["speed"] = TrajectoryShape.Ramp(0.95f, 1.1f)
                }
                Trajectory.PEAKED -> {
                    // Peaks in the middle, drops at the end
                    curves["volume"] = TrajectoryShape.Peak(peakPosition = 0.6f, peakValue = 1.15f, restValue = 0.95f)
                    curves["breathiness"] = TrajectoryShape.Peak(peakPosition = 0.5f, peakValue = 1.4f, restValue = 0.8f)
                    curves["jitter"] = TrajectoryShape.Peak(peakPosition = 0.7f, peakValue = 1.6f, restValue = 0.6f)
                    curves["pitch"] = TrajectoryShape.Peak(peakPosition = 0.6f, peakValue = 1.06f, restValue = 0.98f)
                }
                Trajectory.COLLAPSED -> {
                    // Starts strong, decays to exhaustion
                    curves["volume"] = TrajectoryShape.Decay(startValue = 1.1f, endValue = 0.75f, decayRate = 3f)
                    curves["breathiness"] = TrajectoryShape.Ramp(0.8f, 1.5f) // gets breathier
                    curves["speed"] = TrajectoryShape.Decay(startValue = 1.05f, endValue = 0.8f, decayRate = 2f)
                    curves["jitter"] = TrajectoryShape.Ramp(0.5f, 1.8f) // voice breaks down
                    curves["pitch"] = TrajectoryShape.Decay(startValue = 1.02f, endValue = 0.94f, decayRate = 2.5f)
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

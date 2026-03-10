package com.kokoro.reader

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Applies DSP effects directly to PCM FloatArray from SherpaEngine.
 *
 * Effects applied in order:
 *   1. Soft saturation — smooths harsh transitions and cross-language artifacts
 *   2. Formant smoothing — reduces abrupt frequency jumps between syllables
 *   3. Breathiness — mix a filtered breath-noise layer into the speech signal
 *   4. Spectral tilt — low-pass proportional to breathiness (§5.2)
 *   5. Jitter — micro-amplitude variation for humanizing (§5.1)
 *   6. Vocal fry — phrase-end amplitude modulation (§5.3)
 *   7. Trailing off — fade-out on fatigued/collapsed states (§4.3B)
 *
 * All effects are applied to the same FloatArray and normalised to [-1, 1].
 */
object AudioDsp {

    /**
     * Apply all DSP effects from a ModulatedVoice to raw PCM samples.
     *
     * @param samples      Raw float PCM from SherpaEngine (-1.0 to 1.0)
     * @param sampleRate   Sample rate in Hz (typically 22050)
     * @param modulated    Modulated voice params (has breath intensity etc.)
     */
    fun apply(
        samples: FloatArray,
        sampleRate: Int,
        modulated: ModulatedVoice
    ): FloatArray {
        if (samples.isEmpty()) return samples
        if (sampleRate <= 0) return samples
        return try {
            applyInternal(samples, sampleRate, modulated)
        } catch (e: Exception) {
            android.util.Log.e("AudioDsp", "Error in DSP pipeline", e)
            samples.copyOf()  // Return unmodified copy on error
        }
    }

    private fun applyInternal(
        samples: FloatArray,
        sampleRate: Int,
        modulated: ModulatedVoice
    ): FloatArray {
        var pcm = samples.copyOf()

        // 1. Soft saturation
        pcm = applySoftSaturation(pcm)

        // 2. Formant smoothing
        pcm = applyFormantSmoothing(pcm, sampleRate)

        // 3. Breathiness noise mixing
        if (modulated.breathIntensity >= 5) {
            pcm = applyBreathiness(pcm, sampleRate, modulated.breathIntensity)
        }

        // 4. Spectral tilt — proportional to breathiness (§5.2)
        if (modulated.breathIntensity >= 5) {
            pcm = applySpectralTilt(pcm, sampleRate, modulated.breathIntensity / 100f)
        }

        // 5. Jitter simulation (§5.1)
        if (modulated.jitterAmount > 0.01f) {
            pcm = applyJitter(pcm, sampleRate, modulated.jitterAmount)
        }

        // 6. Vocal fry at phrase endings (§5.3)
        if (modulated.shouldTrailOff) {
            pcm = applyVocalFry(pcm, sampleRate)
        }

        // 7. Trailing off — PCM fade (§4.3B)
        if (modulated.shouldTrailOff) {
            pcm = applyTrailingOff(pcm)
        }

        return pcm
    }

    // ── Soft saturation ───────────────────────────────────────────────────────
    private fun applySoftSaturation(samples: FloatArray): FloatArray {
        val drive = 1.2f
        return FloatArray(samples.size) { i ->
            tanh((samples[i] * drive).toDouble()).toFloat()
        }
    }

    // ── Formant smoothing ─────────────────────────────────────────────────────
    private fun applyFormantSmoothing(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty()) return samples
        val windowSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1)
        val alpha = 1.0f / windowSamples

        val envelope = FloatArray(samples.size)
        envelope[0] = abs(samples[0])
        for (i in 1 until samples.size) {
            val amp = abs(samples[i])
            envelope[i] = envelope[i - 1] + alpha * (amp - envelope[i - 1])
        }

        return FloatArray(samples.size) { i ->
            val originalAmp = abs(samples[i])
            if (originalAmp > 0.001f) {
                val ratio = envelope[i] / originalAmp
                val blended = 0.7f + 0.3f * ratio.coerceIn(0.5f, 2.0f)
                (samples[i] * blended).coerceIn(-1f, 1f)
            } else {
                samples[i]
            }
        }
    }

    // ── Breathiness ───────────────────────────────────────────────────────────
    private fun applyBreathiness(
        samples: FloatArray,
        sampleRate: Int,
        intensity: Int
    ): FloatArray {
        val curve = (intensity / 100f) * (intensity / 100f)
        val noiseLevel = curve * 0.18f
        val alpha = 0.55f
        var prevNoise = 0f

        val envWindow = (sampleRate * 0.02).toInt().coerceAtLeast(1)
        val envelope = computeEnvelope(samples, envWindow)

        return FloatArray(samples.size) { i ->
            val rawNoise = Random.nextFloat() * 2f - 1f
            prevNoise = prevNoise * (1f - alpha) + rawNoise * alpha
            val breathNoise = prevNoise * noiseLevel
            val envShaped = breathNoise * (0.3f + envelope[i] * 0.7f)
            (samples[i] + envShaped).coerceIn(-1f, 1f)
        }
    }

    // ── Spectral tilt filter (§5.2) ───────────────────────────────────────────
    // Low-pass that activates proportionally to breathiness.
    // Real breathiness attenuates high frequencies (steep spectral tilt).
    // Applied AFTER noise-mixing so it shapes both speech and added noise.
    private fun applySpectralTilt(
        samples: FloatArray,
        sampleRate: Int,
        breathiness: Float
    ): FloatArray {
        if (breathiness < 0.1f) return samples
        val normalizedBreath = breathiness.coerceIn(0f, 1f)
        val cutoffHz = lerp(8000f, 2500f, normalizedBreath)
        val alpha = exp(-2.0 * PI * cutoffHz / sampleRate).toFloat()
        val result = samples.copyOf()
        var prev = 0f
        for (i in result.indices) {
            result[i] = (1f - alpha) * result[i] + alpha * prev
            prev = result[i]
        }
        return result
    }

    // ── Jitter simulation (§5.1) ──────────────────────────────────────────────
    // Applies small, randomized amplitude modulations at speech-rate timescales
    // (~8ms windows) to approximate micro-F0 variation. Produces amplitude jitter
    // that perceptually correlates with vocal roughness/humanness.
    private fun applyJitter(
        samples: FloatArray,
        sampleRate: Int,
        jitterAmount: Float
    ): FloatArray {
        val windowMs = 8
        val windowSamples = (sampleRate * windowMs / 1000).coerceAtLeast(1)
        val result = samples.copyOf()
        val rng = Random
        var i = 0
        while (i < result.size) {
            val gain = 1f + (rng.nextFloat() - 0.5f) * 2f * jitterAmount
            val end = minOf(i + windowSamples, result.size)
            for (j in i until end) result[j] = (result[j] * gain).coerceIn(-1f, 1f)
            i = end
        }
        return result
    }

    // ── Vocal fry at phrase endings (§5.3) ────────────────────────────────────
    // Applies low-frequency amplitude modulation (30–70Hz) to the final ~200ms
    // to approximate creaky voice / pulse register at phrase ends.
    private fun applyVocalFry(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.size < sampleRate / 5) return samples  // skip very short samples
        val fryDurationSamples = (sampleRate * 0.2f).toInt()  // last 200ms
        val fryStart = (samples.size - fryDurationSamples).coerceAtLeast(0)
        val fryFreq = 45f  // Hz, typical vocal fry rate
        val result = samples.copyOf()
        for (i in fryStart until result.size) {
            val t = (i - fryStart).toFloat() / sampleRate
            val fryGain = 0.5f + 0.5f * sin(2.0 * PI * fryFreq * t).toFloat()
            val fadeFactor = 1f - ((i - fryStart).toFloat() / (result.size - fryStart).coerceAtLeast(1)) * 0.3f
            result[i] = (result[i] * fryGain * fadeFactor).coerceIn(-1f, 1f)
        }
        return result
    }

    // ── Trailing off (§4.3B) ──────────────────────────────────────────────────
    // Linear fade-out on the last 15% of the PCM buffer for fatigued/collapsed states.
    private fun applyTrailingOff(samples: FloatArray): FloatArray {
        val fadeStart = (samples.size * 0.85f).toInt()
        if (fadeStart >= samples.size) return samples
        val result = samples.copyOf()
        for (i in fadeStart until result.size) {
            val factor = 1f - ((i - fadeStart).toFloat() / (result.size - fadeStart).coerceAtLeast(1))
            result[i] *= factor.coerceAtLeast(0f)
        }
        return result
    }

    // Compute per-sample RMS envelope (normalised 0-1) with a trailing sliding window
    private fun computeEnvelope(samples: FloatArray, windowSize: Int): FloatArray {
        val envelope = FloatArray(samples.size)
        var sumSq = 0f
        var maxRms = 0f

        for (i in samples.indices) {
            sumSq += samples[i] * samples[i]
            if (i >= windowSize) {
                sumSq -= samples[i - windowSize] * samples[i - windowSize]
            }
            sumSq = sumSq.coerceAtLeast(0f)
            val count = minOf(i + 1, windowSize)
            val rms = sqrt(sumSq / count)
            envelope[i] = rms
            if (rms > maxRms) maxRms = rms
        }

        return if (maxRms > 0f) {
            FloatArray(envelope.size) { i -> (envelope[i] / maxRms).coerceIn(0f, 1f) }
        } else envelope
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
}

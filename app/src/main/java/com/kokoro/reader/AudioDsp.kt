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
 * All effects are applied in-place on a single working copy and normalised to [-1, 1].
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
        // Single allocation — all effects modify this array in-place
        val pcm = samples.copyOf()

        // 0. Volume/gain (§5.4) — applied first so all subsequent effects
        //    operate on the correctly-scaled signal
        if (modulated.volume != 1.0f) {
            applyVolume(pcm, modulated.volume)
        }

        // 1. Soft saturation
        applySoftSaturation(pcm)

        // 2. Formant smoothing
        applyFormantSmoothing(pcm, sampleRate)

        // 3. Breathiness noise mixing
        if (modulated.breathIntensity >= 5) {
            applyBreathiness(pcm, sampleRate, modulated.breathIntensity)
        }

        // 4. Spectral tilt — proportional to breathiness (§5.2)
        if (modulated.breathIntensity >= 5) {
            applySpectralTilt(pcm, sampleRate, modulated.breathIntensity / 100f)
        }

        // 5. Jitter simulation (§5.1)
        if (modulated.jitterAmount > 0.01f) {
            applyJitter(pcm, sampleRate, modulated.jitterAmount)
        }

        // 6. Vocal fry at phrase endings (§5.3)
        if (modulated.shouldTrailOff) {
            applyVocalFry(pcm, sampleRate)
        }

        // 7. Trailing off — PCM fade (§4.3B)
        if (modulated.shouldTrailOff) {
            applyTrailingOff(pcm)
        }

        return pcm
    }

    // ── Volume/gain (§5.4) ─────────────────────────────────────────────────────
    private fun applyVolume(pcm: FloatArray, volume: Float) {
        for (i in pcm.indices) {
            pcm[i] = (pcm[i] * volume).coerceIn(-1f, 1f)
        }
    }

    // ── Soft saturation ───────────────────────────────────────────────────────
    private fun applySoftSaturation(pcm: FloatArray) {
        val drive = 1.2f
        for (i in pcm.indices) {
            pcm[i] = tanh((pcm[i] * drive).toDouble()).toFloat()
        }
    }

    // ── Formant smoothing ─────────────────────────────────────────────────────
    private fun applyFormantSmoothing(pcm: FloatArray, sampleRate: Int) {
        if (pcm.isEmpty()) return
        val windowSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1)
        val alpha = 1.0f / windowSamples

        // Envelope needs a temporary array (unavoidable — used as lookup)
        val envelope = FloatArray(pcm.size)
        envelope[0] = abs(pcm[0])
        for (i in 1 until pcm.size) {
            val amp = abs(pcm[i])
            envelope[i] = envelope[i - 1] + alpha * (amp - envelope[i - 1])
        }

        for (i in pcm.indices) {
            val originalAmp = abs(pcm[i])
            if (originalAmp > 0.001f) {
                val ratio = envelope[i] / originalAmp
                val blended = 0.7f + 0.3f * ratio.coerceIn(0.5f, 2.0f)
                pcm[i] = (pcm[i] * blended).coerceIn(-1f, 1f)
            }
        }
    }

    // ── Breathiness ───────────────────────────────────────────────────────────
    private fun applyBreathiness(
        pcm: FloatArray,
        sampleRate: Int,
        intensity: Int
    ) {
        val curve = (intensity / 100f) * (intensity / 100f)
        val noiseLevel = curve * 0.18f
        val alpha = 0.55f
        var prevNoise = 0f

        val envWindow = (sampleRate * 0.02).toInt().coerceAtLeast(1)
        val envelope = computeEnvelope(pcm, envWindow)

        for (i in pcm.indices) {
            val rawNoise = Random.nextFloat() * 2f - 1f
            prevNoise = prevNoise * (1f - alpha) + rawNoise * alpha
            val breathNoise = prevNoise * noiseLevel
            val envShaped = breathNoise * (0.3f + envelope[i] * 0.7f)
            pcm[i] = (pcm[i] + envShaped).coerceIn(-1f, 1f)
        }
    }

    // ── Spectral tilt filter (§5.2) ───────────────────────────────────────────
    // Low-pass that activates proportionally to breathiness.
    // Real breathiness attenuates high frequencies (steep spectral tilt).
    // Applied AFTER noise-mixing so it shapes both speech and added noise.
    private fun applySpectralTilt(
        pcm: FloatArray,
        sampleRate: Int,
        breathiness: Float
    ) {
        if (breathiness < 0.1f) return
        val normalizedBreath = breathiness.coerceIn(0f, 1f)
        val cutoffHz = lerp(8000f, 2500f, normalizedBreath)
        val alpha = exp(-2.0 * PI * cutoffHz / sampleRate).toFloat()
        var prev = 0f
        for (i in pcm.indices) {
            pcm[i] = (1f - alpha) * pcm[i] + alpha * prev
            prev = pcm[i]
        }
    }

    // ── Jitter simulation (§5.1) ──────────────────────────────────────────────
    // Applies small, randomized amplitude modulations at speech-rate timescales
    // (~8ms windows) to approximate micro-F0 variation. Produces amplitude jitter
    // that perceptually correlates with vocal roughness/humanness.
    private fun applyJitter(
        pcm: FloatArray,
        sampleRate: Int,
        jitterAmount: Float
    ) {
        val windowMs = 8
        val windowSamples = (sampleRate * windowMs / 1000).coerceAtLeast(1)
        val rng = Random
        var i = 0
        while (i < pcm.size) {
            val gain = 1f + (rng.nextFloat() - 0.5f) * 2f * jitterAmount
            val end = minOf(i + windowSamples, pcm.size)
            for (j in i until end) pcm[j] = (pcm[j] * gain).coerceIn(-1f, 1f)
            i = end
        }
    }

    // ── Vocal fry at phrase endings (§5.3) ────────────────────────────────────
    // Pulse-based fry: short glottal bursts (~30% duty cycle) separated by
    // near-silence at 30-50Hz, with slight timing irregularity. This produces
    // the characteristic "creaky" sound of vocal fry / pulse register.
    private fun applyVocalFry(pcm: FloatArray, sampleRate: Int) {
        if (pcm.size < sampleRate / 5) return  // skip very short samples
        val fryDurationSamples = (sampleRate * 0.2f).toInt()  // last 200ms
        val fryStart = (pcm.size - fryDurationSamples).coerceAtLeast(0)
        val basePeriodSamples = sampleRate / 40  // ~40Hz base pulse rate
        val dutyCycle = 0.30f  // glottis open 30% of each cycle
        val rng = Random
        var cyclePos = 0  // position within current pulse cycle
        var currentPeriod = basePeriodSamples  // current cycle length (varies)
        val openSamples get() = (currentPeriod * dutyCycle).toInt().coerceAtLeast(1)

        for (i in fryStart until pcm.size) {
            val progress = (i - fryStart).toFloat() / (pcm.size - fryStart).coerceAtLeast(1)
            val fadeFactor = 1f - progress * 0.3f  // gradual fade

            val gain = if (cyclePos < openSamples) {
                // Glottis open — let signal through with slight attenuation
                0.7f + 0.3f * (1f - cyclePos.toFloat() / openSamples)
            } else {
                // Glottis closed — near-silence
                0.05f
            }

            pcm[i] = (pcm[i] * gain * fadeFactor).coerceIn(-1f, 1f)
            cyclePos++

            // End of cycle — start new pulse with slight timing jitter
            if (cyclePos >= currentPeriod) {
                cyclePos = 0
                // ±15% random period variation for irregular pulse timing
                val jitter = 1f + (rng.nextFloat() - 0.5f) * 0.30f
                currentPeriod = (basePeriodSamples * jitter).toInt().coerceAtLeast(1)
            }
        }
    }

    // ── Trailing off (§4.3B) ──────────────────────────────────────────────────
    // Linear fade-out on the last 15% of the PCM buffer for fatigued/collapsed states.
    private fun applyTrailingOff(pcm: FloatArray) {
        val fadeStart = (pcm.size * 0.85f).toInt()
        if (fadeStart >= pcm.size) return
        for (i in fadeStart until pcm.size) {
            val factor = 1f - ((i - fadeStart).toFloat() / (pcm.size - fadeStart).coerceAtLeast(1))
            pcm[i] *= factor.coerceAtLeast(0f)
        }
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

        if (maxRms > 0f) {
            for (i in envelope.indices) {
                envelope[i] = (envelope[i] / maxRms).coerceIn(0f, 1f)
            }
        }
        return envelope
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
}

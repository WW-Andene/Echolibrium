package com.kokoro.reader

import kotlin.math.abs
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
        var pcm = samples.copyOf()

        // Soft saturation: tames harsh peaks from cross-language synthesis artifacts.
        // Always applied (lightweight) — smooths transitions that sound like
        // breathing-like glitches when a voice speaks a non-native language.
        pcm = applySoftSaturation(pcm)

        // Formant smoothing: gently low-passes rapid amplitude changes between
        // syllables so pitch curves feel continuous rather than stepped.
        pcm = applyFormantSmoothing(pcm, sampleRate)

        if (modulated.breathIntensity >= 5) {
            pcm = applyBreathiness(pcm, sampleRate, modulated.breathIntensity)
        }
        return pcm
    }

    // ── Soft saturation ───────────────────────────────────────────────────────
    // Uses tanh waveshaping to gently compress peaks.
    // Voices that struggle on non-native phonemes often produce transient spikes
    // that sound like breathing or clicking; soft-clipping rounds them off while
    // keeping the overall signal intact.
    //
    private fun applySoftSaturation(samples: FloatArray): FloatArray {
        // Drive: how aggressively we push into the tanh curve.
        // 1.2 is very gentle — barely audible on clean speech, but catches
        // the harsh spikes from cross-language artifacts.
        val drive = 1.2f
        return FloatArray(samples.size) { i ->
            tanh((samples[i] * drive).toDouble()).toFloat()
        }
    }

    // ── Formant smoothing ─────────────────────────────────────────────────────
    // A simple single-pole IIR low-pass on the amplitude envelope, then
    // re-applies that smoothed envelope to the original signal's phase.
    // This makes pitch transitions within syllables feel like a curve
    // rather than abrupt steps, particularly for Piper voices that produce
    // slightly robotic transitions between phonemes.
    //
    private fun applyFormantSmoothing(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty()) return samples

        // 5ms window: at 22050 Hz → ~110 samples, at 24000 Hz → ~120 samples
        // Fast enough to preserve consonants, slow enough to smooth vowel-to-vowel
        // transitions that cause artifacts in non-native phoneme synthesis
        val windowSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1)
        val alpha = 1.0f / windowSamples  // IIR coefficient

        // Pass 1: extract instantaneous amplitude envelope
        val envelope = FloatArray(samples.size)
        envelope[0] = abs(samples[0])
        for (i in 1 until samples.size) {
            val amp = abs(samples[i])
            envelope[i] = envelope[i - 1] + alpha * (amp - envelope[i - 1])
        }

        // Pass 2: re-shape original signal by the smoothed envelope
        // Blend ratio: 70% original signal + 30% envelope-shaped
        // Ratio clamped to [0.5, 2.0] — halve at most, double at most —
        // to avoid over-amplifying silence or crushing peaks
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
    // Mix a low-amplitude breath-noise layer into the speech signal.
    //
    // The noise is IIR low-pass filtered to sound like breath (air passing
    // through a throat) rather than electrical hiss. The breath layer is
    // shaped by the speech envelope — it's louder when the voice is loud,
    // quieter during silence. This creates a natural "whispery" quality.
    //
    private fun applyBreathiness(
        samples: FloatArray,
        sampleRate: Int,
        intensity: Int
    ): FloatArray {
        // Quadratic curve: intensity 5→10 = very subtle, 100 = clearly whispery
        val curve = (intensity / 100f) * (intensity / 100f)
        val noiseLevel = curve * 0.18f  // max ~18% noise — above that sounds broken

        // IIR low-pass to make noise sound like breath, not static
        // Cutoff ~3000Hz gives a "breathy air" quality
        val alpha = 0.55f  // IIR coefficient for ~3kHz at 22050Hz
        var prevNoise = 0f

        // Compute RMS envelope of the speech signal (smoothed)
        val envWindow = (sampleRate * 0.02).toInt().coerceAtLeast(1)  // 20ms window
        val envelope = computeEnvelope(samples, envWindow)

        return FloatArray(samples.size) { i ->
            // Low-pass filtered noise
            val rawNoise = Random.nextFloat() * 2f - 1f
            prevNoise = prevNoise * (1f - alpha) + rawNoise * alpha
            val breathNoise = prevNoise * noiseLevel

            // Shape breath by speech envelope — breath sounds loudest where speech is
            val envShaped = breathNoise * (0.3f + envelope[i] * 0.7f)

            (samples[i] + envShaped).coerceIn(-1f, 1f)
        }
    }

    // Compute per-sample RMS envelope (normalised 0-1) with a trailing sliding window
    private fun computeEnvelope(samples: FloatArray, windowSize: Int): FloatArray {
        val envelope = FloatArray(samples.size)
        var sumSq = 0f
        var maxRms = 0f

        for (i in samples.indices) {
            // Add the incoming sample
            sumSq += samples[i] * samples[i]
            // Remove the sample that has fallen out of the window
            if (i >= windowSize) {
                sumSq -= samples[i - windowSize] * samples[i - windowSize]
            }
            // Guard against floating-point underflow producing a negative sumSq
            sumSq = sumSq.coerceAtLeast(0f)
            // During the first windowSize samples the window hasn't filled yet,
            // so divide by the actual number of samples accumulated so far.
            val count = minOf(i + 1, windowSize)
            val rms = sqrt(sumSq / count)
            envelope[i] = rms
            if (rms > maxRms) maxRms = rms
        }

        // Normalize envelope to 0-1
        return if (maxRms > 0f) {
            FloatArray(envelope.size) { i -> (envelope[i] / maxRms).coerceIn(0f, 1f) }
        } else envelope
    }
}

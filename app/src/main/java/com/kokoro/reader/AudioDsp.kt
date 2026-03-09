package com.kokoro.reader

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Applies DSP effects directly to PCM FloatArray from SherpaEngine.
 *
 * This is what breathiness ACTUALLY sounds like — mixing a filtered
 * breath-noise layer into the synthesized audio, not text manipulation.
 *
 * All effects are applied in order to the same FloatArray.
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
        if (modulated.breathIntensity >= 5) {
            pcm = applyBreathiness(pcm, sampleRate, modulated.breathIntensity)
        }
        return pcm
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

    // Compute per-sample RMS envelope (normalised 0-1) with a smoothing window
    private fun computeEnvelope(samples: FloatArray, windowSize: Int): FloatArray {
        val envelope = FloatArray(samples.size)
        var sumSq = 0f
        val half = windowSize / 2

        // Seed with first window
        for (i in 0 until minOf(windowSize, samples.size)) {
            sumSq += samples[i] * samples[i]
        }
        var maxRms = 0f

        for (i in samples.indices) {
            // Slide window
            if (i >= half && i - half < samples.size) {
                sumSq -= samples[i - half] * samples[i - half]
            }
            val addIdx = i + half
            if (addIdx < samples.size) {
                sumSq += samples[addIdx] * samples[addIdx]
            }
            val rms = sqrt((sumSq / windowSize).coerceAtLeast(0f))
            envelope[i] = rms
            if (rms > maxRms) maxRms = rms
        }

        // Normalize envelope to 0-1
        return if (maxRms > 0f) {
            FloatArray(envelope.size) { i -> (envelope[i] / maxRms).coerceIn(0f, 1f) }
        } else envelope
    }
}

package com.kokoro.reader

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Applies DSP effects directly to PCM FloatArray from SherpaEngine.
 *
 * When PhonicLandmarks are available, effects become context-aware:
 *   - Vocal fry targets actual phrase endings, not a fixed buffer tail
 *   - Jitter applies only to voiced regions, skipping silence
 *   - Trailing off fades from actual speech end
 *   - Effect intensities adapt to the signal's natural dynamic range
 *
 * Effects applied in order:
 *   1. Soft saturation — smooths harsh transitions and cross-language artifacts
 *   2. Formant smoothing — reduces abrupt frequency jumps between syllables
 *   3. Breathiness — mix a filtered breath-noise layer into the speech signal
 *   4. Spectral tilt — low-pass proportional to breathiness
 *   5. Jitter — micro-amplitude variation for humanizing
 *   6. Vocal fry — phrase-end amplitude modulation
 *   7. Trailing off — fade-out on fatigued/collapsed states
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
     * @param landmarks    Optional phonic landmarks for context-aware DSP
     */
    fun apply(
        samples: FloatArray,
        sampleRate: Int,
        modulated: ModulatedVoice,
        landmarks: PhonicLandmarks? = null
    ): FloatArray {
        if (samples.isEmpty()) return samples
        if (sampleRate <= 0) return samples
        return try {
            applyInternal(samples, sampleRate, modulated, landmarks)
        } catch (e: Exception) {
            android.util.Log.e("AudioDsp", "Error in DSP pipeline", e)
            samples.copyOf()  // Return unmodified copy on error
        }
    }

    private fun applyInternal(
        samples: FloatArray,
        sampleRate: Int,
        modulated: ModulatedVoice,
        landmarks: PhonicLandmarks?
    ): FloatArray {
        // Single allocation — all effects modify this array in-place
        val pcm = samples.copyOf()

        // Adaptive intensity: scale effects based on signal's natural expressiveness.
        val intensityScale = if (landmarks != null) {
            lerp(0.6f, 1.2f, (landmarks.dynamicRange - 1f) / 5f)
        } else 1.0f

        // 0. Volume/gain — applied first so all subsequent effects
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
            applyBreathiness(pcm, sampleRate, modulated.breathIntensity, landmarks, intensityScale)
        }

        // 4. Spectral tilt — proportional to breathiness
        if (modulated.breathIntensity >= 5) {
            applySpectralTilt(pcm, sampleRate, modulated.breathIntensity / 100f)
        }

        // 5. Jitter simulation — landmark-aware: voiced regions only
        if (modulated.jitterAmount > 0.01f) {
            applyJitter(pcm, sampleRate, modulated.jitterAmount * intensityScale, landmarks)
        }

        // 6. Vocal fry at phrase endings — landmark-aware: actual phrase endings
        if (modulated.shouldTrailOff) {
            applyVocalFry(pcm, sampleRate, landmarks)
        }

        // 7. Trailing off — PCM fade — landmark-aware: from speech end
        if (modulated.shouldTrailOff) {
            applyTrailingOff(pcm, landmarks)
        }

        return pcm
    }

    // ── Volume/gain ────────────────────────────────────────────────────────────
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
        intensity: Int,
        landmarks: PhonicLandmarks?,
        intensityScale: Float
    ) {
        val curve = (intensity / 100f) * (intensity / 100f)
        val noiseLevel = curve * 0.18f * intensityScale
        val alpha = 0.55f
        var prevNoise = 0f

        val envWindow = (sampleRate * 0.02).toInt().coerceAtLeast(1)
        val envelope = computeEnvelope(pcm, envWindow)

        val energyContour = landmarks?.energyContour

        for (i in pcm.indices) {
            val rawNoise = Random.nextFloat() * 2f - 1f
            prevNoise = prevNoise * (1f - alpha) + rawNoise * alpha
            val breathNoise = prevNoise * noiseLevel

            val envValue = if (landmarks != null && energyContour != null && i < energyContour.size) {
                energyContour[i] / landmarks.dynamicRange.coerceAtLeast(0.01f)
            } else {
                envelope[i]
            }
            val envShaped = breathNoise * (0.3f + envValue.coerceIn(0f, 1f) * 0.7f)
            pcm[i] = (pcm[i] + envShaped).coerceIn(-1f, 1f)
        }
    }

    // ── Spectral tilt filter ──────────────────────────────────────────────────
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

    // ── Jitter simulation ─────────────────────────────────────────────────────
    private fun applyJitter(
        pcm: FloatArray,
        sampleRate: Int,
        jitterAmount: Float,
        landmarks: PhonicLandmarks?
    ) {
        val windowMs = 8
        val windowSamples = (sampleRate * windowMs / 1000).coerceAtLeast(1)
        val rng = Random

        if (landmarks != null && landmarks.voicedRegions.isNotEmpty()) {
            for (region in landmarks.voicedRegions) {
                var i = region.first.coerceAtLeast(0)
                val regionEnd = region.last.coerceAtMost(pcm.size)
                while (i < regionEnd) {
                    val gain = 1f + (rng.nextFloat() - 0.5f) * 2f * jitterAmount
                    val end = minOf(i + windowSamples, regionEnd)
                    for (j in i until end) pcm[j] = (pcm[j] * gain).coerceIn(-1f, 1f)
                    i = end
                }
            }
        } else {
            var i = 0
            while (i < pcm.size) {
                val gain = 1f + (rng.nextFloat() - 0.5f) * 2f * jitterAmount
                val end = minOf(i + windowSamples, pcm.size)
                for (j in i until end) pcm[j] = (pcm[j] * gain).coerceIn(-1f, 1f)
                i = end
            }
        }
    }

    // ── Vocal fry at phrase endings ───────────────────────────────────────────
    private fun applyVocalFry(pcm: FloatArray, sampleRate: Int, landmarks: PhonicLandmarks?) {
        if (pcm.size < sampleRate / 5) return
        val fryDurationSamples = (sampleRate * 0.2f).toInt()
        val basePeriodSamples = sampleRate / 40
        val dutyCycle = 0.30f

        if (landmarks != null && landmarks.phraseEndings.isNotEmpty()) {
            for (ending in landmarks.phraseEndings) {
                val fryStart = (ending - fryDurationSamples).coerceAtLeast(0)
                val fryEnd = ending.coerceAtMost(pcm.size)
                if (fryEnd - fryStart < sampleRate / 10) continue
                applyFryRegion(pcm, fryStart, fryEnd, basePeriodSamples, dutyCycle)
            }
        } else {
            val fryStart = (pcm.size - fryDurationSamples).coerceAtLeast(0)
            applyFryRegion(pcm, fryStart, pcm.size, basePeriodSamples, dutyCycle)
        }
    }

    private fun applyFryRegion(
        pcm: FloatArray, fryStart: Int, fryEnd: Int,
        basePeriodSamples: Int, dutyCycle: Float
    ) {
        val rng = Random
        var cyclePos = 0
        var currentPeriod = basePeriodSamples
        val openSamples = { (currentPeriod * dutyCycle).toInt().coerceAtLeast(1) }
        val regionLen = (fryEnd - fryStart).coerceAtLeast(1)

        for (i in fryStart until fryEnd) {
            val progress = (i - fryStart).toFloat() / regionLen
            val fadeFactor = 1f - progress * 0.3f

            val gain = if (cyclePos < openSamples()) {
                0.7f + 0.3f * (1f - cyclePos.toFloat() / openSamples())
            } else {
                0.05f
            }

            pcm[i] = (pcm[i] * gain * fadeFactor).coerceIn(-1f, 1f)
            cyclePos++

            if (cyclePos >= currentPeriod) {
                cyclePos = 0
                val jitter = 1f + (rng.nextFloat() - 0.5f) * 0.30f
                currentPeriod = (basePeriodSamples * jitter).toInt().coerceAtLeast(1)
            }
        }
    }

    // ── Trailing off ──────────────────────────────────────────────────────────
    private fun applyTrailingOff(pcm: FloatArray, landmarks: PhonicLandmarks?) {
        val speechEnd = landmarks?.speechEnd ?: pcm.size
        val speechStart = landmarks?.speechStart ?: 0
        val speechLen = (speechEnd - speechStart).coerceAtLeast(1)

        val fadeStart = speechStart + (speechLen * 0.85f).toInt()
        if (fadeStart >= pcm.size) return

        val fadeEnd = speechEnd.coerceAtMost(pcm.size)
        val fadeLen = (fadeEnd - fadeStart).coerceAtLeast(1)

        for (i in fadeStart until fadeEnd) {
            val factor = 1f - ((i - fadeStart).toFloat() / fadeLen)
            pcm[i] *= factor.coerceAtLeast(0f)
        }
        for (i in fadeEnd until pcm.size) {
            pcm[i] *= 0.05f
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

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

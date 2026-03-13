package com.kokoro.reader

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
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
 *   0. Pitch shift — resample-based, preserves duration
 *   1. Volume/gain
 *   2. Soft saturation — very gentle, smooths harsh transitions
 *   3. Formant smoothing — reduces abrupt frequency jumps
 *   4. Breathiness — filtered breath-noise layer
 *   5. Spectral tilt — low-pass proportional to breathiness
 *   6. Jitter — micro-amplitude variation with crossfaded windows
 *   7. Vocal fry — phrase-end amplitude modulation
 *   8. Trailing off — fade-out on fatigued/collapsed states
 *   9. Soft limiter — prevents hard clipping artifacts
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
            samples.copyOf()
        }
    }

    /**
     * Pitch-shift audio via high-quality resampling. Preserves duration
     * by resampling to change pitch, then time-stretching back via
     * overlap-add. This replaces the old playbackRate approach which
     * shifted pitch AND speed together.
     *
     * @param samples   Input PCM
     * @param pitch     Pitch multiplier (1.0 = no change, 1.5 = up 50%, 0.75 = down 25%)
     * @return Pitch-shifted PCM at the same duration and sample rate
     */
    fun pitchShift(samples: FloatArray, pitch: Float): FloatArray {
        if (samples.isEmpty()) return samples
        if (pitch in 0.99f..1.01f) return samples  // no-op for near-unity

        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)

        // Step 1: Resample — changes both pitch and speed
        val resampledLen = (samples.size / clampedPitch).toInt().coerceAtLeast(1)
        val resampled = FloatArray(resampledLen) { i ->
            val srcPos = i * clampedPitch
            val srcIdx = srcPos.toInt()
            val frac = srcPos - srcIdx
            when {
                srcIdx + 1 < samples.size -> samples[srcIdx] * (1f - frac) + samples[srcIdx + 1] * frac
                srcIdx < samples.size -> samples[srcIdx]
                else -> 0f
            }
        }

        // Step 2: Time-stretch back to original duration via overlap-add (OLA)
        // This restores the original tempo while keeping the pitch shift
        return olaTimeStretch(resampled, samples.size, 22050)
    }

    /**
     * Simple overlap-add time stretching.
     * Stretches/compresses `input` to `targetLen` samples.
     */
    private fun olaTimeStretch(input: FloatArray, targetLen: Int, sampleRate: Int): FloatArray {
        if (input.isEmpty() || targetLen <= 0) return FloatArray(targetLen)

        val windowMs = 25  // 25ms windows — good for speech
        val windowSamples = (sampleRate * windowMs / 1000).coerceAtLeast(64)
        val hopOut = windowSamples / 2  // 50% overlap in output
        val stretchRatio = targetLen.toFloat() / input.size
        val hopIn = (hopOut / stretchRatio).toInt().coerceAtLeast(1)

        val output = FloatArray(targetLen)
        val window = FloatArray(windowSamples) { i ->
            // Hann window for smooth crossfade
            (0.5f * (1f - kotlin.math.cos(2.0 * PI * i / windowSamples))).toFloat()
        }

        var inPos = 0
        var outPos = 0

        while (outPos < targetLen) {
            val chunkLen = min(windowSamples, targetLen - outPos)
            for (j in 0 until chunkLen) {
                val srcIdx = inPos + j
                val sample = if (srcIdx < input.size) input[srcIdx] else 0f
                val w = if (j < window.size) window[j] else 0f
                output[outPos + j] += sample * w
            }
            outPos += hopOut
            inPos += hopIn
            // Wrap around for very short inputs
            if (inPos >= input.size) inPos = input.size - windowSamples.coerceAtMost(input.size)
            if (inPos < 0) inPos = 0
        }

        // Normalize to prevent amplitude buildup from overlap
        val maxAmp = output.maxOfOrNull { abs(it) } ?: 1f
        if (maxAmp > 1f) {
            val scale = 1f / maxAmp
            for (i in output.indices) output[i] *= scale
        }

        return output
    }

    private fun applyInternal(
        samples: FloatArray,
        sampleRate: Int,
        modulated: ModulatedVoice,
        landmarks: PhonicLandmarks?
    ): FloatArray {
        val pcm = samples.copyOf()
        // Keep dry copy for ramp-in blending
        val dry = samples.copyOf()

        val intensityScale = if (landmarks != null) {
            lerp(0.6f, 1.2f, (landmarks.dynamicRange - 1f) / 5f)
        } else 1.0f

        // 1. Volume/gain
        if (modulated.volume != 1.0f) {
            applyVolume(pcm, modulated.volume)
        }

        // 2. Soft saturation — very gentle to avoid distortion
        applySoftSaturation(pcm)

        // 3. Formant smoothing
        applyFormantSmoothing(pcm, sampleRate)

        // 4. Breathiness noise mixing — use squared curve for smoother intensity
        if (modulated.breathIntensity >= 5) {
            applyBreathiness(pcm, sampleRate, modulated.breathIntensity, landmarks, intensityScale)
        }

        // 5. Spectral tilt — proportional to breathiness
        if (modulated.breathIntensity >= 10) {
            applySpectralTilt(pcm, sampleRate, modulated.breathIntensity / 100f)
        }

        // 6. Jitter simulation — with crossfaded windows to prevent clicks
        if (modulated.jitterAmount > 0.01f) {
            applyJitter(pcm, sampleRate, modulated.jitterAmount * intensityScale, landmarks)
        }

        // 7. Vocal fry at phrase endings
        if (modulated.shouldTrailOff) {
            applyVocalFry(pcm, sampleRate, landmarks)
        }

        // 8. Trailing off
        if (modulated.shouldTrailOff) {
            applyTrailingOff(pcm, landmarks)
        }

        // 9. Effect ramp-in — crossfade from dry to wet over first 30ms
        // Prevents abrupt effect onset when parameters change between utterances
        val rampSamples = (sampleRate * 0.030f).toInt().coerceAtMost(pcm.size)
        if (rampSamples > 0) {
            for (i in 0 until rampSamples) {
                val wet = i.toFloat() / rampSamples  // 0→1 over 30ms
                pcm[i] = dry[i] * (1f - wet) + pcm[i] * wet
            }
        }

        // 10. Final soft limiter — prevents hard clipping from compound effects
        applySoftLimiter(pcm)

        return pcm
    }

    // ── Volume/gain ────────────────────────────────────────────────────────────
    private fun applyVolume(pcm: FloatArray, volume: Float) {
        for (i in pcm.indices) {
            pcm[i] *= volume
        }
    }

    // ── Soft saturation ───────────────────────────────────────────────────────
    // Very gentle drive (1.05) — just smooths peaks, doesn't color the sound
    private fun applySoftSaturation(pcm: FloatArray) {
        val drive = 1.05f
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
            envelope[i] = envelope[i - 1] + alpha * (abs(pcm[i]) - envelope[i - 1])
        }

        // Gentler blending ratio (0.85 + 0.15*ratio instead of 0.7 + 0.3*ratio)
        for (i in pcm.indices) {
            val originalAmp = abs(pcm[i])
            if (originalAmp > 0.001f) {
                val ratio = envelope[i] / originalAmp
                val blended = 0.85f + 0.15f * ratio.coerceIn(0.5f, 2.0f)
                pcm[i] *= blended
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
        val curve = smoothCurve(intensity)
        // Reduced from 0.18 to 0.12 — less noise, cleaner output
        val noiseLevel = curve * 0.12f * intensityScale
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
            pcm[i] += breathNoise * (0.3f + envValue.coerceIn(0f, 1f) * 0.7f)
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
        val cutoffHz = lerp(8000f, 3000f, normalizedBreath)  // Higher floor (3000 vs 2500)
        val alpha = exp(-2.0 * PI * cutoffHz / sampleRate).toFloat()
        var prev = 0f
        for (i in pcm.indices) {
            pcm[i] = (1f - alpha) * pcm[i] + alpha * prev
            prev = pcm[i]
        }
    }

    // ── Jitter simulation ─────────────────────────────────────────────────────
    // Uses crossfaded windows to prevent clicking at boundaries
    private fun applyJitter(
        pcm: FloatArray,
        sampleRate: Int,
        jitterAmount: Float,
        landmarks: PhonicLandmarks?
    ) {
        val windowMs = 12  // Larger windows = smoother transitions
        val windowSamples = (sampleRate * windowMs / 1000).coerceAtLeast(1)
        val fadeLen = windowSamples / 4  // 25% crossfade at boundaries
        val rng = Random

        // Apply jitter to a region with crossfaded window boundaries
        fun applyRegionJitter(start: Int, end: Int) {
            var i = start
            while (i < end) {
                val gain = 1f + (rng.nextFloat() - 0.5f) * 2f * jitterAmount
                val windowEnd = min(i + windowSamples, end)
                for (j in i until windowEnd) {
                    // Crossfade at window edges to prevent clicks
                    val distFromEdge = min(j - i, windowEnd - 1 - j).coerceAtLeast(0)
                    val fadeFactor = if (distFromEdge < fadeLen) {
                        distFromEdge.toFloat() / fadeLen
                    } else 1f
                    val effectiveGain = 1f + (gain - 1f) * fadeFactor
                    pcm[j] *= effectiveGain
                }
                i = windowEnd
            }
        }

        if (landmarks != null && landmarks.voicedRegions.isNotEmpty()) {
            for (region in landmarks.voicedRegions) {
                applyRegionJitter(region.first.coerceAtLeast(0), region.last.coerceAtMost(pcm.size))
            }
        } else {
            applyRegionJitter(0, pcm.size)
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

            pcm[i] *= gain * fadeFactor
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

    // ── Soft limiter ──────────────────────────────────────────────────────────
    // Prevents hard clipping from compound effects. Uses tanh for smooth limiting.
    private fun applySoftLimiter(pcm: FloatArray) {
        for (i in pcm.indices) {
            val v = pcm[i]
            pcm[i] = if (abs(v) > 0.9f) {
                // Soft-clip anything above 0.9 using tanh curve
                val sign = if (v > 0f) 1f else -1f
                sign * (0.9f + 0.1f * tanh(((abs(v) - 0.9f) * 10f).toDouble()).toFloat())
            } else v
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
            val count = min(i + 1, windowSize)
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

    /** Quadratic smoothing curve: maps 0-100 int to 0.0-1.0 with gentle low end */
    private fun smoothCurve(v: Int): Float {
        val n = (v / 100f).coerceIn(0f, 1f)
        return n * n
    }
}

package com.echolibrium.kyokan

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * Minimal DSP: pitch shift only.
 */
object AudioDsp {

    /**
     * Pitch-shift audio via high-quality resampling. Preserves duration
     * by resampling to change pitch, then time-stretching back via
     * overlap-add.
     *
     * @param samples    Input PCM
     * @param pitch      Pitch multiplier (1.0 = no change, 1.5 = up 50%, 0.75 = down 25%)
     * @param sampleRate Sample rate in Hz for correct OLA window sizing
     * @return Pitch-shifted PCM at the same duration and sample rate
     */
    fun pitchShift(samples: FloatArray, pitch: Float, sampleRate: Int = 24000): FloatArray {
        if (samples.isEmpty()) return samples
        if (pitch in 0.99f..1.01f) return samples

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
        return olaTimeStretch(resampled, samples.size, sampleRate)
    }

    /**
     * Simple overlap-add time stretching.
     * Stretches/compresses `input` to `targetLen` samples.
     */
    private fun olaTimeStretch(input: FloatArray, targetLen: Int, sampleRate: Int): FloatArray {
        if (input.isEmpty() || targetLen <= 0) return FloatArray(targetLen)

        val windowMs = 25
        val windowSamples = (sampleRate * windowMs / 1000).coerceAtLeast(64)
        val hopOut = windowSamples / 2
        val stretchRatio = targetLen.toFloat() / input.size
        val hopIn = (hopOut / stretchRatio).toInt().coerceAtLeast(1)

        val output = FloatArray(targetLen)
        val window = FloatArray(windowSamples) { i ->
            (0.5f * (1f - cos(2.0 * PI * i / windowSamples))).toFloat()
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
            if (inPos >= input.size) inPos = input.size - windowSamples.coerceAtMost(input.size)
            if (inPos < 0) inPos = 0
        }

        val maxAmp = output.maxOfOrNull { abs(it) } ?: 1f
        if (maxAmp > 1f) {
            val scale = 1f / maxAmp
            for (i in output.indices) output[i] *= scale
        }

        return output
    }
}

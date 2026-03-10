package com.kokoro.reader

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Phonic Landmark Detector — single-pass PCM analysis that extracts
 * structural landmarks from synthesized speech BEFORE DSP effects are applied.
 *
 * This makes AudioDsp context-aware instead of blind:
 *   - Vocal fry targets actual phrase endings, not a fixed % of the buffer
 *   - Jitter is applied only to voiced regions, not silence
 *   - Trailing off fades from the last real speech, not buffer end
 *   - DSP intensity adapts to the signal's natural dynamic range
 *
 * All analysis is O(n) single-pass on the PCM buffer. No FFT, no allocations
 * beyond the result struct and a small per-window working buffer.
 */
object PhonicAnalyzer {

    // ── Analysis window ─────────────────────────────────────────────────────
    private const val WINDOW_MS = 5           // 5ms analysis frames
    private const val SILENCE_THRESHOLD = 0.02f
    private const val VOICED_THRESHOLD = 0.04f
    private const val ONSET_RISE_FACTOR = 3.0f // energy must rise 3x to mark onset
    private const val MIN_PHRASE_GAP_MS = 80   // silence > 80ms = phrase boundary

    /**
     * Analyze raw PCM and extract phonic landmarks.
     * Called once per notification, between SherpaEngine and AudioDsp.
     */
    fun analyze(samples: FloatArray, sampleRate: Int): PhonicLandmarks {
        if (samples.isEmpty() || sampleRate <= 0) return PhonicLandmarks.EMPTY

        val windowSamples = (sampleRate * WINDOW_MS / 1000).coerceAtLeast(1)
        val numWindows = samples.size / windowSamples
        if (numWindows == 0) return PhonicLandmarks.EMPTY

        // Per-window metrics
        val rmsEnergy = FloatArray(numWindows)
        val zcr = FloatArray(numWindows)  // zero-crossing rate

        // Single pass: compute RMS and ZCR per window
        for (w in 0 until numWindows) {
            val start = w * windowSamples
            val end = min(start + windowSamples, samples.size)
            var sumSq = 0f
            var crossings = 0
            var prev = samples[start]

            for (i in start until end) {
                sumSq += samples[i] * samples[i]
                if (i > start && samples[i] * prev < 0f) crossings++
                prev = samples[i]
            }

            val count = end - start
            rmsEnergy[w] = sqrt(sumSq / count)
            zcr[w] = crossings.toFloat() / count
        }

        // Find global energy stats
        var maxEnergy = 0f
        var totalEnergy = 0f
        for (e in rmsEnergy) {
            if (e > maxEnergy) maxEnergy = e
            totalEnergy += e
        }
        val meanEnergy = if (numWindows > 0) totalEnergy / numWindows else 0f

        // ── Classify windows ────────────────────────────────────────────────
        val voiced = BooleanArray(numWindows)
        val silent = BooleanArray(numWindows)
        for (w in 0 until numWindows) {
            silent[w] = rmsEnergy[w] < SILENCE_THRESHOLD
            // Voiced: above threshold AND low ZCR (periodic signal)
            voiced[w] = rmsEnergy[w] >= VOICED_THRESHOLD && zcr[w] < 0.3f
        }

        // ── Detect onsets (energy rising edges) ─────────────────────────────
        val onsets = mutableListOf<Int>()  // sample indices
        for (w in 1 until numWindows) {
            if (rmsEnergy[w] >= VOICED_THRESHOLD &&
                rmsEnergy[w - 1] < SILENCE_THRESHOLD &&
                (w < 2 || rmsEnergy[w] > rmsEnergy[w - 1] * ONSET_RISE_FACTOR)) {
                onsets.add(w * windowSamples)
            }
        }

        // ── Detect phrase endings (voiced→silence transitions > MIN_PHRASE_GAP_MS) ──
        val phraseEndings = mutableListOf<Int>()  // sample indices
        val minGapWindows = MIN_PHRASE_GAP_MS / WINDOW_MS
        var silenceRunStart = -1

        for (w in 0 until numWindows) {
            if (silent[w]) {
                if (silenceRunStart < 0) silenceRunStart = w
            } else {
                if (silenceRunStart >= 0) {
                    val gapLen = w - silenceRunStart
                    if (gapLen >= minGapWindows && silenceRunStart > 0) {
                        // The phrase ended at silenceRunStart
                        phraseEndings.add(silenceRunStart * windowSamples)
                    }
                }
                silenceRunStart = -1
            }
        }
        // Buffer end counts as a phrase ending if last voiced region precedes it
        val lastVoicedWindow = voiced.indexOfLast { it }
        if (lastVoicedWindow >= 0) {
            val endSample = (lastVoicedWindow + 1) * windowSamples
            if (phraseEndings.isEmpty() || phraseEndings.last() != endSample) {
                phraseEndings.add(endSample.coerceAtMost(samples.size))
            }
        }

        // ── Detect stress points (local energy peaks) ───────────────────────
        val stressPoints = mutableListOf<Int>()
        for (w in 1 until numWindows - 1) {
            if (voiced[w] &&
                rmsEnergy[w] > rmsEnergy[w - 1] &&
                rmsEnergy[w] > rmsEnergy[w + 1] &&
                rmsEnergy[w] > meanEnergy * 1.3f) {
                stressPoints.add(w * windowSamples)
            }
        }

        // ── Build voiced regions (contiguous voiced windows) ────────────────
        val voicedRegions = mutableListOf<IntRange>()
        var regionStart = -1
        for (w in 0 until numWindows) {
            if (voiced[w] && regionStart < 0) {
                regionStart = w * windowSamples
            } else if (!voiced[w] && regionStart >= 0) {
                voicedRegions.add(regionStart until (w * windowSamples))
                regionStart = -1
            }
        }
        if (regionStart >= 0) {
            voicedRegions.add(regionStart until samples.size)
        }

        // ── Build silence regions ───────────────────────────────────────────
        val silenceRegions = mutableListOf<IntRange>()
        var silStart = -1
        for (w in 0 until numWindows) {
            if (silent[w] && silStart < 0) {
                silStart = w * windowSamples
            } else if (!silent[w] && silStart >= 0) {
                silenceRegions.add(silStart until (w * windowSamples))
                silStart = -1
            }
        }
        if (silStart >= 0) {
            silenceRegions.add(silStart until samples.size)
        }

        // ── Dynamic range ───────────────────────────────────────────────────
        // Ratio of peak to mean energy — high = expressive, low = monotone
        val dynamicRange = if (meanEnergy > 0.001f) (maxEnergy / meanEnergy).coerceIn(1f, 10f) else 1f

        // ── Per-sample energy contour (smoothed) ────────────────────────────
        // Upsampled from per-window RMS to per-sample with linear interpolation
        val energyContour = FloatArray(samples.size)
        for (w in 0 until numWindows) {
            val start = w * windowSamples
            val end = min(start + windowSamples, samples.size)
            val nextE = if (w + 1 < numWindows) rmsEnergy[w + 1] else rmsEnergy[w]
            for (i in start until end) {
                val t = (i - start).toFloat() / windowSamples
                energyContour[i] = rmsEnergy[w] + (nextE - rmsEnergy[w]) * t
            }
        }

        // ── YIN pitch estimation ────────────────────────────────────────────
        val pitchContour = estimatePitchContour(samples, sampleRate, windowSamples, numWindows)

        // ── Speech boundaries ───────────────────────────────────────────────
        val firstVoiced = voicedRegions.firstOrNull()?.first ?: 0
        val lastVoiced = voicedRegions.lastOrNull()?.last ?: samples.size

        return PhonicLandmarks(
            voicedRegions = voicedRegions,
            silenceRegions = silenceRegions,
            onsets = onsets,
            stressPoints = stressPoints,
            phraseEndings = phraseEndings,
            energyContour = energyContour,
            pitchContour = pitchContour,
            dynamicRange = dynamicRange,
            speechStart = firstVoiced,
            speechEnd = lastVoiced,
            sampleRate = sampleRate,
            totalSamples = samples.size
        )
    }

    // ── YIN pitch estimator ─────────────────────────────────────────────────
    // Simplified YIN: estimates F0 per analysis window using the cumulative
    // mean normalized difference function. Returns 0 for unvoiced windows.
    // Reference: de Cheveigné & Kawahara (2002)

    private fun estimatePitchContour(
        samples: FloatArray,
        sampleRate: Int,
        windowSamples: Int,
        numWindows: Int
    ): FloatArray {
        val pitchPerWindow = FloatArray(numWindows)
        // YIN works best with a larger window — use 2x analysis window
        val yinWindow = windowSamples * 2
        val maxLag = (sampleRate / 60).coerceAtMost(yinWindow / 2)  // 60Hz min
        val minLag = sampleRate / 500  // 500Hz max
        if (maxLag <= minLag || yinWindow > samples.size) return pitchPerWindow

        val diff = FloatArray(maxLag)
        val cmndf = FloatArray(maxLag)  // cumulative mean normalized difference

        for (w in 0 until numWindows) {
            val start = w * windowSamples
            if (start + yinWindow > samples.size) break

            // Step 1: Difference function
            for (tau in 1 until maxLag) {
                var sum = 0f
                for (j in 0 until yinWindow - maxLag) {
                    val d = samples[start + j] - samples[start + j + tau]
                    sum += d * d
                }
                diff[tau] = sum
            }

            // Step 2: Cumulative mean normalized difference
            cmndf[0] = 1f
            var runningSum = 0f
            for (tau in 1 until maxLag) {
                runningSum += diff[tau]
                cmndf[tau] = if (runningSum > 0f) diff[tau] * tau / runningSum else 1f
            }

            // Step 3: Absolute threshold — find first dip below 0.15
            val threshold = 0.15f
            var bestTau = -1
            for (tau in minLag until maxLag - 1) {
                if (cmndf[tau] < threshold) {
                    // Parabolic interpolation for sub-sample accuracy
                    if (tau > 0 && tau < maxLag - 1) {
                        val a = cmndf[tau - 1]
                        val b = cmndf[tau]
                        val c = cmndf[tau + 1]
                        val shift = (a - c) / (2f * (a - 2f * b + c))
                        if (!shift.isNaN() && !shift.isInfinite() && abs(shift) < 1f) {
                            bestTau = tau  // use integer tau, parabolic just for validation
                        } else {
                            bestTau = tau
                        }
                    } else {
                        bestTau = tau
                    }
                    break
                }
            }

            pitchPerWindow[w] = if (bestTau > 0) sampleRate.toFloat() / bestTau else 0f
        }

        // Upsample to per-sample (same as energy contour)
        val pitchContour = FloatArray(samples.size)
        for (w in 0 until numWindows) {
            val start = w * windowSamples
            val end = min(start + windowSamples, samples.size)
            val nextP = if (w + 1 < numWindows) pitchPerWindow[w + 1] else pitchPerWindow[w]
            for (i in start until end) {
                val t = (i - start).toFloat() / windowSamples
                pitchContour[i] = pitchPerWindow[w] + (nextP - pitchPerWindow[w]) * t
            }
        }
        return pitchContour
    }
}

/**
 * Structural landmarks extracted from synthesized PCM.
 * Immutable — computed once, read by AudioDsp for surgical effect placement.
 */
data class PhonicLandmarks(
    /** Contiguous sample ranges where speech is voiced (periodic) */
    val voicedRegions: List<IntRange>,
    /** Contiguous sample ranges of silence */
    val silenceRegions: List<IntRange>,
    /** Sample indices where speech onsets occur (silence→voiced transitions) */
    val onsets: List<Int>,
    /** Sample indices of stressed syllables (local energy peaks) */
    val stressPoints: List<Int>,
    /** Sample indices where phrases end (before significant silence gaps) */
    val phraseEndings: List<Int>,
    /** Per-sample RMS energy (smoothed, interpolated from 5ms windows) */
    val energyContour: FloatArray,
    /** Per-sample pitch estimate in Hz (0 = unvoiced). YIN-based. */
    val pitchContour: FloatArray,
    /** Peak-to-mean energy ratio. High = expressive, low = monotone */
    val dynamicRange: Float,
    /** First voiced sample index */
    val speechStart: Int,
    /** Last voiced sample index */
    val speechEnd: Int,
    /** Sample rate used during analysis */
    val sampleRate: Int,
    /** Total sample count */
    val totalSamples: Int
) {
    /** True if sample index i falls within a voiced region */
    fun isVoiced(i: Int): Boolean = voicedRegions.any { i in it }

    /** True if sample index i falls within a silence region */
    fun isSilent(i: Int): Boolean = silenceRegions.any { i in it }

    /** Nearest phrase ending at or after sample index i, or -1 */
    fun nextPhraseEnding(fromSample: Int): Int =
        phraseEndings.firstOrNull { it >= fromSample } ?: -1

    /** Duration of actual speech content in seconds */
    fun speechDurationSec(): Float =
        if (sampleRate > 0) (speechEnd - speechStart).toFloat() / sampleRate else 0f

    companion object {
        val EMPTY = PhonicLandmarks(
            voicedRegions = emptyList(),
            silenceRegions = emptyList(),
            onsets = emptyList(),
            stressPoints = emptyList(),
            phraseEndings = emptyList(),
            energyContour = FloatArray(0),
            pitchContour = FloatArray(0),
            dynamicRange = 1f,
            speechStart = 0,
            speechEnd = 0,
            sampleRate = 0,
            totalSamples = 0
        )
    }
}

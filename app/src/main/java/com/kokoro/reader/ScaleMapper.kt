package com.kokoro.reader

/**
 * ScaleMapper — Maps VoiceModulator output to Piper's generative parameters.
 *
 * Piper (VITS) exposes three scale inputs that control voice character at synthesis time:
 *   - noise_scale:  Controls decoder noise → breathiness/expressiveness (default 0.667)
 *   - length_scale: Controls phoneme duration → speaking speed (default 1.0)
 *   - noise_w:      Controls stochastic duration predictor → rhythm variation (default 0.8)
 *
 * These are NOT post-processing. They feed directly into the generative model.
 *
 * Adapted to use the actual ModulatedVoice fields:
 *   - breathIntensity (Int, 0-100) instead of breath (Float)
 *   - intonationIntensity (Int, 0-100) + intonationVariation (Float, 0-1)
 *   - jitterAmount (Float, 0-1) instead of jitter (Float, 0-100)
 *   - EmotionBlend.NONE instead of null
 */
object ScaleMapper {

    private const val DEFAULT_NOISE_SCALE = 0.667f
    private const val DEFAULT_LENGTH_SCALE = 1.0f
    private const val DEFAULT_NOISE_W = 0.8f

    // Safety bounds — going outside these produces garbage audio
    private const val MIN_NOISE_SCALE = 0.1f
    private const val MAX_NOISE_SCALE = 1.2f
    private const val MIN_LENGTH_SCALE = 0.5f
    private const val MAX_LENGTH_SCALE = 2.0f
    private const val MIN_NOISE_W = 0.1f
    private const val MAX_NOISE_W = 1.5f

    /**
     * Map ModulatedVoice parameters to Piper's three scale values.
     *
     * @return FloatArray of [noise_scale, length_scale, noise_w]
     */
    fun mapScales(modulated: ModulatedVoice, signal: SignalMap): FloatArray {
        val noiseScale = mapNoiseScale(modulated, signal)
        val lengthScale = mapLengthScale(modulated)
        val noiseW = mapNoiseW(modulated, signal)

        return floatArrayOf(
            noiseScale.guardNaN(DEFAULT_NOISE_SCALE).coerceIn(MIN_NOISE_SCALE, MAX_NOISE_SCALE),
            lengthScale.guardNaN(DEFAULT_LENGTH_SCALE).coerceIn(MIN_LENGTH_SCALE, MAX_LENGTH_SCALE),
            noiseW.guardNaN(DEFAULT_NOISE_W).coerceIn(MIN_NOISE_W, MAX_NOISE_W),
        )
    }

    /**
     * noise_scale → BREATHINESS + EXPRESSIVENESS
     *
     * Maps from:
     *   - modulated.breathIntensity (0-100): primary driver
     *   - emotionBlend: secondary influence
     *   - floodTier: more overwhelmed = slightly more breath
     */
    private fun mapNoiseScale(modulated: ModulatedVoice, signal: SignalMap): Float {
        var scale = DEFAULT_NOISE_SCALE

        // breathIntensity: 0-100 → noise_scale shift of ±0.3
        val breathNorm = modulated.breathIntensity / 100f
        val breathShift = smoothCurve(breathNorm) * 0.4f - 0.1f
        scale += breathShift

        // Emotion blend influence
        when (signal.emotionBlend) {
            EmotionBlend.NERVOUS_EXCITEMENT -> scale += 0.05f
            EmotionBlend.SUPPRESSED_TENSION -> scale -= 0.08f
            EmotionBlend.NOSTALGIC_WARMTH -> scale += 0.08f
            EmotionBlend.RESIGNED_ACCEPTANCE -> scale -= 0.05f
            EmotionBlend.WORRIED_AFFECTION -> scale += 0.03f
            EmotionBlend.NONE -> {}
        }

        // Flood tier: overwhelmed → breathier
        when (signal.floodTier) {
            FloodTier.FLOODED -> scale += 0.04f
            FloodTier.OVERWHELMED -> scale += 0.08f
            else -> {}
        }

        return scale
    }

    /**
     * length_scale → SPEAKING SPEED
     *
     * length_scale is inverted from ModulatedVoice.speed:
     *   speed=1.2 means "speak faster" → length_scale < 1.0
     */
    private fun mapLengthScale(modulated: ModulatedVoice): Float {
        return if (modulated.speed > 0.01f) {
            1f / modulated.speed
        } else {
            DEFAULT_LENGTH_SCALE
        }
    }

    /**
     * noise_w → RHYTHM VARIATION / NATURALNESS
     *
     * Maps from:
     *   - modulated.intonationIntensity (0-100) + intonationVariation (0-1): primary
     *   - modulated.jitterAmount (0-1): secondary
     *   - emotionBlend: tertiary
     */
    private fun mapNoiseW(modulated: ModulatedVoice, signal: SignalMap): Float {
        var noiseW = DEFAULT_NOISE_W

        // Intonation: intensity controls how much effect, variation controls direction
        // High intensity + high variation (>0.5) = wider rhythm
        // High intensity + low variation (<0.5) = flatter rhythm
        val intonationNorm = modulated.intonationIntensity / 100f
        val intonationShift = intonationNorm * (modulated.intonationVariation - 0.5f) * 0.6f
        noiseW += intonationShift

        // Jitter adds rhythm variation (replaces DSP jitter for Piper path)
        // jitterAmount is 0.0-1.0
        noiseW += smoothCurve(modulated.jitterAmount) * 0.2f

        // Emotion overrides
        when (signal.emotionBlend) {
            EmotionBlend.NERVOUS_EXCITEMENT -> noiseW += 0.1f
            EmotionBlend.SUPPRESSED_TENSION -> noiseW -= 0.15f
            EmotionBlend.NOSTALGIC_WARMTH -> noiseW += 0.05f
            EmotionBlend.RESIGNED_ACCEPTANCE -> noiseW -= 0.1f
            EmotionBlend.WORRIED_AFFECTION -> noiseW += 0.05f
            EmotionBlend.NONE -> {}
        }

        // Sarcasm → flatten rhythm
        if (signal.detectedSarcasm) {
            noiseW -= 0.1f
        }

        return noiseW
    }

    private fun smoothCurve(v: Float): Float = v * v

    private fun Float.guardNaN(default: Float): Float =
        if (this.isNaN() || this.isInfinite()) default else this
}

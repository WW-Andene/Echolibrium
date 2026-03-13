package com.echolibrium.kyokan

import kotlin.math.*

/**
 * StyleSculptor — Manipulates Kokoro's 256-dim style embedding BEFORE synthesis.
 *
 * Instead of applying effects to finished audio (DSP), we modify the voice identity
 * vector that the model uses to generate speech. The model then synthesizes audio
 * that IS breathy, IS energetic, IS warm — not audio with effects painted on top.
 *
 * The style vector space is continuous. Interpolating between two voice embeddings
 * produces a valid intermediate voice. This is a property of StyleTTS2-based models.
 *
 * Adapted to use the actual ModulatedVoice, SignalMap, and EmotionBlend types
 * from the Echolibrium codebase.
 */
object StyleSculptor {

    private const val STYLE_DIM = 256

    /**
     * Voice palette — pre-analyzed voice embeddings with known emotional characteristics.
     * Loaded from voices.bin and tagged with perceptual qualities.
     */
    data class VoicePalette(
        val name: String,
        val vectors: FloatArray,  // shape: [numTokenLengths, 256]
        val numTokenLengths: Int,
        // Perceptual tags (0.0 to 1.0)
        val warmth: Float = 0.5f,
        val breathiness: Float = 0.5f,
        val energy: Float = 0.5f,
        val clarity: Float = 0.5f,
    )

    // Cache of loaded voice palettes
    private val palettes = mutableMapOf<String, VoicePalette>()

    // Perceptual profiles for known Kokoro voices (KokoroVoices.ALL)
    // Index matches KokoroVoice.sid: [warmth, breathiness, energy, clarity]
    private val VOICE_PROFILES = mapOf(
        "af_heart"    to floatArrayOf(0.8f, 0.5f, 0.5f, 0.7f),  // warm, moderate
        "af_bella"    to floatArrayOf(0.7f, 0.4f, 0.6f, 0.8f),  // warm, clear, moderate energy
        "af_nicole"   to floatArrayOf(0.6f, 0.5f, 0.5f, 0.7f),  // balanced
        "af_sarah"    to floatArrayOf(0.5f, 0.3f, 0.7f, 0.9f),  // neutral, clear, high energy
        "af_sky"      to floatArrayOf(0.8f, 0.6f, 0.4f, 0.6f),  // warm, breathy, calm
        "am_adam"     to floatArrayOf(0.4f, 0.3f, 0.8f, 0.8f),  // cool, clear, energetic
        "am_michael"  to floatArrayOf(0.6f, 0.2f, 0.7f, 0.9f),  // warm, very clear, energetic
        "bf_emma"     to floatArrayOf(0.6f, 0.4f, 0.5f, 0.8f),  // moderate warmth, clear
        "bf_isabella" to floatArrayOf(0.7f, 0.5f, 0.4f, 0.7f),  // warm, moderate breath
        "bm_george"   to floatArrayOf(0.5f, 0.3f, 0.6f, 0.8f),  // neutral, clear
        "bm_lewis"    to floatArrayOf(0.4f, 0.4f, 0.7f, 0.7f),  // cool, moderate
    )

    /**
     * Load a voice palette from the voices.bin data.
     *
     * @param name Voice name (e.g., "af_bella") — matches KokoroVoice.id
     * @param allVoicesData Raw float array from voices.bin
     * @param voiceIndex KokoroVoice.sid (0-based index in voices.bin)
     * @param numTokenLengths Number of token-length variants (usually 510 or 512)
     */
    fun loadPalette(
        name: String,
        allVoicesData: FloatArray,
        voiceIndex: Int,
        numTokenLengths: Int = 512
    ): VoicePalette {
        val offset = voiceIndex * numTokenLengths * STYLE_DIM
        val end = (offset + numTokenLengths * STYLE_DIM).coerceAtMost(allVoicesData.size)
        val vectors = allVoicesData.copyOfRange(offset, end)

        val profile = VOICE_PROFILES[name] ?: floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val palette = VoicePalette(
            name = name,
            vectors = vectors,
            numTokenLengths = numTokenLengths,
            warmth = profile[0],
            breathiness = profile[1],
            energy = profile[2],
            clarity = profile[3],
        )
        palettes[name] = palette
        return palette
    }

    /**
     * Get the base style vector for a given voice and token count.
     */
    fun getBaseStyle(palette: VoicePalette, numTokens: Int): FloatArray {
        val idx = numTokens.coerceIn(0, palette.numTokenLengths - 1)
        val offset = idx * STYLE_DIM
        val end = (offset + STYLE_DIM).coerceAtMost(palette.vectors.size)
        if (end - offset < STYLE_DIM) return FloatArray(STYLE_DIM)
        return palette.vectors.copyOfRange(offset, end)
    }

    /**
     * SCULPT — The main entry point.
     *
     * Takes a base style vector and the full emotional context from VoiceModulator,
     * and returns a modified style vector that encodes the emotion at the synthesis level.
     *
     * @param baseStyle The unmodified style vector for the current voice + token count
     * @param modulated The modulation parameters from VoiceModulator
     * @param signal The signal map from SignalExtractor
     * @param secondaryPalette Optional second voice to blend toward for emotional shifts
     * @param numTokens Token count for secondary palette lookup
     */
    fun sculpt(
        baseStyle: FloatArray,
        modulated: ModulatedVoice,
        signal: SignalMap,
        secondaryPalette: VoicePalette? = null,
        numTokens: Int = 50
    ): FloatArray {
        val style = baseStyle.copyOf()

        // 1. EMOTIONAL BLEND via voice interpolation
        if (secondaryPalette != null) {
            val blendFactor = computeBlendFactor(modulated, signal)
            if (blendFactor > 0.01f) {
                val secondaryStyle = getBaseStyle(secondaryPalette, numTokens)
                lerp(style, secondaryStyle, blendFactor)
            }
        }

        // 2. BREATHINESS via style vector perturbation
        //    breathIntensity is 0-100 from ModulatedVoice
        applyBreathiness(style, modulated.breathIntensity)

        // 3. ENERGY/INTENSITY via magnitude scaling
        applyEnergy(style, modulated.volume, signal)

        // 4. INTONATION RANGE via dimension-selective scaling
        //    Uses intonationVariation (0.0-1.0) as the primary driver
        applyIntonation(style, modulated.intonationIntensity, modulated.intonationVariation)

        // 5. EMOTION BLEND OVERRIDES
        applyEmotionBlend(style, signal.emotionBlend)

        // 6. MICRO-PERTURBATION for naturalness (replaces DSP jitter)
        //    jitterAmount is 0.0-1.0 from ModulatedVoice
        if (modulated.jitterAmount > 0.001f) {
            applyMicroPerturbation(style, modulated.jitterAmount)
        }

        // 7. NaN guard
        for (i in style.indices) {
            if (style[i].isNaN() || style[i].isInfinite()) {
                style[i] = baseStyle[i]
            }
        }

        return style
    }

    // ─── Internal manipulation functions ───────────────────────────────

    private fun computeBlendFactor(modulated: ModulatedVoice, signal: SignalMap): Float {
        val emotionalIntensity = maxOf(
            abs(modulated.pitch - 1f) * 2f,
            abs(modulated.speed - 1f),
            modulated.breathIntensity / 100f,
        )
        // Cap at 0.35 — never fully replace the primary voice
        return (emotionalIntensity * 0.5f).coerceIn(0f, 0.35f)
    }

    private fun applyBreathiness(style: FloatArray, breathIntensity: Int) {
        if (breathIntensity < 1) return

        val intensity = smoothCurve(breathIntensity / 100f) * 0.15f  // max 15% shift

        // Breathiness direction vector — heuristic approximation.
        // In production, compute once via computeBreathDirection() with
        // known clear (af_bella sid=1) and breathy (af_sky sid=4) voices.
        for (i in style.indices) {
            val direction = if (i % 2 == 0) 1f else -1f
            style[i] += direction * intensity * 0.5f
        }
    }

    private fun applyEnergy(style: FloatArray, volume: Float, signal: SignalMap) {
        val currentMag = sqrt(style.sumOf { (it * it).toDouble() }.toFloat())
        if (currentMag < 0.001f) return

        val scaleFactor = 1f + (volume - 1f) * 0.3f  // damped, max ±30%

        for (i in style.indices) {
            style[i] *= scaleFactor
        }
    }

    private fun applyIntonation(style: FloatArray, intensity: Int, variation: Float) {
        // intensity: 0-100 controls how much intonation is applied
        // variation: 0.0-1.0 controls the width of pitch range
        if (intensity < 1) return

        val mean = style.average().toFloat()
        // Map intensity (0-100) and variation (0-1) to a scale factor around 1.0
        val scaleFactor = 1f + (intensity / 100f) * (variation - 0.5f) * 0.4f

        for (i in style.indices) {
            val deviation = style[i] - mean
            style[i] = mean + deviation * scaleFactor
        }
    }

    private fun applyEmotionBlend(style: FloatArray, blend: EmotionBlend) {
        if (blend == EmotionBlend.NONE) return

        val (shift, magnitude) = when (blend) {
            EmotionBlend.NERVOUS_EXCITEMENT -> {
                Pair({ i: Int -> if (i < 128) -0.02f else 0.02f }, 1.0f)
            }
            EmotionBlend.SUPPRESSED_TENSION -> {
                Pair({ i: Int -> -(style[i] - style.average().toFloat()) * 0.08f }, 1.0f)
            }
            EmotionBlend.NOSTALGIC_WARMTH -> {
                Pair({ i: Int -> if (i % 3 == 0) 0.03f else -0.01f }, 0.8f)
            }
            EmotionBlend.RESIGNED_ACCEPTANCE -> {
                Pair({ i: Int -> -(style[i] - style.average().toFloat()) * 0.12f }, 0.7f)
            }
            EmotionBlend.WORRIED_AFFECTION -> {
                Pair({ i: Int -> if (i < 64) 0.02f else 0f }, 0.9f)
            }
            EmotionBlend.NONE -> return  // already handled above
        }

        for (i in style.indices) {
            style[i] += shift(i) * magnitude
        }
    }

    private fun applyMicroPerturbation(style: FloatArray, jitterAmount: Float) {
        // jitterAmount is 0.0-1.0 from ModulatedVoice
        val jitterScale = smoothCurve(jitterAmount) * 0.02f  // max 2% perturbation
        var seed = (System.nanoTime() % 65536).toInt()

        for (i in style.indices) {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            val noise = (seed.toFloat() / 0x7FFFFFFF.toFloat() - 0.5f) * 2f
            style[i] += noise * jitterScale
        }
    }

    // ─── Utility ───────────────────────────────────────────────────────

    private fun lerp(a: FloatArray, b: FloatArray, t: Float) {
        for (i in a.indices) {
            if (i < b.size) a[i] = a[i] * (1f - t) + b[i] * t
        }
    }

    private fun smoothCurve(v: Float): Float = v * v

    // ─── Analysis utilities (run once to build voice profiles) ─────────

    /**
     * Compute the "breath direction" vector from two voice embeddings.
     * Call with a known clear voice and a known breathy voice, e.g.:
     *   val bellaStyle = engine.getKokoroStyle(1, 50)  // af_bella (clear)
     *   val skyStyle = engine.getKokoroStyle(4, 50)    // af_sky (breathy)
     *   val breathDir = computeBreathDirection(bellaStyle, skyStyle)
     */
    fun computeBreathDirection(clearVoice: FloatArray, breathyVoice: FloatArray): FloatArray {
        val dir = FloatArray(STYLE_DIM)
        var mag = 0f
        for (i in dir.indices) {
            dir[i] = breathyVoice[i] - clearVoice[i]
            mag += dir[i] * dir[i]
        }
        mag = sqrt(mag)
        if (mag > 0.001f) {
            for (i in dir.indices) dir[i] /= mag
        }
        return dir
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var magA = 0f; var magB = 0f
        for (i in a.indices) {
            if (i >= b.size) break
            dot += a[i] * b[i]
            magA += a[i] * a[i]
            magB += b[i] * b[i]
        }
        val denom = sqrt(magA) * sqrt(magB)
        return if (denom > 0.001f) dot / denom else 0f
    }
}

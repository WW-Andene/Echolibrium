package com.echolibrium.kyokan

/**
 * Per-phoneme duration ratios based on emotion context.
 *
 * When mel-spectrogram interception lands, DurationMap feeds directly into
 * mel-space frame duplication/removal. Until then, it informs PhonemeTokenizer
 * and PhonicAnalyzer about expected timing.
 *
 * Emotion-based duration rules:
 *   - Sadness: stretch vowels 1.3x, slightly slow consonants
 *   - Excitement: compress plosives 0.8x, speed up overall
 *   - Anger: shorten pauses, sharpen plosives
 *   - Tenderness: lengthen nasals and liquids
 *   - Fatigue: stretch everything, insert longer pauses
 */
data class DurationMap(
    /** Per-phoneme class duration multipliers */
    val vowelRatio: Float = 1.0f,
    val plosiveRatio: Float = 1.0f,
    val fricativeRatio: Float = 1.0f,
    val nasalRatio: Float = 1.0f,
    val liquidRatio: Float = 1.0f,
    val pauseRatio: Float = 1.0f,
    /** Overall tempo multiplier (applied on top of per-class ratios) */
    val globalRatio: Float = 1.0f
) {
    /** Get the duration ratio for a phoneme category */
    fun ratioFor(category: PhonemeCategory): Float = when (category) {
        PhonemeCategory.VOWEL -> vowelRatio
        PhonemeCategory.PLOSIVE -> plosiveRatio
        PhonemeCategory.FRICATIVE -> fricativeRatio
        PhonemeCategory.NASAL -> nasalRatio
        PhonemeCategory.LIQUID -> liquidRatio
        PhonemeCategory.PAUSE -> pauseRatio
        PhonemeCategory.OTHER -> 1.0f
    } * globalRatio

    companion object {
        val NEUTRAL = DurationMap()

        /**
         * Build a DurationMap from emotion context.
         *
         * @param signal The current signal map
         * @param modulated The modulated voice parameters
         * @param mood Optional current mood state
         */
        fun fromContext(
            signal: SignalMap,
            modulated: ModulatedVoice,
            mood: MoodState? = null
        ): DurationMap {
            var vowel = 1.0f
            var plosive = 1.0f
            var fricative = 1.0f
            var nasal = 1.0f
            var liquid = 1.0f
            var pause = 1.0f
            var global = 1.0f

            // Sadness: stretch vowels, slow down
            if (signal.warmth == WarmthLevel.DISTRESSED || signal.emojiSad) {
                vowel *= 1.3f
                nasal *= 1.1f
                liquid *= 1.15f
                pause *= 1.2f
                global *= 0.92f
            }

            // Excitement / high arousal: compress plosives, speed up
            if (signal.trajectory == Trajectory.PEAKED || signal.capsRatio > 0.5f) {
                plosive *= 0.8f
                fricative *= 0.9f
                pause *= 0.7f
                global *= 1.08f
            }

            // Anger / urgency: sharp plosives, short pauses
            if (signal.emojiAngry || signal.urgencyType == UrgencyType.BLOCKING) {
                plosive *= 0.75f
                pause *= 0.6f
                global *= 1.05f
            }

            // Tenderness / warmth: lengthen nasals and liquids
            if (signal.warmth == WarmthLevel.HIGH || signal.emojiLove) {
                nasal *= 1.2f
                liquid *= 1.25f
                vowel *= 1.1f
            }

            // Fatigue / collapsed: stretch everything
            if (signal.trajectory == Trajectory.COLLAPSED) {
                global *= 0.88f
                vowel *= 1.15f
                pause *= 1.4f
            }

            // Flood fatigue: slight slowdown
            if (signal.floodTier == FloodTier.OVERWHELMED) {
                global *= 0.95f
                pause *= 1.1f
            }

            // Mood influence (slow-moving baseline)
            if (mood != null) {
                // Low arousal = slower
                global *= 0.9f + mood.arousal * 0.2f
                // Negative valence = stretch vowels slightly
                if (mood.valence < -0.3f) {
                    vowel *= 1.0f + (-mood.valence - 0.3f) * 0.2f
                }
            }

            return DurationMap(
                vowelRatio = vowel.coerceIn(0.6f, 1.8f),
                plosiveRatio = plosive.coerceIn(0.5f, 1.5f),
                fricativeRatio = fricative.coerceIn(0.6f, 1.5f),
                nasalRatio = nasal.coerceIn(0.7f, 1.6f),
                liquidRatio = liquid.coerceIn(0.7f, 1.6f),
                pauseRatio = pause.coerceIn(0.3f, 2.0f),
                globalRatio = global.coerceIn(0.7f, 1.4f)
            )
        }
    }
}

/** Phoneme category for duration mapping */
enum class PhonemeCategory {
    VOWEL,       // a, e, i, o, u, æ, ɑ, ɛ, ɪ, ʊ, etc.
    PLOSIVE,     // p, b, t, d, k, g
    FRICATIVE,   // f, v, s, z, ʃ, ʒ, θ, ð, h
    NASAL,       // m, n, ŋ
    LIQUID,      // l, r, ɹ, w, j
    PAUSE,       // silence, breath
    OTHER;

    companion object {
        /** Classify an IPA character into a phoneme category */
        fun classify(ipa: Char): PhonemeCategory = when (ipa) {
            // Vowels
            'a', 'e', 'i', 'o', 'u', 'ɑ', 'æ', 'ɛ', 'ɪ', 'ɔ', 'ʊ', 'ʌ',
            'ə', 'ɚ', 'ɝ', 'ɐ' -> VOWEL
            // Plosives
            'p', 'b', 't', 'd', 'k', 'g', 'ʔ' -> PLOSIVE
            // Fricatives
            'f', 'v', 's', 'z', 'ʃ', 'ʒ', 'θ', 'ð', 'h', 'ç', 'x' -> FRICATIVE
            // Nasals
            'm', 'n', 'ŋ', 'ɲ' -> NASAL
            // Liquids and glides
            'l', 'r', 'ɹ', 'w', 'j', 'ɾ', 'ɫ' -> LIQUID
            // Pause/silence markers
            ' ', '|', '‖' -> PAUSE
            else -> OTHER
        }
    }
}

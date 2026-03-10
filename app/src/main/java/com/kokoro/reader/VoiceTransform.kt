package com.kokoro.reader

import kotlin.math.roundToInt
import kotlin.random.Random

object VoiceTransform {

    // ── Gimmick definitions ───────────────────────────────────────────────────
    enum class GimmickPosition { START, MID, END, RANDOM }

    data class Gimmick(
        val type: String,
        val signalCondition: String,   // matches CommentaryCondition.type
        val frequencyCap: Int,         // 0–100 ceiling (expressiveness dial)
        val position: GimmickPosition = GimmickPosition.START
    )

    private val GIMMICK_SOUNDS = mapOf(
        "giggle" to listOf("heh heh", "ha ha ha", "heh heh heh"),
        "sigh"   to listOf("haah...", "haaah...", "haahm..."),
        "huh"    to listOf("huh?", "hm?", "huh..."),
        "mmm"    to listOf("mmm.", "mhmm."),
        "woah"   to listOf("woah!", "oh wow!", "woah..."),
        "ugh"    to listOf("ugh.", "ugh!", "uuugh."),
        "aww"    to listOf("aww.", "awww...", "aw."),
        "gasp"   to listOf("*gasp*", "oh!", "oh-"),
        "yawn"   to listOf("haaah...", "aaah...", "haahm."),
        "hmm"    to listOf("hmm.", "hmmm...", "hmm,"),
        "laugh"  to listOf("ha ha ha!", "bah ha ha", "ha ha"),
        "tsk"    to listOf("tsk.", "tsk tsk.", "tch."),
    )

    // ── Filler injection definitions (§4.1) ───────────────────────────────────
    private val FILLER_HESITATION    = listOf("um", "uh", "uhh")
    private val FILLER_THINKING      = listOf("hmm", "hm...")
    private val FILLER_STALLING      = listOf("well...", "so...", "I mean...", "like...")
    private val FILLER_ACKNOWLEDGMENT = listOf("okay so", "right so", "alright")
    private val FILLER_UNCERTAINTY    = listOf("I think...", "maybe...")

    // ── Pre-speech breath definitions (§4.3A) ─────────────────────────────────
    private val BREATH_LIGHT  = listOf("hm. ")
    private val BREATH_FULL   = listOf("hmh... ")
    private val BREATH_SHARP  = listOf("hh— ")

    // Consonants eligible for prolongation (fricatives + nasals + liquids)
    private val PROLONGABLE_CONSONANTS = setOf('s', 'm', 'n', 'f', 'v', 'l', 'r', 'z', 'h', 'w')

    fun applyGimmicks(text: String, configs: List<GimmickConfig>, signal: SignalMap): String {
        var result = text
        for (cfg in configs) {
            val condition = CommentaryCondition(cfg.type.toGimmickSignal())
            if (!condition.matches(signal)) continue
            if (Random.nextInt(100) >= cfg.frequency) continue
            val sound = GIMMICK_SOUNDS[cfg.type]?.random() ?: continue
            val pos = try { GimmickPosition.valueOf(cfg.position) } catch (_: IllegalArgumentException) { GimmickPosition.RANDOM }
            result = when (pos) {
                GimmickPosition.START -> "$sound $result"
                GimmickPosition.END   -> "$result $sound"
                GimmickPosition.MID   -> {
                    val words = result.split(" ")
                    val mid = words.size / 2
                    (words.take(mid) + listOf(sound) + words.drop(mid)).joinToString(" ")
                }
                GimmickPosition.RANDOM -> if (Random.nextBoolean()) "$sound $result" else "$result $sound"
            }
        }
        return result
    }

    // Maps gimmick type to the signal condition that should gate it.
    // Each gimmick fires only when its emotional context is appropriate.
    private fun String.toGimmickSignal() = when (this) {
        "sigh"   -> "warmth_distressed"
        "giggle" -> "emoji_happy"
        "huh"    -> "sender_unknown"
        "mmm"    -> "sender_human"
        "woah"   -> "intensity_high"
        "ugh"    -> "stakes_high"
        "aww"    -> "warmth_high"
        "gasp"   -> "urgency_expiring"
        "yawn"   -> "stakes_low"
        "hmm"    -> "intent_request"
        "laugh"  -> "stakes_fake"
        "tsk"    -> "emoji_angry"
        else     -> "always"
    }

    // ── Commentary ────────────────────────────────────────────────────────────
    fun applyCommentary(text: String, profile: VoiceProfile, signal: SignalMap, mood: MoodState? = null): String {
        val pre = profile.commentaryPools
            .filter { it.position == "pre" && it.condition.matches(signal, mood) && it.lines.isNotEmpty() }
            .filter { Random.nextInt(100) < it.frequency }
            .flatMap { it.lines }
            .randomOrNull()

        val post = profile.commentaryPools
            .filter { it.position == "post" && it.condition.matches(signal, mood) && it.lines.isNotEmpty() }
            .filter { Random.nextInt(100) < it.frequency }
            .flatMap { it.lines }
            .randomOrNull()

        return listOfNotNull(pre, text, post).joinToString(" ")
    }

    // ── Smoothing curve — maps linear 0-100 slider to gradual 0.0-1.0 ─────────
    private fun smooth(v: Int): Float = (v / 100f) * (v / 100f)

    // ── Filler injection (§4.1) ───────────────────────────────────────────────
    fun applyFillers(text: String, fillerIntensity: Int, signal: SignalMap, mood: MoodState? = null): String {
        if (fillerIntensity < 5) return text
        val smoothIntensity = smooth(fillerIntensity)
        val words = text.split(" ").toMutableList()
        val result = mutableListOf<String>()

        // Calculate filler probability based on signal + mood
        var baseProbability = smoothIntensity * 0.15f
        if (signal.unknownFactor) baseProbability += 0.15f
        if (signal.register == Register.RAW || signal.fragmented) baseProbability += 0.20f
        if (mood != null && mood.arousal > 0.65f) baseProbability += 0.25f
        if (mood != null && mood.stability < 0.5f) baseProbability += 0.15f

        // Select filler type based on signal
        val fillerPool = when {
            signal.trajectory == Trajectory.COLLAPSED -> FILLER_STALLING
            signal.has(Intent.PLEA) || signal.has(Intent.DENIAL) -> FILLER_HESITATION
            signal.unknownFactor -> FILLER_UNCERTAINTY
            mood != null && mood.arousal > 0.7f -> FILLER_HESITATION
            else -> FILLER_THINKING + FILLER_ACKNOWLEDGMENT
        }

        // Pre-text filler
        if (fillerPool.isNotEmpty() && Random.nextFloat() < baseProbability) {
            result.add(fillerPool.random())
        }

        // Inject fillers at clause breaks and before long words
        for (i in words.indices) {
            val word = words[i]
            // At comma boundaries (clause breaks)
            if (word.endsWith(",") && fillerPool.isNotEmpty() && Random.nextFloat() < baseProbability * 0.6f) {
                result.add(word)
                result.add(fillerPool.random())
                continue
            }
            // Before unusually long words (>10 chars)
            if (word.length > 10 && fillerPool.isNotEmpty() && Random.nextFloat() < baseProbability * 0.4f) {
                result.add(fillerPool.random())
            }
            result.add(word)
        }

        return result.joinToString(" ")
    }

    // ── Pre-speech breath injection (§4.3A) ───────────────────────────────────
    fun applyBreathInjection(text: String, signal: SignalMap, mood: MoodState? = null): String {
        val shouldBreathe = signal.urgencyType >= UrgencyType.REAL ||
            signal.stakesType == StakesType.EMOTIONAL ||
            signal.stakesType == StakesType.FINANCIAL ||
            signal.trajectory == Trajectory.PEAKED ||
            (mood != null && mood.arousal > 0.8f)

        if (!shouldBreathe) return text

        val breath = when {
            signal.urgencyType == UrgencyType.EXPIRING || signal.emojiShock -> BREATH_SHARP.random()
            signal.stakesType == StakesType.EMOTIONAL || signal.trajectory == Trajectory.PEAKED -> BREATH_FULL.random()
            else -> BREATH_LIGHT.random()
        }
        return "$breath$text"
    }

    // ── Question/Statement intonation (§8.1) ──────────────────────────────────
    fun applyQuestionIntonation(text: String): String {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return text
        return when {
            // Question ending: append ... to encourage rising terminal contour
            trimmed.endsWith("?") -> "$trimmed.."
            // Exclamation in high-intensity: duplicate last vowel before !
            trimmed.endsWith("!") -> {
                val beforeBang = trimmed.dropLast(1)
                val lastVowelIdx = beforeBang.indexOfLast { it.lowercaseChar() in "aeiou" }
                if (lastVowelIdx >= 0) {
                    beforeBang.substring(0, lastVowelIdx + 1) +
                        beforeBang[lastVowelIdx] +
                        beforeBang.substring(lastVowelIdx + 1) + "!"
                } else text
            }
            else -> text
        }
    }

    // ── Trailing off text marker (§4.3B / §5.3) ──────────────────────────────
    fun applyTrailingText(text: String, signal: SignalMap, mood: MoodState? = null): String {
        val shouldTrail = signal.trajectory == Trajectory.COLLAPSED ||
            (mood != null && mood.arousal < 0.3f)
        if (!shouldTrail) return text
        val trimmed = text.trimEnd()
        return if (trimmed.isNotEmpty() && !trimmed.endsWith("...")) "$trimmed..." else text
    }

    /** Replace NaN or Infinity with a safe default to prevent IllegalArgumentException in roundToInt */
    private fun Float.safeFloat(default: Float): Float =
        if (this.isNaN() || this.isInfinite()) default else this

    // ── Breathiness ───────────────────────────────────────────────────────────
    fun applyBreathiness(text: String, intensity: Int, curvePosition: Float, pause: Int): String {
        if (intensity < 5) return text
        return text.split(" ").joinToString(" ") { breathWord(it, intensity, curvePosition, pause) }
    }

    private fun breathWord(word: String, intensity: Int, curvePos: Float, pause: Int): String {
        if (word.isBlank()) return word
        val hCount = (smooth(intensity) * 4f + 0.5f).toInt().coerceIn(1, 4)
        val hStr = "h".repeat(hCount)
        val pauseStr = " ".repeat((smooth(pause) * 3f).toInt().coerceIn(0, 3))
        return when {
            curvePos < 0.2f -> "$hStr$pauseStr$word"
            curvePos < 0.4f -> { val m = word.take(1); "$hStr$m$pauseStr${word.drop(1)}" }
            curvePos < 0.6f -> "$hStr${stretchWord(word, intensity)}"
            curvePos < 0.8f -> {
                val mid = (word.length / 2).coerceAtLeast(1)
                "${word.take(mid)}$pauseStr$hStr$pauseStr${word.drop(mid)}"
            }
            else -> "$word$hStr"
        }
    }

    private fun stretchWord(word: String, intensity: Int): String {
        val vowels = setOf('a','e','i','o','u','A','E','I','O','U')
        val r = (intensity / 30).coerceIn(1, 4)
        return word.map { c -> if (c in vowels) c.toString().repeat(r) else c.toString() }.joinToString("")
    }

    // ── Stuttering with type expansion (§4.2) ────────────────────────────────
    fun applyStuttering(
        text: String, intensity: Int, position: Float, frequency: Int, pause: Int,
        stutterType: StutterType = StutterType.REPETITION
    ): String {
        if (intensity < 5 || frequency < 5) return text
        val smoothFreq = (smooth(frequency) * 100f).toInt().coerceIn(0, 100)
        return text.split(" ").joinToString(" ") { word ->
            if (word.length > 2 && Random.nextInt(100) < smoothFreq) {
                val effectiveType = if (stutterType == StutterType.MIXED) selectMixedType() else stutterType
                stutterWord(word, intensity, position, pause, effectiveType)
            } else word
        }
    }

    private fun selectMixedType(): StutterType {
        val roll = Random.nextInt(100)
        return when {
            roll < 40 -> StutterType.REPETITION
            roll < 70 -> StutterType.PROLONGATION
            roll < 90 -> StutterType.BLOCK
            else      -> StutterType.REVISION
        }
    }

    private fun stutterWord(
        word: String, intensity: Int, position: Float, pause: Int,
        type: StutterType
    ): String {
        if (word.length < 2) return word
        return when (type) {
            StutterType.REPETITION -> stutterRepetition(word, intensity, position, pause)
            StutterType.PROLONGATION -> stutterProlongation(word, intensity)
            StutterType.BLOCK -> stutterBlock(word)
            StutterType.REVISION -> stutterRevision(word)
            StutterType.MIXED -> stutterRepetition(word, intensity, position, pause)  // fallback
        }
    }

    private fun stutterRepetition(word: String, intensity: Int, position: Float, pause: Int): String {
        val safePosition = position.safeFloat(0f)
        val repeats = (smooth(intensity) * 3f + 1f).toInt().coerceIn(1, 4)
        val pauseStr = "-".repeat((smooth(pause) * 3f).toInt().coerceIn(0, 3))
        val idx = (word.length * safePosition).roundToInt().coerceIn(0, word.length - 1)
        val endIdx = minOf(idx + 2, word.length)
        val syllable = word.substring(idx, endIdx)
        val stutter = (1..repeats).joinToString(pauseStr) { syllable } + pauseStr
        return word.substring(0, idx) + stutter + word.substring(idx)
    }

    // PROLONGATION: stretch first consonant cluster (§4.2)
    private fun stutterProlongation(word: String, intensity: Int): String {
        val firstChar = word.first().lowercaseChar()
        // Only prolong fricatives, nasals, liquids — plosives can't be prolonged
        return if (firstChar in PROLONGABLE_CONSONANTS) {
            val repeatCount = (smooth(intensity) * 3f + 1f).toInt().coerceIn(2, 4)
            firstChar.toString().repeat(repeatCount) + word.drop(1)
        } else {
            // Fall back to repetition for plosives
            "${word.take(2)}-${word.take(2)}-$word"
        }
    }

    // BLOCK: silence/pause before word onset (§4.2)
    private fun stutterBlock(word: String): String {
        // Partial onset attempt then block
        return "${word.first()}—$word"
    }

    // REVISION: start-abandon-restart (§4.2)
    private fun stutterRevision(word: String): String {
        val fragment = word.take((word.length / 2).coerceAtLeast(1))
        return "$fragment— $word"
    }

    // ── Intonation ────────────────────────────────────────────────────────────
    fun applyIntonation(text: String, intensity: Int, variation: Float): String {
        if (intensity < 5) return text
        val safeVariation = variation.safeFloat(0.5f)
        return text.split(" ").mapIndexed { i, word ->
            val stressed = i % (3 - (safeVariation * 2).roundToInt()).coerceAtLeast(1) == 0
            if (stressed) intonateWord(word, intensity, safeVariation) else word
        }.joinToString(" ")
    }

    private fun intonateWord(word: String, intensity: Int, variation: Float): String {
        if (word.isBlank()) return word
        val vowels = setOf('a','e','i','o','u','A','E','I','O','U')
        val stretch = (intensity / 35).coerceIn(1, 3)
        val stretched = word.map { c ->
            if (c in vowels && Random.nextFloat() < (0.3f + variation * 0.5f))
                c.toString().repeat(stretch) else c.toString()
        }.joinToString("")
        return if (intensity > 60 && Random.nextFloat() < variation) "$stretched..." else stretched
    }

    // ── Wording rules ─────────────────────────────────────────────────────────
    fun applyWordingRules(text: String, rules: List<Pair<String, String>>): String {
        var result = text
        for ((find, replace) in rules) {
            if (find.isNotBlank()) result = result.replace(find, replace, ignoreCase = true)
        }
        return result
    }

    // ── Full pipeline ─────────────────────────────────────────────────────────
    fun process(
        text: String,
        profile: VoiceProfile,
        modulated: ModulatedVoice,
        signal: SignalMap,
        rules: List<Pair<String, String>>,
        mood: MoodState? = null
    ): String {
        return try {
            var r = applyWordingRules(text, rules)
            r = applyCommentary(r, profile, signal, mood)
            r = applyFillers(r, profile.fillerIntensity, signal, mood)      // §4.1
            r = applyBreathInjection(r, signal, mood)                       // §4.3A
            r = applyGimmicks(r, profile.gimmicks, signal)
            r = applyQuestionIntonation(r)                                  // §8.1
            r = applyIntonation(r, modulated.intonationIntensity, modulated.intonationVariation)
            r = applyStuttering(r, modulated.stutterIntensity, modulated.stutterPosition,
                modulated.stutterFrequency, modulated.stutterPause, profile.stutterType)  // §4.2
            r = applyBreathiness(r, modulated.breathIntensity, modulated.breathCurvePosition, modulated.breathPause)
            r = applyTrailingText(r, signal, mood)                          // §4.3B/§5.3
            r
        } catch (e: Exception) {
            android.util.Log.e("VoiceTransform", "Error in text processing pipeline", e)
            text  // Fallback to original text on processing error
        }
    }
}

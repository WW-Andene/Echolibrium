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
        "giggle" to listOf("hehe", "hehehe", "haha"),
        "sigh"   to listOf("hhhhhh...", "hhhhh...", "haah..."),
        "huh"    to listOf("huh?", "hm?", "huh..."),
        "mmm"    to listOf("mmm.", "mhmm."),
        "woah"   to listOf("woah!", "oh wow!", "woah..."),
        "ugh"    to listOf("ugh.", "ugh!", "uuugh."),
        "aww"    to listOf("aww.", "awww...", "aw."),
        "gasp"   to listOf("*gasp*", "oh!", "oh-"),
        "yawn"   to listOf("haaah...", "aaah...", "haahm."),
        "hmm"    to listOf("hmm.", "hmmm...", "hmm,"),
        "laugh"  to listOf("hah!", "ha.", "hahah."),
        "tsk"    to listOf("tsk.", "tsk tsk.", "tch."),
    )

    fun applyGimmicks(text: String, configs: List<GimmickConfig>, signal: SignalMap): String {
        var result = text
        for (cfg in configs) {
            val condition = CommentaryCondition(cfg.type.toGimmickSignal())
            if (!condition.matches(signal)) continue
            if (Random.nextInt(100) >= cfg.frequency) continue
            val sound = GIMMICK_SOUNDS[cfg.type]?.random() ?: continue
            val pos = GimmickPosition.valueOf(cfg.position)
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

    // Maps gimmick type to the signal condition that should gate it
    private fun String.toGimmickSignal() = when (this) {
        "sigh"   -> "warmth_distressed"   // sadness, apology, collapse
        "giggle" -> "warmth_high"         // warm, happy, love emoji
        "huh"    -> "sender_unknown"      // confusion, unknown
        "mmm"    -> "intent_request"      // someone needs something
        "woah"   -> "traj_peaked"         // peaked intensity
        "ugh"    -> "urgency_real"        // genuine urgency or demand
        "aww"    -> "emoji_sad"           // sad emoji or emotional
        "gasp"   -> "stakes_high"         // high stakes anything
        "yawn"   -> "stakes_low"          // nothing happening
        "hmm"    -> "always"              // general uncertainty
        "laugh"  -> "stakes_fake"         // game / fake stakes
        "tsk"    -> "flooded"             // too many notifications
        else     -> "always"
    }

    // ── Commentary ────────────────────────────────────────────────────────────
    fun applyCommentary(text: String, profile: VoiceProfile, signal: SignalMap): String {
        val pre = profile.commentaryPools
            .filter { it.position == "pre" && it.condition.matches(signal) && it.lines.isNotEmpty() }
            .filter { Random.nextInt(100) < it.frequency }
            .flatMap { it.lines }
            .randomOrNull()

        val post = profile.commentaryPools
            .filter { it.position == "post" && it.condition.matches(signal) && it.lines.isNotEmpty() }
            .filter { Random.nextInt(100) < it.frequency }
            .flatMap { it.lines }
            .randomOrNull()

        return listOfNotNull(pre, text, post).joinToString(" ")
    }

    // ── Breathiness ───────────────────────────────────────────────────────────
    fun applyBreathiness(text: String, intensity: Int, curvePosition: Float, pause: Int): String {
        if (intensity == 0) return text
        return text.split(" ").joinToString(" ") { breathWord(it, intensity, curvePosition, pause) }
    }

    private fun breathWord(word: String, intensity: Int, curvePos: Float, pause: Int): String {
        if (word.isBlank()) return word
        val hCount = (intensity / 10).coerceIn(1, 10)
        val hStr = "h".repeat(hCount)
        val pauseStr = " ".repeat((pause / 25).coerceIn(0, 4))
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

    // ── Stuttering ────────────────────────────────────────────────────────────
    fun applyStuttering(text: String, intensity: Int, position: Float, frequency: Int, pause: Int): String {
        if (intensity == 0 || frequency == 0) return text
        return text.split(" ").joinToString(" ") { word ->
            if (word.length > 2 && Random.nextInt(100) < frequency)
                stutterWord(word, intensity, position, pause)
            else word
        }
    }

    private fun stutterWord(word: String, intensity: Int, position: Float, pause: Int): String {
        val repeats = (intensity / 25).coerceIn(1, 4)
        val pauseStr = "-".repeat((pause / 30).coerceIn(0, 3))
        val idx = (word.length * position).roundToInt().coerceIn(0, word.length - 1)
        val sylLen = if (idx + 2 <= word.length) 2 else 1
        val syllable = word.substring(idx, idx + sylLen)
        val stutter = (1..repeats).joinToString(pauseStr) { syllable } + pauseStr
        return word.substring(0, idx) + stutter + word.substring(idx)
    }

    // ── Intonation ────────────────────────────────────────────────────────────
    fun applyIntonation(text: String, intensity: Int, variation: Float): String {
        if (intensity == 0) return text
        return text.split(" ").mapIndexed { i, word ->
            val stressed = i % (3 - (variation * 2).roundToInt()).coerceAtLeast(1) == 0
            if (stressed) intonateWord(word, intensity, variation) else word
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
        rules: List<Pair<String, String>>
    ): String {
        var r = applyWordingRules(text, rules)
        r = applyCommentary(r, profile, signal)
        r = applyGimmicks(r, profile.gimmicks, signal)
        r = applyIntonation(r, modulated.intonationIntensity, modulated.intonationVariation)
        r = applyStuttering(r, modulated.stutterIntensity, modulated.stutterPosition, modulated.stutterFrequency, modulated.stutterPause)
        r = applyBreathiness(r, modulated.breathIntensity, modulated.breathCurvePosition, modulated.breathPause)
        return r
    }
}

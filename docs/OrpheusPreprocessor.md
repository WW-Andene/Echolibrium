package com.echolibrium.kyokan

/**
 * Orpheus text preprocessor — personality-driven voice direction.
 *
 * The voice (tara, leo, zoe) is just timbre — an audio asset.
 * The personality (EchoPersonality) is who speaks through that voice.
 * This preprocessor translates personality + content → delivery instructions
 * that Orpheus renders as emotionally authentic speech.
 *
 * The same notification processed through different personalities produces
 * different text output → different Orpheus delivery → different experience.
 *
 * Only used for cloud voices. Kokoro and Piper receive raw text untouched.
 */
object OrpheusPreprocessor {

    // ── Orpheus vocal sound tags ────────────────────────────────────────────
    private const val LAUGH   = "<laugh>"
    private const val CHUCKLE = "<chuckle>"
    private const val SIGH    = "<sigh>"
    private const val GASP    = "<gasp>"
    private const val GROAN   = "<groan>"
    private const val COUGH   = "<cough>"
    private const val SNIFFLE = "<sniffle>"

    // ── Tone categories ─────────────────────────────────────────────────────
    enum class Tone { NEUTRAL, JOY, HUMOR, SAD, SURPRISE, FRUSTRATION, URGENCY, CASUAL, INTIMACY }

    // ── Pattern banks ───────────────────────────────────────────────────────

    private val JOY_PATTERNS = listOf(
        Regex("\\b(congrat(?:ulation)?s?|congrats)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(won|winner|victory|champion|promoted|accepted|approved)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(amazing|awesome|fantastic|incredible|wonderful|brilliant|perfect)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(happy|excited|thrilled|delighted|proud|grateful)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(yay|woohoo|hooray|yess+)\\b", RegexOption.IGNORE_CASE),
        Regex("[!]{2,}"),
        Regex("[😂🤣😄😁🎉🥳🏆❤️🎊💪✨🙌]"),
    )

    private val HUMOR_PATTERNS = listOf(
        Regex("\\b(haha+|hehe+|lol|lmao|rofl|funny|hilarious|joke)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(bruh|lmfao|dead|i can'?t|screaming)\\b", RegexOption.IGNORE_CASE),
        Regex("[😂🤣😆😹]"),
    )

    private val SAD_PATTERNS = listOf(
        Regex("\\b(sorry|apolog(?:y|ize|ies)|unfortunately|regret)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(cancel+ed|denied|rejected|failed|expired|declined|refused)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(lost|missed|gone|removed|deleted|passed away|died)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(sad|disappointed|upset|heartbroken|devastated)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(goodbye|farewell|miss you|take care)\\b", RegexOption.IGNORE_CASE),
        Regex("[😢😭💔😞😔]"),
    )

    private val SURPRISE_PATTERNS = listOf(
        Regex("\\b(wow|whoa|omg|oh my go[ds]|no way|what the|holy)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(unexpected|unbelievable|shocking|insane|wild|crazy)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(breaking|just in|happening now)\\b", RegexOption.IGNORE_CASE),
        Regex("[😱😮🤯😳]"),
    )

    private val FRUSTRATION_PATTERNS = listOf(
        Regex("\\b(error|fail(?:ed|ure)?|crash(?:ed)?|broken|bug|issue|problem)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(stuck|blocked|timeout|unavailable|refused|denied)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(ugh|damn|crap|annoying|frustrated|ridiculous)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(again|still not|why won'?t)\\b", RegexOption.IGNORE_CASE),
        Regex("[😤😠🤦💢]"),
    )

    private val URGENCY_PATTERNS = listOf(
        Regex("\\b(urgent|emergency|alert|warning|critical|immediately|asap|now)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(deadline|overdue|expir(?:es?|ing|ed)|last chance|final)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(hurry|rush|quick|right away|time sensitive)\\b", RegexOption.IGNORE_CASE),
        Regex("[🚨⚠️🔴❗‼️]"),
    )

    private val CASUAL_PATTERNS = listOf(
        Regex("\\b(hey|hi|sup|yo|what'?s up|how'?s it going)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(gonna|wanna|gotta|kinda|sorta|dunno|idk)\\b", RegexOption.IGNORE_CASE),
        Regex("[👋😊🙂👍😎]"),
    )

    private val INTIMACY_PATTERNS = listOf(
        Regex("\\b(love you|miss you|thinking of you|good night|sweet dreams)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(babe|baby|honey|darling|sweetheart)\\b", RegexOption.IGNORE_CASE),
        Regex("[❤️💕😘🥰💗💞]"),
    )

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Process notification text through a personality lens.
     *
     * @param text        Raw notification text
     * @param personality The active EchoPersonality (or null for default behavior)
     * @param appName     Optional source app name for context detection
     * @return Text shaped by personality for Orpheus rendering
     */
    fun process(
        text: String,
        personality: EchoPersonality? = null,
        appName: String? = null
    ): String {
        if (text.isBlank() || text.length < 5) return text

        val p = personality ?: EchoPersonality()  // defaults if no personality loaded yet
        val tone = detectTone(text)
        val cleaned = cleanForSpeech(text)
        val shaped = shapeForPersonality(cleaned, tone, p, appName)
        return injectTag(shaped, tone, p)
    }

    // ── Tone detection ──────────────────────────────────────────────────────

    private fun detectTone(text: String): Tone {
        val scores = mapOf(
            Tone.HUMOR       to score(text, HUMOR_PATTERNS) * 2,
            Tone.URGENCY     to score(text, URGENCY_PATTERNS) * 2,
            Tone.INTIMACY    to score(text, INTIMACY_PATTERNS) * 2,
            Tone.JOY         to score(text, JOY_PATTERNS),
            Tone.SURPRISE    to score(text, SURPRISE_PATTERNS),
            Tone.FRUSTRATION to score(text, FRUSTRATION_PATTERNS),
            Tone.SAD         to score(text, SAD_PATTERNS),
            Tone.CASUAL      to score(text, CASUAL_PATTERNS),
        )
        val best = scores.maxByOrNull { it.value } ?: return Tone.NEUTRAL
        return if (best.value >= 2) best.key else Tone.NEUTRAL
    }

    private fun score(text: String, patterns: List<Regex>): Int =
        patterns.count { it.containsMatchIn(text) }

    // ── Text cleanup ────────────────────────────────────────────────────────

    private fun cleanForSpeech(text: String): String {
        var t = text
        t = t.replace(Regex("[\\p{So}\\p{Sk}]"), "")
        t = t.replace(Regex("[!]{3,}"), "!!")
        t = t.replace(Regex("[?]{3,}"), "??")
        t = t.replace(Regex("\\.{4,}"), "...")
        t = t.replace(Regex("https?://\\S+"), "")
        t = t.replace(Regex("#(\\w+)"), "$1")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    // ── Personality-driven text shaping ──────────────────────────────────────

    private fun shapeForPersonality(
        text: String, tone: Tone, p: EchoPersonality, appName: String?
    ): String {

        return when (tone) {

            Tone.URGENCY -> {
                // High neuroticism amplifies urgency → more fragmentation
                // Low neuroticism (stoic) → deliver it straight, no drama
                if (p.neuroticism > 0.5f || p.emotionalRange > 0.7f) {
                    fragmentForUrgency(text)
                } else {
                    text  // stoic delivers urgency calmly
                }
            }

            Tone.SAD -> {
                // High empathy → trailing ellipsis, slower pacing cues
                // Low empathy → factual delivery, no trailing
                if (p.empathyLevel > 0.5f) {
                    if (!text.endsWith("...") && !text.endsWith(".")) {
                        "$text..."
                    } else if (text.endsWith(".") && !text.endsWith("..")) {
                        text.dropLast(1) + "..."
                    } else text
                } else {
                    // Low empathy: clean period. No lingering.
                    if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) {
                        "$text."
                    } else text
                }
            }

            Tone.HUMOR -> {
                // Sarcastic personality might deadpan what others find funny
                // High humor frequency amplifies, low dampens
                if (p.sarcasmTendency > 0.5f && p.humorStyle == "dry") {
                    // Deadpan: strip exclamation marks, let flat delivery do the work
                    text.replace("!!", ".").replace("!", ".")
                } else {
                    text
                }
            }

            Tone.JOY -> {
                // High extraversion → let the energy flow
                // Low extraversion → contained reaction
                if (p.extraversion < 0.3f) {
                    // Introvert: tone down multiple exclamation marks
                    text.replace("!!", "!").replace("!!", "!")
                } else {
                    text
                }
            }

            Tone.INTIMACY -> {
                // High agreeableness + high trust → soft trailing delivery
                // Low trust / avoidant attachment → keep it neutral
                if (p.agreeableness > 0.6f && p.trustLevel > 0.4f) {
                    if (!text.endsWith("...")) "$text..." else text
                } else {
                    text
                }
            }

            Tone.CASUAL -> text   // personality comes through in tag choice, not reshaping
            Tone.SURPRISE -> text // tags handle this
            Tone.FRUSTRATION -> text
            Tone.NEUTRAL -> text
        }
    }

    private fun fragmentForUrgency(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        return sentences.joinToString(" ") { sentence ->
            val words = sentence.split(" ")
            if (words.size > 12) {
                val breakWords = setOf("due", "because", "from", "with", "after", "before", "until", "by", "for", "and", "but", "so")
                val breakIdx = words.indexOfLast { it.lowercase() in breakWords && words.indexOf(it) > 3 }
                if (breakIdx > 3) {
                    val first = words.subList(0, breakIdx).joinToString(" ").trimEnd(',', ';')
                    val second = words.subList(breakIdx, words.size).joinToString(" ")
                        .replaceFirst(Regex("^(due to|because of|from|with|after|before)\\s+", RegexOption.IGNORE_CASE), "")
                    "$first. ${second.replaceFirstChar { it.uppercase() }}"
                } else sentence
            } else sentence
        }
    }

    // ── Personality-driven tag injection ─────────────────────────────────────

    private fun injectTag(text: String, tone: Tone, p: EchoPersonality): String {
        // Short text: no tag regardless of personality
        if (text.length < 20) return text

        // Tag restraint check: the personality's threshold for using vocal sounds
        // tagRestraint 0.0 = uses tags freely, 1.0 = almost never
        // We need the tone to be "strong enough" to overcome the restraint
        val toneStrength = when (tone) {
            Tone.NEUTRAL, Tone.CASUAL -> 0.0f
            Tone.HUMOR -> 0.5f + (p.humorFrequency * 0.3f)
            Tone.JOY -> 0.6f
            Tone.SURPRISE -> 0.8f   // surprise breaks through most restraint
            Tone.URGENCY -> 0.9f    // urgency breaks through almost all restraint
            Tone.SAD -> 0.4f + (p.empathyLevel * 0.3f)
            Tone.FRUSTRATION -> 0.5f
            Tone.INTIMACY -> 0.3f + (p.agreeableness * 0.3f)
        }

        // If tone isn't strong enough to overcome this personality's restraint, no tag
        if (toneStrength < p.tagRestraint) return text

        // Choose tag based on tone × personality
        return when (tone) {
            Tone.HUMOR -> {
                when (p.humorStyle) {
                    "warm" -> "$text $CHUCKLE"
                    "dry", "deadpan" -> text  // dry humor doesn't laugh at itself
                    "dark" -> "$text $CHUCKLE"
                    "absurd" -> "$text $LAUGH"
                    else -> "$text $CHUCKLE"
                }
            }

            Tone.JOY -> {
                // Extraverts laugh more, introverts chuckle or stay quiet
                if (p.extraversion > 0.6f) "$text $LAUGH"
                else if (p.extraversion > 0.3f) "$text $CHUCKLE"
                else text
            }

            Tone.SURPRISE -> {
                // Almost everyone gasps at genuine surprise
                // Except very low neuroticism (stoic)
                if (p.neuroticism > 0.1f || p.emotionalRange > 0.3f) "$GASP $text"
                else text
            }

            Tone.URGENCY -> {
                // High neuroticism gasps, low neuroticism stays level
                if (p.neuroticism > 0.3f) "$GASP $text"
                else text
            }

            Tone.SAD -> {
                // High empathy sighs, low empathy stays clean
                if (p.empathyLevel > 0.5f) "$text $SIGH"
                else text
            }

            Tone.FRUSTRATION -> {
                // High agreeableness sighs (resignation), low agreeableness groans (annoyance)
                if (p.agreeableness > 0.6f) "$text $SIGH"
                else "$text $GROAN"
            }

            Tone.INTIMACY -> {
                // Only with high trust and high agreeableness
                if (p.trustLevel > 0.5f && p.agreeableness > 0.6f) "$text $SIGH"  // content sigh
                else text
            }

            Tone.CASUAL, Tone.NEUTRAL -> text
        }
    }

    // ── Quirk processing (future: Custom Core feeds these) ──────────────────

    /**
     * Apply personality quirks to the processed text.
     * Called after main processing. Quirks are string-matched behavioral rules
     * that the Custom Core adds/removes as the personality evolves.
     *
     * This is the hook for Tamagotchi growth — new quirks appear over time,
     * changing how the personality reacts to content it's seen many times.
     */
    fun applyQuirks(text: String, tone: Tone, personality: EchoPersonality): String {
        var result = text

        for (quirk in personality.quirks) {
            result = when {
                // Time-based quirks
                quirk.contains("late at night") && isLateNight() -> {
                    // Soften delivery: replace ! with .
                    result.replace("!", ".")
                }
                quirk.contains("morning") && isMorning() -> {
                    result // placeholder for morning-specific behavior
                }

                // Tone-based quirks
                quirk.contains("sighs before bad news") && tone == Tone.SAD -> {
                    if (!result.startsWith(SIGH)) "$SIGH $result" else result
                }
                quirk.contains("deadpan") && tone == Tone.HUMOR -> {
                    result.replace("!!", ".").replace("!", ".")
                }
                quirk.contains("gasps dramatically") && tone == Tone.NEUTRAL -> {
                    // The wildcard personality finds drama in mundane things
                    if (personality.emotionalRange > 0.8f && Math.random() < 0.15) {
                        "$GASP $result"
                    } else result
                }
                quirk.contains("groans at corporate") -> {
                    val corporate = Regex("\\b(synergy|leverage|bandwidth|circle back|deep dive|align|pivot|stakeholder)\\b", RegexOption.IGNORE_CASE)
                    if (corporate.containsMatchIn(result)) "$result $GROAN" else result
                }

                else -> result
            }
        }

        return result
    }

    private fun isLateNight(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 6
    }

    private fun isMorning(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour in 6..9
    }
}

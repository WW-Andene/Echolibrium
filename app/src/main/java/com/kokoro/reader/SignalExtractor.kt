package com.kokoro.reader

/**
 * Reads raw notification data and produces a SignalMap.
 * No LLM — pure pattern matching. Honest about its limits.
 * Will miss subtle cases. Works on obvious ones.
 *
 * Enhanced with:
 *   - Negation detection (inverts urgency/distress under negation)
 *   - Intensity modifier scaling (amplifiers, diminishers, maximizers)
 *   - Sarcasm detection (pattern-matching for ironic content)
 *   - Emotional blend detection (complex mixed emotional states)
 */
object SignalExtractor {

    // ── Emoji maps ────────────────────────────────────────────────────────────
    private val HAPPY_EMOJI  = setOf("😊","😄","😁","😀","🙂","😃","😆","😂","🤣","😍","🥰","😎","🎉","🎊","✨","💪","👍","🔥","❤️","💕","💖","💗","💓","💞","😸","🥳","😝","😛","🤩")
    private val SAD_EMOJI    = setOf("😔","😢","😭","😞","😟","😕","🙁","☹️","💔","😿","😓","😥","🥺","😩","😫","😪","😴")
    private val ANGRY_EMOJI  = setOf("😠","😡","🤬","💢","👿","😤","🖕","😾")
    private val LOVE_EMOJI   = setOf("❤️","💕","💖","💗","💓","💞","💘","💝","🥰","😍","😘","💋","🫶","🤍","💙","💛","💚","💜","🖤")
    private val SHOCK_EMOJI  = setOf("😱","😳","🫢","😮","😯","😲","🤯","👀","‼️","⁉️")

    private val NUMERIC_SENDER_RE = Regex(".*\\d{5,}.*")

    // ── Game app patterns ─────────────────────────────────────────────────────
    private val GAME_PACKAGES = listOf("game","clash","blizzard","supercell","king","zynga","epic","roblox","minecraft","brave","souls","brawl","legends","pubg","fortnite","pokemon","candy","farm")
    private val GAME_WORDS    = listOf("energy","stamina","attack","raid","base","clan","guild","level","xp","reward","chest","loot","battle","arena","league","village","city","troops","spell","hero","quest","mission","daily","bonus")

    // ── Financial patterns ────────────────────────────────────────────────────
    private val FINANCIAL_SOURCES  = listOf("bank","paypal","stripe","revolut","wise","cash","venmo","finance","payment","invoice","billing","wallet")
    private val FINANCIAL_WORDS    = listOf("payment","transaction","transfer","balance","deposit","withdrawal","credit","debit","invoice","amount","dollar","euro","pound","yen","\$","€","£","¥","paid","charged","refund","validation","confirm","verify","authorization")

    // ── Service/platform patterns ─────────────────────────────────────────────
    private val SERVICE_SOURCES   = listOf("amazon","fedex","ups","dhl","usps","laposte","colissimo","delivery","tracking","shop","store","order","package","parcel")
    private val PLATFORM_SOURCES  = listOf("twitch","youtube","spotify","netflix","discord","reddit","twitter","instagram","tiktok","snapchat","telegram")

    // ── Intent patterns ───────────────────────────────────────────────────────
    private val REQUEST_WORDS   = listOf("could you","can you","would you","please","pls","plz","help me","need you","lend","borrow","send me","give me","can i","do you mind","would you mind")
    private val APOLOGY_WORDS   = listOf("sorry","i'm sorry","im sorry","apologize","forgive","my bad","my fault","i didn't mean","i didn't know")
    private val DENIAL_WORDS    = listOf("i swear","i didn't","i never","i promise","believe me","it wasn't me","not true","i can explain","false")
    private val PLEA_WORDS      = listOf("please come back","pls come back","don't leave","come back","miss you","need you back","don't go","stay")
    private val GREETING_WORDS  = listOf("hello","hi","hey","good morning","good evening","good night","howdy","sup","what's up","how are you")
    private val URGENT_WORDS    = listOf("urgent","asap","immediately","now","right now","emergency","help","sos","quickly","hurry","fast","important","critical")

    // ── Negation patterns (§2.1) ──────────────────────────────────────────────
    private val NEGATION_WORDS = setOf(
        "not", "no", "don't", "didn't", "won't", "isn't", "aren't",
        "never", "calm", "relax", "fine", "ok", "okay", "all good"
    )
    private val REASSURANCE_PHRASES = listOf(
        "don't worry", "no worries", "false alarm", "no need to worry",
        "calm down", "it's fine", "it's okay", "all good", "no problem"
    )

    // ── Intensity modifier maps (§2.2) ────────────────────────────────────────
    private val AMPLIFIERS = setOf(
        "very", "really", "extremely", "absolutely", "completely",
        "totally", "insanely", "so", "super"
    )
    private val DIMINISHERS = setOf(
        "a bit", "slightly", "kinda", "kind of", "a little", "somewhat",
        "maybe", "might be", "possibly", "guess", "sort of"
    )
    private val MAXIMIZERS = setOf(
        "cannot", "impossible", "have to", "must", "critical", "no choice"
    )

    // ── Sarcasm patterns (§8.2) ────────────────────────────────────────────────
    private val SARCASM_STARTERS = listOf("oh wow", "great", "wonderful", "fantastic", "amazing", "brilliant", "lovely", "perfect")
    private val SARCASM_MARKERS  = listOf("obviously", "clearly", "of course", "sure", "totally", "right", "yeah right")
    private val COMPLAINT_WORDS  = listOf("broken", "failed", "wrong", "terrible", "awful", "worst", "useless", "stupid", "annoying", "problem", "issue", "bug", "error", "crash")

    // ── Intensity word weights ────────────────────────────────────────────────
    private val INTENSITY_WORDS = mapOf(
        "fuck" to 0.4f, "shit" to 0.3f, "damn" to 0.2f, "hate" to 0.25f,
        "love" to 0.15f, "miss" to 0.1f, "please" to 0.1f, "pls" to 0.08f,
        "urgent" to 0.35f, "emergency" to 0.4f, "asap" to 0.3f,
        "!!!" to 0.3f, "!!" to 0.2f, "???" to 0.25f, "??" to 0.15f,
        "i swear" to 0.2f, "never" to 0.15f, "always" to 0.1f,
        "ghosting" to 0.3f, "cheating" to 0.35f, "cheat" to 0.3f,
        "attack" to 0.15f, "under attack" to 0.15f
    )

    // ── Main extraction ───────────────────────────────────────────────────────
    fun extract(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        hourOfDay: Int,
        floodCount: Int
    ): SignalMap {
        return try {
            extractInternal(packageName, appName, title, text, hourOfDay, floodCount)
        } catch (e: Exception) {
            android.util.Log.e("SignalExtractor", "Error extracting signal", e)
            SignalMap()  // Return safe defaults on extraction error
        }
    }

    private fun extractInternal(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        hourOfDay: Int,
        floodCount: Int
    ): SignalMap {

        val fullText = "$title $text".trim()
        val lower    = fullText.lowercase()
        val pkg      = packageName.lowercase()
        val app      = appName.lowercase()

        // ── Emoji detection ───────────────────────────────────────────────────
        val emojiHappy = HAPPY_EMOJI.any  { fullText.contains(it) }
        val emojiSad   = SAD_EMOJI.any    { fullText.contains(it) }
        val emojiAngry = ANGRY_EMOJI.any  { fullText.contains(it) }
        val emojiLove  = LOVE_EMOJI.any   { fullText.contains(it) }
        val emojiShock = SHOCK_EMOJI.any  { fullText.contains(it) }

        // ── Negation scan (§2.1) ──────────────────────────────────────────────
        val tokens = lower.split(Regex("\\s+"))
        val hasReassurance = REASSURANCE_PHRASES.count { lower.contains(it) } >= 2
        val negatedStressCount = countNegatedStress(tokens)

        // ── Source type ───────────────────────────────────────────────────────
        val sourceType = when {
            GAME_PACKAGES.any { pkg.contains(it) || app.contains(it) } -> SourceType.GAME
            FINANCIAL_SOURCES.any { pkg.contains(it) || app.contains(it) } -> SourceType.FINANCIAL
            SERVICE_SOURCES.any   { pkg.contains(it) || app.contains(it) } -> SourceType.SERVICE
            PLATFORM_SOURCES.any  { pkg.contains(it) || app.contains(it) } -> SourceType.PLATFORM
            pkg.contains("android") || pkg.contains("system") || pkg.contains("settings") -> SourceType.SYSTEM
            else -> SourceType.PERSONAL
        }

        // ── Sender type ───────────────────────────────────────────────────────
        val senderType = when (sourceType) {
            SourceType.PERSONAL -> {
                if (title.length in 2..30 && !title.contains("@") && !title.contains("http")) SenderType.HUMAN
                else SenderType.BOT
            }
            SourceType.GAME, SourceType.SERVICE, SourceType.FINANCIAL,
            SourceType.PLATFORM, SourceType.SYSTEM -> SenderType.BOT
            else -> SenderType.UNKNOWN
        }

        val isUnknown = title.matches(NUMERIC_SENDER_RE) ||
                        title.contains("unknown", ignoreCase = true) ||
                        title.isBlank()

        // ── Intent detection ──────────────────────────────────────────────────
        val intents = mutableSetOf<Intent>()
        if (REQUEST_WORDS.any { lower.contains(it) })         intents.add(Intent.REQUEST)
        if (APOLOGY_WORDS.any { lower.contains(it) })         intents.add(Intent.REASSURANCE)
        if (DENIAL_WORDS.any  { lower.contains(it) })         intents.add(Intent.DENIAL)
        if (PLEA_WORDS.any    { lower.contains(it) })         intents.add(Intent.PLEA)
        if (GREETING_WORDS.any{ lower.contains(it) })         intents.add(Intent.GREETING)
        if (URGENT_WORDS.any  { lower.contains(it) })         intents.add(Intent.ALERT)
        if (lower.contains("?"))                               intents.add(Intent.REQUEST)
        if (GAME_WORDS.any    { lower.contains(it) } && sourceType == SourceType.GAME) intents.add(Intent.INFORM)
        if (FINANCIAL_WORDS.any{ lower.contains(it) })        intents.add(Intent.ACTION_REQUIRED)
        if (lower.contains("delivered") || lower.contains("shipped") || lower.contains("tracking")) intents.add(Intent.INFORM)
        if (lower.contains("live") || lower.contains("streaming") || lower.contains("started")) intents.add(Intent.INVITE)
        // Reassurance intent (§2.1): negation + stress combos suggest calming message
        if (hasReassurance || negatedStressCount >= 2) intents.add(Intent.REASSURANCE)
        if (intents.isEmpty()) intents.add(Intent.INFORM)

        // ── Stakes ────────────────────────────────────────────────────────────
        val stakesType = when {
            sourceType == SourceType.FINANCIAL || FINANCIAL_WORDS.any { lower.contains(it) } -> StakesType.FINANCIAL
            senderType == SenderType.HUMAN && (intents.contains(Intent.PLEA) || intents.contains(Intent.DENIAL)) -> StakesType.EMOTIONAL
            sourceType == SourceType.GAME -> StakesType.FAKE
            sourceType == SourceType.SYSTEM -> StakesType.TECHNICAL
            else -> StakesType.NONE
        }
        val stakesLevel = when (stakesType) {
            StakesType.FINANCIAL -> StakesLevel.HIGH
            StakesType.EMOTIONAL -> StakesLevel.HIGH
            StakesType.FAKE      -> StakesLevel.LOW
            StakesType.TECHNICAL -> StakesLevel.LOW
            else -> if (senderType == SenderType.HUMAN) StakesLevel.MEDIUM else StakesLevel.LOW
        }

        // ── Urgency ───────────────────────────────────────────────────────────
        val urgencyType = when {
            pkg.contains("phone") || pkg.contains("call") || pkg.contains("dialer") -> UrgencyType.EXPIRING
            intents.contains(Intent.ACTION_REQUIRED) && stakesType == StakesType.FINANCIAL -> UrgencyType.BLOCKING
            // Negated urgency words reduce to SOFT instead of REAL (§2.1)
            URGENT_WORDS.any { lower.contains(it) } -> if (negatedStressCount > 0) UrgencyType.SOFT else UrgencyType.REAL
            lower.contains("before you leave") || lower.contains("when you get back") -> UrgencyType.SOFT
            sourceType == SourceType.GAME -> UrgencyType.NONE
            intents.contains(Intent.INVITE) -> UrgencyType.SOFT
            else -> UrgencyType.NONE
        }

        // ── Warmth ────────────────────────────────────────────────────────────
        val warmth = when {
            emojiSad && (intents.contains(Intent.PLEA) || intents.contains(Intent.DENIAL)) -> WarmthLevel.DISTRESSED
            emojiAngry -> WarmthLevel.NONE
            emojiLove || emojiHappy -> WarmthLevel.HIGH
            senderType == SenderType.HUMAN && APOLOGY_WORDS.any { lower.contains(it) } -> WarmthLevel.MEDIUM
            // Reassurance messages carry warmth (§2.1)
            intents.contains(Intent.REASSURANCE) -> WarmthLevel.MEDIUM
            senderType == SenderType.HUMAN -> WarmthLevel.MEDIUM
            else -> WarmthLevel.NONE
        }

        // ── Register ──────────────────────────────────────────────────────────
        val lines     = text.split("\n").filter { it.isNotBlank() }
        val fragmented = lines.size >= 3 || text.contains("…") || text.contains("...")
        val capsRatio = if (text.isNotBlank()) text.count { it.isUpperCase() }.toFloat() / text.length else 0f
        val register = when {
            fragmented && senderType == SenderType.HUMAN -> Register.RAW
            capsRatio > 0.4f -> Register.DRAMATIC
            sourceType == SourceType.FINANCIAL || sourceType == SourceType.SERVICE -> Register.FORMAL
            sourceType == SourceType.SYSTEM -> Register.TECHNICAL
            senderType == SenderType.HUMAN -> Register.CASUAL
            else -> Register.MINIMAL
        }

        // ── Trajectory & intensity (with modifier scaling §2.2) ──────────────
        val lineScores = if (lines.size > 1) {
            lines.map { line -> scoreIntensity(line) }
        } else {
            listOf(scoreIntensity(text))
        }

        val intensityLevel = (lineScores.maxOrNull() ?: 0f).coerceIn(0f, 1f)

        val trajectory = when {
            lineScores.size <= 1 -> Trajectory.FLAT
            lineScores.last() < lineScores.max() * 0.5f -> Trajectory.COLLAPSED
            lineScores.zipWithNext().all { (a, b) -> b >= a } -> Trajectory.BUILDING
            lineScores.last() == lineScores.max() -> Trajectory.PEAKED
            else -> Trajectory.FLAT
        }

        // ── Sarcasm detection (§8.2) ──────────────────────────────────────────
        val detectedSarcasm = detectSarcasm(lower, fullText, capsRatio)

        // ── Emotional blend detection (§2.3) ──────────────────────────────────
        val emotionBlend = detectBlend(
            urgencyType, warmth, emojiHappy, emojiLove, emojiSad,
            register, intensityLevel, trajectory, stakesType, senderType
        )

        // ── Flood tier (§8.3) ─────────────────────────────────────────────────
        val floodTier = FloodTier.from(floodCount)

        return SignalMap(
            sourceType      = sourceType,
            senderType      = senderType,
            intents         = intents,
            stakesLevel     = stakesLevel,
            stakesType      = stakesType,
            urgencyType     = urgencyType,
            warmth          = warmth,
            register        = register,
            emojiHappy      = emojiHappy,
            emojiSad        = emojiSad,
            emojiAngry      = emojiAngry,
            emojiLove       = emojiLove,
            emojiShock      = emojiShock,
            fragmented      = fragmented,
            capsRatio       = capsRatio,
            unknownFactor   = isUnknown,
            actionNeeded    = intents.contains(Intent.ACTION_REQUIRED) || urgencyType == UrgencyType.EXPIRING,
            intensityLevel  = intensityLevel,
            trajectory      = trajectory,
            hourOfDay       = hourOfDay,
            floodCount      = floodCount,
            emotionBlend    = emotionBlend,
            detectedSarcasm = detectedSarcasm,
            floodTier       = floodTier
        )
    }

    // ── Negation scan (§2.1) ──────────────────────────────────────────────────
    // Count how many stress/urgency keywords appear within 3 tokens of a negator
    private fun countNegatedStress(tokens: List<String>): Int {
        val stressWords = URGENT_WORDS + listOf("worry", "panic", "scared", "afraid", "problem")
        var count = 0
        for (i in tokens.indices) {
            if (tokens[i] in NEGATION_WORDS) {
                val window = tokens.subList(i + 1, minOf(i + 4, tokens.size))
                if (window.any { w -> stressWords.any { w.contains(it) } }) count++
            }
        }
        return count
    }

    // ── Intensity scoring with modifier scaling (§2.2) ────────────────────────
    private fun scoreIntensity(line: String): Float {
        val lower = line.lowercase()
        val tokens = lower.split(Regex("\\s+"))
        var score = 0f

        // Apply modifier-aware scoring
        INTENSITY_WORDS.forEach { (word, weight) ->
            if (lower.contains(word)) {
                val multiplier = findModifier(tokens, word)
                score += weight * multiplier
            }
        }

        // punctuation amplifiers
        val exclamations = line.count { it == '!' }
        val questions    = line.count { it == '?' }
        score += exclamations * 0.1f
        score += questions    * 0.08f
        // caps
        val capsRatio = if (line.isNotBlank()) line.count { it.isUpperCase() }.toFloat() / line.length else 0f
        score += capsRatio * 0.3f
        return score.coerceIn(0f, 1f)
    }

    // Find the modifier multiplier for a keyword based on preceding words
    private fun findModifier(tokens: List<String>, keyword: String): Float {
        if (tokens.isEmpty()) return 1.0f
        val idx = tokens.indexOfFirst { it.contains(keyword) }
        if (idx <= 0) return 1.0f
        val preceding = tokens.subList(maxOf(0, idx - 2), idx).joinToString(" ")
        return when {
            MAXIMIZERS.any  { preceding.contains(it) } -> 2.0f
            AMPLIFIERS.any  { preceding.contains(it) } -> 1.5f
            DIMINISHERS.any { preceding.contains(it) } -> 0.4f
            else -> 1.0f
        }
    }

    // ── Sarcasm detection (§8.2) ──────────────────────────────────────────────
    private fun detectSarcasm(lower: String, fullText: String, capsRatio: Float): Boolean {
        // Sarcasm starter + complaint combo
        val hasStarter   = SARCASM_STARTERS.any { lower.startsWith(it) || lower.contains(". $it") }
        val hasComplaint = COMPLAINT_WORDS.any { lower.contains(it) }
        if (hasStarter && hasComplaint) return true

        // Sarcasm markers overuse
        val markerCount = SARCASM_MARKERS.count { lower.contains(it) }
        if (markerCount >= 2) return true

        // "Thanks" at end of complaint
        if ((lower.endsWith("thanks") || lower.endsWith("thanks a lot")) && hasComplaint) return true

        // Excessive exclamations with otherwise flat content
        val exclCount = fullText.count { it == '!' }
        if (exclCount >= 4 && capsRatio < 0.1f) return true

        // ALL CAPS on positive words in otherwise lowercase text
        val posWords = listOf("AMAZING", "LOVE", "GREAT", "WONDERFUL", "FANTASTIC", "PERFECT")
        if (capsRatio < 0.3f && posWords.any { fullText.contains(it) }) return true

        return false
    }

    // ── Emotional blend detection (§2.3) ──────────────────────────────────────
    private fun detectBlend(
        urgency: UrgencyType, warmth: WarmthLevel,
        emojiHappy: Boolean, emojiLove: Boolean, emojiSad: Boolean,
        register: Register, intensity: Float,
        trajectory: Trajectory, stakes: StakesType, sender: SenderType
    ): EmotionBlend = when {
        // NERVOUS_EXCITEMENT: urgency + warmth + positive emoji
        urgency >= UrgencyType.REAL && warmth >= WarmthLevel.MEDIUM && (emojiHappy || emojiLove) ->
            EmotionBlend.NERVOUS_EXCITEMENT

        // SUPPRESSED_TENSION: soft urgency + formal register + low intensity
        urgency >= UrgencyType.SOFT && register == Register.FORMAL && intensity < 0.5f ->
            EmotionBlend.SUPPRESSED_TENSION

        // NOSTALGIC_WARMTH: high warmth + collapsed trajectory + emotional stakes
        warmth == WarmthLevel.HIGH && trajectory == Trajectory.COLLAPSED && stakes == StakesType.EMOTIONAL ->
            EmotionBlend.NOSTALGIC_WARMTH

        // RESIGNED_ACCEPTANCE: collapsed trajectory + low intensity + human sender
        trajectory == Trajectory.COLLAPSED && intensity < 0.3f && sender == SenderType.HUMAN ->
            EmotionBlend.RESIGNED_ACCEPTANCE

        // WORRIED_AFFECTION: warmth + soft urgency + sad emoji or plea
        warmth >= WarmthLevel.MEDIUM && urgency >= UrgencyType.SOFT && emojiSad ->
            EmotionBlend.WORRIED_AFFECTION

        else -> EmotionBlend.NONE
    }
}

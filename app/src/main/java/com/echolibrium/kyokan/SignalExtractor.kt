package com.echolibrium.kyokan

/**
 * Reads raw notification data and produces a SignalMap.
 * No LLM — pure pattern matching. Honest about its limits.
 * Will miss subtle cases. Works on obvious ones.
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

    // ── Intensity word weights ────────────────────────────────────────────────
    // each word adds to the intensity score
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
                // heuristic: if title looks like a name (short, no special chars) = human
                if (title.length in 2..30 && !title.contains("@") && !title.contains("http")) SenderType.HUMAN
                else SenderType.BOT
            }
            SourceType.GAME, SourceType.SERVICE, SourceType.FINANCIAL,
            SourceType.PLATFORM, SourceType.SYSTEM -> SenderType.BOT
            else -> SenderType.UNKNOWN
        }

        val isUnknown = title.matches(NUMERIC_SENDER_RE) || // number-only "sender"
                        title.contains("unknown", ignoreCase = true) ||
                        title.isBlank()

        // ── Intent detection ──────────────────────────────────────────────────
        val intents = mutableSetOf<Intent>()
        if (REQUEST_WORDS.any { lower.contains(it) })         intents.add(Intent.REQUEST)
        if (APOLOGY_WORDS.any { lower.contains(it) })         intents.add(Intent.REQUEST) // apology often accompanies request
        if (DENIAL_WORDS.any  { lower.contains(it) })         intents.add(Intent.DENIAL)
        if (PLEA_WORDS.any    { lower.contains(it) })         intents.add(Intent.PLEA)
        if (GREETING_WORDS.any{ lower.contains(it) })         intents.add(Intent.GREETING)
        if (URGENT_WORDS.any  { lower.contains(it) })         intents.add(Intent.ALERT)
        if (lower.contains("?"))                               intents.add(Intent.REQUEST)
        if (GAME_WORDS.any    { lower.contains(it) } && sourceType == SourceType.GAME) intents.add(Intent.INFORM)
        if (FINANCIAL_WORDS.any{ lower.contains(it) })        intents.add(Intent.ACTION_REQUIRED)
        if (lower.contains("delivered") || lower.contains("shipped") || lower.contains("tracking")) intents.add(Intent.INFORM)
        if (lower.contains("live") || lower.contains("streaming") || lower.contains("started")) intents.add(Intent.INVITE)
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
            URGENT_WORDS.any { lower.contains(it) } -> UrgencyType.REAL
            lower.contains("before you leave") || lower.contains("when you get back") -> UrgencyType.SOFT
            sourceType == SourceType.GAME -> UrgencyType.NONE
            intents.contains(Intent.INVITE) -> UrgencyType.SOFT // live stream = soft urgency
            else -> UrgencyType.NONE
        }

        // ── Warmth ────────────────────────────────────────────────────────────
        val warmth = when {
            emojiSad && (intents.contains(Intent.PLEA) || intents.contains(Intent.DENIAL)) -> WarmthLevel.DISTRESSED
            emojiAngry -> WarmthLevel.NONE
            emojiLove || emojiHappy -> WarmthLevel.HIGH
            senderType == SenderType.HUMAN && APOLOGY_WORDS.any { lower.contains(it) } -> WarmthLevel.MEDIUM
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

        // ── Trajectory & intensity ────────────────────────────────────────────
        // Score each line's intensity then look at the arc
        val lineScores = if (lines.size > 1) {
            lines.map { line -> scoreIntensity(line) }
        } else {
            listOf(scoreIntensity(text))
        }

        val intensityLevel = (lineScores.maxOrNull() ?: 0f).coerceIn(0f, 1f)

        val trajectory = when {
            lineScores.size <= 1 -> Trajectory.FLAT
            lineScores.last() < lineScores.max() * 0.5f -> Trajectory.COLLAPSED // peaked then dropped
            lineScores.zipWithNext().all { (a, b) -> b >= a } -> Trajectory.BUILDING // always rising
            lineScores.last() == lineScores.max() -> Trajectory.PEAKED // ends at peak
            else -> Trajectory.FLAT
        }

        // ── Emotion blend detection ──────────────────────────────────────────
        val emotionBlend = detectBlend(
            urgencyType, warmth, emojiHappy, emojiLove, emojiSad,
            register, intensityLevel, trajectory, stakesType, senderType
        )

        // ── Sarcasm detection ────────────────────────────────────────────────
        val detectedSarcasm = detectSarcasm(lower, capsRatio)

        // ── Flood tier ───────────────────────────────────────────────────────
        val floodTier = FloodTier.from(floodCount)

        return SignalMap(
            sourceType    = sourceType,
            senderType    = senderType,
            intents       = intents,
            stakesLevel   = stakesLevel,
            stakesType    = stakesType,
            urgencyType   = urgencyType,
            warmth        = warmth,
            register      = register,
            emojiHappy    = emojiHappy,
            emojiSad      = emojiSad,
            emojiAngry    = emojiAngry,
            emojiLove     = emojiLove,
            emojiShock    = emojiShock,
            fragmented    = fragmented,
            capsRatio     = capsRatio,
            unknownFactor = isUnknown,
            actionNeeded  = intents.contains(Intent.ACTION_REQUIRED) || urgencyType == UrgencyType.EXPIRING,
            intensityLevel = intensityLevel,
            trajectory    = trajectory,
            hourOfDay     = hourOfDay,
            floodCount    = floodCount,
            emotionBlend  = emotionBlend,
            detectedSarcasm = detectedSarcasm,
            floodTier     = floodTier
        )
    }

    // ── Emotion blend detection ────────────────────────────────────────────
    private fun detectBlend(
        urgency: UrgencyType, warmth: WarmthLevel,
        emojiHappy: Boolean, emojiLove: Boolean, emojiSad: Boolean,
        register: Register, intensity: Float, trajectory: Trajectory,
        stakesType: StakesType, senderType: SenderType
    ): EmotionBlend = when {
        urgency >= UrgencyType.REAL && warmth >= WarmthLevel.MEDIUM && (emojiHappy || emojiLove) ->
            EmotionBlend.NERVOUS_EXCITEMENT
        urgency >= UrgencyType.SOFT && register == Register.FORMAL && intensity < 0.5f ->
            EmotionBlend.SUPPRESSED_TENSION
        warmth == WarmthLevel.HIGH && trajectory == Trajectory.COLLAPSED && stakesType == StakesType.EMOTIONAL ->
            EmotionBlend.NOSTALGIC_WARMTH
        trajectory == Trajectory.COLLAPSED && intensity < 0.3f && senderType == SenderType.HUMAN ->
            EmotionBlend.RESIGNED_ACCEPTANCE
        warmth >= WarmthLevel.MEDIUM && urgency >= UrgencyType.SOFT && emojiSad ->
            EmotionBlend.WORRIED_AFFECTION
        else -> EmotionBlend.NONE
    }

    // ── Sarcasm detection ───────────────────────────────────────────────────
    private val SARCASM_STARTERS = listOf("oh wow", "great", "oh great", "wonderful", "fantastic", "brilliant", "lovely", "oh sure", "yeah right")
    private val SARCASM_MARKERS  = listOf("obviously", "of course", "sure", "right", "totally", "absolutely", "clearly")
    private val COMPLAINT_WORDS  = listOf("broke", "crash", "fail", "error", "bug", "wrong", "bad", "terrible", "awful", "worse", "broken", "useless", "stupid")

    private fun detectSarcasm(lower: String, capsRatio: Float): Boolean {
        // Sarcasm starter + complaint combo
        if (SARCASM_STARTERS.any { lower.contains(it) } && COMPLAINT_WORDS.any { lower.contains(it) }) return true
        // Multiple sarcasm markers
        if (SARCASM_MARKERS.count { lower.contains(it) } >= 2) return true
        // "Thanks" at end with complaints
        if ((lower.endsWith("thanks") || lower.endsWith("thanks a lot")) && COMPLAINT_WORDS.any { lower.contains(it) }) return true
        // Excessive exclamations with low caps (fake enthusiasm)
        if (lower.count { it == '!' } >= 4 && capsRatio < 0.1f) return true
        return false
    }

    private fun scoreIntensity(line: String): Float {
        val lower = line.lowercase()
        var score = 0f
        INTENSITY_WORDS.forEach { (word, weight) -> if (lower.contains(word)) score += weight }
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
}

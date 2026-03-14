package com.echolibrium.kyokan

// ── Signal dimensions ─────────────────────────────────────────────────────────

enum class SourceType   { GAME, PERSONAL, SERVICE, PLATFORM, FINANCIAL, SYSTEM, UNKNOWN }
enum class SenderType   { HUMAN, BOT, SYSTEM, UNKNOWN }
enum class Intent       { INFORM, REQUEST, ALERT, INVITE, ACTION_REQUIRED, DENIAL, PLEA, GREETING, SHARING, REASSURANCE }
enum class StakesLevel  { NONE, LOW, MEDIUM, HIGH }
enum class StakesType   { NONE, FAKE, FINANCIAL, EMOTIONAL, TECHNICAL, PHYSICAL }
enum class UrgencyType  { NONE, SOFT, REAL, EXPIRING, BLOCKING }
enum class WarmthLevel  { NONE, LOW, MEDIUM, HIGH, DISTRESSED }
enum class Register     { MINIMAL, CASUAL, FORMAL, DRAMATIC, RAW, TECHNICAL }
enum class Trajectory   { FLAT, BUILDING, PEAKED, COLLAPSED }

/** Blended emotional states — acoustically distinct from pure signals */
enum class EmotionBlend { NONE, NERVOUS_EXCITEMENT, SUPPRESSED_TENSION, NOSTALGIC_WARMTH, RESIGNED_ACCEPTANCE, WORRIED_AFFECTION }

/** Flood tiers based on daily notification count */
enum class FloodTier { CALM, ACTIVE, BUSY, FLOODED, OVERWHELMED;
    companion object {
        fun from(count: Int) = when {
            count <= 5  -> CALM
            count <= 15 -> ACTIVE
            count <= 30 -> BUSY
            count <= 50 -> FLOODED
            else        -> OVERWHELMED
        }
    }
}

// ── Sub-objects for organized signal grouping ─────────────────────────────────

/** Source identity and sender history */
data class SourceContext(
    val sourceType:     SourceType = SourceType.UNKNOWN,
    val senderType:     SenderType = SenderType.UNKNOWN,
    val unknownFactor:  Boolean    = false,
    val senderRepeat:   Int        = 0,
    val senderRecency:  Long       = Long.MAX_VALUE,
    val senderPressure: Float      = 0f
)

/** Emotional valence — emoji flags and blended state */
data class EmotionalSignals(
    val warmth:          WarmthLevel  = WarmthLevel.NONE,
    val emojiHappy:      Boolean      = false,
    val emojiSad:        Boolean      = false,
    val emojiAngry:      Boolean      = false,
    val emojiLove:       Boolean      = false,
    val emojiShock:      Boolean      = false,
    val emotionBlend:    EmotionBlend = EmotionBlend.NONE,
    val detectedSarcasm: Boolean      = false
)

/** Intensity, urgency, and message structure */
data class IntensityMetrics(
    val urgencyType:    UrgencyType = UrgencyType.NONE,
    val intensityLevel: Float       = 0f,
    val trajectory:     Trajectory  = Trajectory.FLAT,
    val capsRatio:      Float       = 0f,
    val actionNeeded:   Boolean     = false,
    val register:       Register    = Register.MINIMAL,
    val fragmented:     Boolean     = false
)

/** Stakes assessment */
data class StakesContext(
    val stakesLevel: StakesLevel = StakesLevel.NONE,
    val stakesType:  StakesType  = StakesType.NONE
)

/** Temporal and flood context */
data class TemporalContext(
    val hourOfDay:  Int       = 12,
    val floodCount: Int       = 0,
    val floodTier:  FloodTier = FloodTier.CALM,
    val tokenCount: Int       = 0
)

// ── Main signal map ──────────────────────────────────────────────────────────

data class SignalMap(
    val source:    SourceContext    = SourceContext(),
    val emotion:   EmotionalSignals = EmotionalSignals(),
    val intensity: IntensityMetrics = IntensityMetrics(),
    val stakes:    StakesContext    = StakesContext(),
    val temporal:  TemporalContext  = TemporalContext(),
    val intents:   Set<Intent>     = emptySet()
) {
    // ── Delegating properties for backward compatibility ─────────────────
    // All existing signal.fieldName access continues to work unchanged.

    // Source
    val sourceType     get() = source.sourceType
    val senderType     get() = source.senderType
    val unknownFactor  get() = source.unknownFactor
    val senderRepeat   get() = source.senderRepeat
    val senderRecency  get() = source.senderRecency
    val senderPressure get() = source.senderPressure

    // Emotional
    val warmth          get() = emotion.warmth
    val emojiHappy      get() = emotion.emojiHappy
    val emojiSad        get() = emotion.emojiSad
    val emojiAngry      get() = emotion.emojiAngry
    val emojiLove       get() = emotion.emojiLove
    val emojiShock      get() = emotion.emojiShock
    val emotionBlend    get() = emotion.emotionBlend
    val detectedSarcasm get() = emotion.detectedSarcasm

    // Intensity
    val urgencyType    get() = intensity.urgencyType
    val intensityLevel get() = intensity.intensityLevel
    val trajectory     get() = intensity.trajectory
    val capsRatio      get() = intensity.capsRatio
    val actionNeeded   get() = intensity.actionNeeded
    val register       get() = intensity.register
    val fragmented     get() = intensity.fragmented

    // Stakes
    val stakesLevel get() = stakes.stakesLevel
    val stakesType  get() = stakes.stakesType

    // Temporal
    val hourOfDay  get() = temporal.hourOfDay
    val floodCount get() = temporal.floodCount
    val floodTier  get() = temporal.floodTier
    val tokenCount get() = temporal.tokenCount

    // ── Convenience checks ───────────────────────────────────────────────
    fun has(intent: Intent) = intents.contains(intent)
    fun isHuman()   = senderType == SenderType.HUMAN
    fun isGame()    = sourceType == SourceType.GAME
    fun isSystem()  = senderType == SenderType.SYSTEM || senderType == SenderType.BOT
    fun isNight()   = hourOfDay >= 22 || hourOfDay < 6
    fun isMorning() = hourOfDay in 7..10
    fun isFlooded() = floodCount >= 5
}

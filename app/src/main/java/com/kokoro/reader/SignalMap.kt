package com.kokoro.reader

// ── Signal dimensions ─────────────────────────────────────────────────────────

enum class SourceType   { GAME, PERSONAL, SERVICE, PLATFORM, FINANCIAL, SYSTEM, UNKNOWN }
enum class SenderType   { HUMAN, BOT, SYSTEM, UNKNOWN }
enum class Intent       { INFORM, REQUEST, ALERT, INVITE, ACTION_REQUIRED, DENIAL, PLEA, GREETING, SHARING }
enum class StakesLevel  { NONE, LOW, MEDIUM, HIGH }
enum class StakesType   { NONE, FAKE, FINANCIAL, EMOTIONAL, TECHNICAL, PHYSICAL }
enum class UrgencyType  { NONE, SOFT, REAL, EXPIRING, BLOCKING }
enum class WarmthLevel  { NONE, LOW, MEDIUM, HIGH, DISTRESSED }
enum class Register     { MINIMAL, CASUAL, FORMAL, DRAMATIC, RAW, TECHNICAL }
enum class Trajectory   { FLAT, BUILDING, PEAKED, COLLAPSED }

data class SignalMap(
    // Source & sender
    val sourceType:     SourceType  = SourceType.UNKNOWN,
    val senderType:     SenderType  = SenderType.UNKNOWN,

    // Intent
    val intents:        Set<Intent> = emptySet(),

    // Stakes
    val stakesLevel:    StakesLevel = StakesLevel.NONE,
    val stakesType:     StakesType  = StakesType.NONE,

    // Urgency
    val urgencyType:    UrgencyType = UrgencyType.NONE,

    // Emotional
    val warmth:         WarmthLevel = WarmthLevel.NONE,
    val register:       Register    = Register.MINIMAL,

    // Emoji sentiment
    val emojiHappy:     Boolean = false,
    val emojiSad:       Boolean = false,
    val emojiAngry:     Boolean = false,
    val emojiLove:      Boolean = false,
    val emojiShock:     Boolean = false,

    // Message structure
    val fragmented:     Boolean = false,      // multiple short lines
    val capsRatio:      Float   = 0f,         // 0.0–1.0
    val unknownFactor:  Boolean = false,      // absence of info is the signal
    val actionNeeded:   Boolean = false,

    // Intensity
    val intensityLevel: Float      = 0f,      // 0.0–1.0 overall intensity
    val trajectory:     Trajectory = Trajectory.FLAT,

    // Context
    val hourOfDay:      Int = 12,
    val floodCount:     Int = 0
) {
    // Convenience checks for condition matching
    fun has(intent: Intent) = intents.contains(intent)
    fun isHuman()   = senderType == SenderType.HUMAN
    fun isGame()    = sourceType == SourceType.GAME
    fun isSystem()  = senderType == SenderType.SYSTEM || senderType == SenderType.BOT
    fun isNight()   = hourOfDay >= 22 || hourOfDay < 6
    fun isMorning() = hourOfDay in 7..10
    fun isFlooded() = floodCount >= 5
}

package com.kokoro.reader

import org.json.JSONArray
import org.json.JSONObject

data class CommentaryCondition(
    val type: String = "always",
    val value: String = ""
) {
    /** Match against signal only (backward-compatible) */
    fun matches(signal: SignalMap): Boolean = matches(signal, null)

    /** Match against signal and optional mood state */
    fun matches(signal: SignalMap, mood: MoodState?): Boolean = when (type) {
        "always"            -> true
        "source_game"       -> signal.isGame()
        "source_personal"   -> signal.sourceType == SourceType.PERSONAL
        "source_financial"  -> signal.sourceType == SourceType.FINANCIAL
        "source_service"    -> signal.sourceType == SourceType.SERVICE
        "source_platform"   -> signal.sourceType == SourceType.PLATFORM
        "source_system"     -> signal.isSystem()
        "sender_human"      -> signal.isHuman()
        "sender_unknown"    -> signal.unknownFactor
        "intent_request"    -> signal.has(Intent.REQUEST)
        "intent_plea"       -> signal.has(Intent.PLEA)
        "intent_denial"     -> signal.has(Intent.DENIAL)
        "intent_alert"      -> signal.has(Intent.ALERT)
        "intent_greeting"   -> signal.has(Intent.GREETING)
        "intent_invite"     -> signal.has(Intent.INVITE)
        "intent_action"     -> signal.has(Intent.ACTION_REQUIRED)
        "intent_reassurance"-> signal.has(Intent.REASSURANCE)
        "stakes_fake"       -> signal.stakesType == StakesType.FAKE
        "stakes_financial"  -> signal.stakesType == StakesType.FINANCIAL
        "stakes_emotional"  -> signal.stakesType == StakesType.EMOTIONAL
        "stakes_high"       -> signal.stakesLevel == StakesLevel.HIGH
        "stakes_low"        -> signal.stakesLevel == StakesLevel.LOW || signal.stakesLevel == StakesLevel.NONE
        "urgency_none"      -> signal.urgencyType == UrgencyType.NONE
        "urgency_real"      -> signal.urgencyType == UrgencyType.REAL || signal.urgencyType == UrgencyType.BLOCKING
        "urgency_expiring"  -> signal.urgencyType == UrgencyType.EXPIRING
        "warmth_high"       -> signal.warmth == WarmthLevel.HIGH || signal.warmth == WarmthLevel.DISTRESSED
        "warmth_distressed" -> signal.warmth == WarmthLevel.DISTRESSED
        "emoji_sad"         -> signal.emojiSad
        "emoji_happy"       -> signal.emojiHappy
        "emoji_angry"       -> signal.emojiAngry
        "emoji_love"        -> signal.emojiLove
        "intensity_low"     -> signal.intensityLevel < 0.2f
        "intensity_high"    -> signal.intensityLevel > 0.5f
        "traj_building"     -> signal.trajectory == Trajectory.BUILDING
        "traj_peaked"       -> signal.trajectory == Trajectory.PEAKED
        "traj_collapsed"    -> signal.trajectory == Trajectory.COLLAPSED
        "time_night"        -> signal.isNight()
        "time_morning"      -> signal.isMorning()
        "flooded"           -> signal.isFlooded()
        "sarcasm_detected"  -> signal.detectedSarcasm
        // ── Mood-based conditions (§1.0) ──────────────────────────────────
        "mood_tired"        -> mood != null && mood.arousal < 0.2f
        "mood_tense"        -> mood != null && mood.arousal > 0.7f && mood.valence < -0.3f
        "mood_content"      -> mood != null && mood.valence > 0.5f && mood.stability > 0.7f
        "mood_overwhelmed"  -> mood != null && mood.arousal > 0.8f && mood.valence < -0.5f
        "mood_flat"         -> mood != null && mood.arousal < 0.3f && mood.stability > 0.8f
        // ── Sender history conditions (§7.0) ──────────────────────────────
        "sender_repeat_2"   -> signal.senderRepeat == 2
        "sender_repeat_3"   -> signal.senderRepeat >= 3
        "sender_rapid"      -> signal.senderRecency < 300_000L  // 2+ within 5 minutes
        "sender_persistent" -> signal.senderPressure > 0.6f
        // ── Flood tiers (§8.3) ────────────────────────────────────────────
        "flood_busy"        -> signal.floodTier >= FloodTier.BUSY
        "flood_overwhelmed" -> signal.floodTier >= FloodTier.OVERWHELMED
        else -> false
    }

    fun label(): String = when (type) {
        "always"            -> "Always"
        "source_game"       -> "Game notification"
        "source_personal"   -> "Personal message"
        "source_financial"  -> "Financial / bank"
        "source_service"    -> "Delivery / service"
        "source_platform"   -> "Platform (Twitch, YouTube…)"
        "source_system"     -> "System / bot"
        "sender_human"      -> "From a human"
        "sender_unknown"    -> "Unknown sender"
        "intent_request"    -> "Asking for something"
        "intent_plea"       -> "Pleading"
        "intent_denial"     -> "Denying something"
        "intent_alert"      -> "Urgent alert"
        "intent_greeting"   -> "Greeting"
        "intent_invite"     -> "Invitation / going live"
        "intent_action"     -> "Action required"
        "intent_reassurance"-> "Reassuring message"
        "stakes_fake"       -> "Fake stakes (game)"
        "stakes_financial"  -> "Real money involved"
        "stakes_emotional"  -> "Emotional stakes"
        "stakes_high"       -> "High stakes"
        "stakes_low"        -> "Low / no stakes"
        "urgency_none"      -> "Not urgent"
        "urgency_real"      -> "Genuinely urgent"
        "urgency_expiring"  -> "Expires now (call)"
        "warmth_high"       -> "Warm message"
        "warmth_distressed" -> "Distressed / in pain"
        "emoji_sad"         -> "Sad emoji"
        "emoji_happy"       -> "Happy emoji"
        "emoji_angry"       -> "Angry emoji"
        "emoji_love"        -> "Love emoji"
        "intensity_low"     -> "Low intensity"
        "intensity_high"    -> "High intensity"
        "traj_building"     -> "Escalating message"
        "traj_peaked"       -> "Peaks at the end"
        "traj_collapsed"    -> "Broke down / collapsed"
        "time_night"        -> "Night time"
        "time_morning"      -> "Morning"
        "flooded"           -> "Many notifications today"
        "sarcasm_detected"  -> "Sarcasm detected"
        "mood_tired"        -> "Mood: tired"
        "mood_tense"        -> "Mood: tense"
        "mood_content"      -> "Mood: content"
        "mood_overwhelmed"  -> "Mood: overwhelmed"
        "mood_flat"         -> "Mood: flat"
        "sender_repeat_2"   -> "2nd message from sender"
        "sender_repeat_3"   -> "3rd+ message from sender"
        "sender_rapid"      -> "Rapid sender (< 5min)"
        "sender_persistent" -> "Persistent sender"
        "flood_busy"        -> "Flood: busy (16+)"
        "flood_overwhelmed" -> "Flood: overwhelmed (51+)"
        else -> type
    }

    fun toJson() = JSONObject().apply { put("type", type); put("value", value) }

    companion object {
        fun fromJson(j: JSONObject) = CommentaryCondition(
            j.optString("type", "always"), j.optString("value", "")
        )

        val ALL_TYPES = listOf(
            "always",
            "source_game", "source_personal", "source_financial", "source_service",
            "source_platform", "source_system", "sender_human", "sender_unknown",
            "intent_request", "intent_plea", "intent_denial", "intent_alert",
            "intent_greeting", "intent_invite", "intent_action", "intent_reassurance",
            "stakes_fake", "stakes_financial", "stakes_emotional", "stakes_high", "stakes_low",
            "urgency_none", "urgency_real", "urgency_expiring",
            "warmth_high", "warmth_distressed",
            "emoji_sad", "emoji_happy", "emoji_angry", "emoji_love",
            "intensity_low", "intensity_high",
            "traj_building", "traj_peaked", "traj_collapsed",
            "time_night", "time_morning", "flooded",
            "sarcasm_detected",
            "mood_tired", "mood_tense", "mood_content", "mood_overwhelmed", "mood_flat",
            "sender_repeat_2", "sender_repeat_3", "sender_rapid", "sender_persistent",
            "flood_busy", "flood_overwhelmed"
        )
    }
}

data class CommentaryPool(
    val id: String = java.util.UUID.randomUUID().toString(),
    val position: String = "pre",
    val condition: CommentaryCondition = CommentaryCondition(),
    val lines: List<String> = emptyList(),
    val frequency: Int = 40
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("position", position)
        put("condition", condition.toJson())
        val la = JSONArray(); lines.forEach { la.put(it) }; put("lines", la)
        put("frequency", frequency)
    }

    companion object {
        fun fromJson(j: JSONObject) = CommentaryPool(
            id        = j.optString("id", java.util.UUID.randomUUID().toString()),
            position  = j.optString("position", "pre"),
            condition = CommentaryCondition.fromJson(j.optJSONObject("condition") ?: JSONObject()),
            lines     = j.optJSONArray("lines")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it, null) }
            } ?: emptyList(),
            frequency = j.optInt("frequency", 40)
        )
    }
}

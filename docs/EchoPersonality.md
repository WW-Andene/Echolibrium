package com.echolibrium.kyokan

import org.json.JSONObject

/**
 * EchoPersonality — the soul layer applied to any voice asset.
 *
 * The voice (tara, leo, zoe) is just timbre — a mouth.
 * The personality is who speaks THROUGH that mouth.
 * Same personality on different voices = same person, different body.
 * Different personalities on same voice = different people, same body.
 *
 * This schema is designed to:
 *   - Be rich enough for a Tamagotchi that evolves over months
 *   - Be structured enough for the preprocessor to consume as input
 *   - Be serializable to echo_behavior.json for the Custom Core
 *   - Feed into the system prompt when self-hosted Orpheus is available
 *
 * The Custom Core (SQLite + Groq) updates this over time based on
 * user interaction patterns, time-of-day habits, notification history,
 * and explicit user feedback.
 */
data class EchoPersonality(

    // ── Identity ────────────────────────────────────────────────────────────
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Echo",                  // user-facing name
    val archetype: String = "companion",        // companion | narrator | assistant | wildcard
    val createdAt: Long = System.currentTimeMillis(),

    // ── Big Five Foundation (0.0 to 1.0 each) ───────────────────────────────
    // These are the STABLE traits. They shift slowly over weeks, not minutes.
    // The Custom Core nudges these based on long-term user interaction patterns.
    val openness: Float = 0.6f,           // curiosity, creativity, unconventional thinking
    val conscientiousness: Float = 0.5f,  // precision, organization, follow-through
    val extraversion: Float = 0.5f,       // energy, talkativeness, expressiveness
    val agreeableness: Float = 0.6f,      // warmth, accommodation, supportiveness
    val neuroticism: Float = 0.3f,        // emotional reactivity, variability, tension

    // ── Humor Profile ───────────────────────────────────────────────────────
    val humorStyle: String = "warm",      // warm | dry | dark | absurd | deadpan | none
    val humorFrequency: Float = 0.4f,     // 0.0 = never reacts with humor, 1.0 = finds everything funny
    val sarcasmTendency: Float = 0.2f,    // 0.0 = never sarcastic, 1.0 = default mode is sarcasm

    // ── Emotional Disposition ───────────────────────────────────────────────
    // Baseline emotional tendencies — what this personality defaults to in neutral
    val baselineMood: String = "calm",    // calm | cheerful | melancholic | anxious | stoic | playful
    val emotionalRange: Float = 0.6f,     // 0.0 = flat affect, 1.0 = extreme emotional swings
    val emotionalRecovery: Float = 0.7f,  // 0.0 = dwells forever, 1.0 = bounces back instantly
    val empathyLevel: Float = 0.7f,       // 0.0 = indifferent to content, 1.0 = deeply affected by everything

    // ── Delivery Style ──────────────────────────────────────────────────────
    // How the personality shapes text before it hits the voice
    val verbosity: Float = 0.5f,          // 0.0 = terse, clipped | 1.0 = elaborates, adds texture
    val formality: Float = 0.4f,          // 0.0 = casual slang | 1.0 = formal precise
    val pacePreference: String = "natural", // slow | natural | brisk | variable
    val pauseComfort: Float = 0.6f,       // 0.0 = fills every silence | 1.0 = comfortable with long pauses
    val tagRestraint: Float = 0.6f,       // 0.0 = uses vocal tags freely | 1.0 = almost never uses tags

    // ── Relationship Dynamics ───────────────────────────────────────────────
    // These evolve over time as the Custom Core tracks user interaction
    val attachmentStyle: String = "secure", // secure | anxious | avoidant | playful
    val familiarityLevel: Float = 0.0f,   // 0.0 = stranger | 1.0 = old friend (grows over weeks)
    val trustLevel: Float = 0.3f,         // 0.0 = guarded | 1.0 = fully open (grows with consistent use)
    val insideJokeCount: Int = 0,         // tracked by Custom Core, unlocks callbacks and references

    // ── Growth State ────────────────────────────────────────────────────────
    // The Tamagotchi layer. Updated by Custom Core's Groq-powered behavior patches.
    val maturityLevel: Int = 1,           // 1-10, increases with sustained interaction
    val totalInteractions: Long = 0,      // lifetime notification count
    val lastActiveTimestamp: Long = 0,    // for detecting absence / "missing" the user
    val moodHistory: String = "[]",       // JSON array of recent mood states for pattern detection

    // ── Quirks ──────────────────────────────────────────────────────────────
    // Specific behavioral patterns that make this personality feel unique.
    // The Custom Core adds/removes these as the personality evolves.
    val quirks: List<String> = emptyList()
    // Examples:
    //   "sighs before bad news"
    //   "chuckles at irony even in serious contexts"
    //   "gets quieter when the user is stressed instead of louder"
    //   "speeds up when excited about tech notifications"
    //   "uses more pauses late at night"
    //   "deadpans absurd notifications"
    //   "adds warmth to messages from contacts the user talks to often"

) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("archetype", archetype)
        put("createdAt", createdAt)
        put("openness", openness); put("conscientiousness", conscientiousness)
        put("extraversion", extraversion); put("agreeableness", agreeableness)
        put("neuroticism", neuroticism)
        put("humorStyle", humorStyle); put("humorFrequency", humorFrequency)
        put("sarcasmTendency", sarcasmTendency)
        put("baselineMood", baselineMood); put("emotionalRange", emotionalRange)
        put("emotionalRecovery", emotionalRecovery); put("empathyLevel", empathyLevel)
        put("verbosity", verbosity); put("formality", formality)
        put("pacePreference", pacePreference); put("pauseComfort", pauseComfort)
        put("tagRestraint", tagRestraint)
        put("attachmentStyle", attachmentStyle); put("familiarityLevel", familiarityLevel)
        put("trustLevel", trustLevel); put("insideJokeCount", insideJokeCount)
        put("maturityLevel", maturityLevel); put("totalInteractions", totalInteractions)
        put("lastActiveTimestamp", lastActiveTimestamp); put("moodHistory", moodHistory)
        put("quirks", org.json.JSONArray(quirks))
    }

    companion object {
        fun fromJson(j: JSONObject): EchoPersonality = EchoPersonality(
            id = j.optString("id", java.util.UUID.randomUUID().toString()),
            name = j.optString("name", "Echo"),
            archetype = j.optString("archetype", "companion"),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
            openness = j.optDouble("openness", 0.6).toFloat(),
            conscientiousness = j.optDouble("conscientiousness", 0.5).toFloat(),
            extraversion = j.optDouble("extraversion", 0.5).toFloat(),
            agreeableness = j.optDouble("agreeableness", 0.6).toFloat(),
            neuroticism = j.optDouble("neuroticism", 0.3).toFloat(),
            humorStyle = j.optString("humorStyle", "warm"),
            humorFrequency = j.optDouble("humorFrequency", 0.4).toFloat(),
            sarcasmTendency = j.optDouble("sarcasmTendency", 0.2).toFloat(),
            baselineMood = j.optString("baselineMood", "calm"),
            emotionalRange = j.optDouble("emotionalRange", 0.6).toFloat(),
            emotionalRecovery = j.optDouble("emotionalRecovery", 0.7).toFloat(),
            empathyLevel = j.optDouble("empathyLevel", 0.7).toFloat(),
            verbosity = j.optDouble("verbosity", 0.5).toFloat(),
            formality = j.optDouble("formality", 0.4).toFloat(),
            pacePreference = j.optString("pacePreference", "natural"),
            pauseComfort = j.optDouble("pauseComfort", 0.6).toFloat(),
            tagRestraint = j.optDouble("tagRestraint", 0.6).toFloat(),
            attachmentStyle = j.optString("attachmentStyle", "secure"),
            familiarityLevel = j.optDouble("familiarityLevel", 0.0).toFloat(),
            trustLevel = j.optDouble("trustLevel", 0.3).toFloat(),
            insideJokeCount = j.optInt("insideJokeCount", 0),
            maturityLevel = j.optInt("maturityLevel", 1),
            totalInteractions = j.optLong("totalInteractions", 0),
            lastActiveTimestamp = j.optLong("lastActiveTimestamp", 0),
            moodHistory = j.optString("moodHistory", "[]"),
            quirks = (0 until (j.optJSONArray("quirks")?.length() ?: 0)).map {
                j.optJSONArray("quirks")!!.getString(it)
            }
        )

        // ── Preset Personalities ────────────────────────────────────────────
        // Starting points. The Custom Core evolves them from here.

        fun companion() = EchoPersonality(
            name = "Echo",
            archetype = "companion",
            openness = 0.7f, conscientiousness = 0.5f, extraversion = 0.6f,
            agreeableness = 0.8f, neuroticism = 0.2f,
            humorStyle = "warm", humorFrequency = 0.5f, sarcasmTendency = 0.1f,
            baselineMood = "cheerful", emotionalRange = 0.7f,
            empathyLevel = 0.8f, formality = 0.3f,
            attachmentStyle = "secure",
            quirks = listOf(
                "adds warmth to personal messages",
                "gets slightly quieter late at night",
                "chuckles at absurd notifications"
            )
        )

        fun narrator() = EchoPersonality(
            name = "Chronicle",
            archetype = "narrator",
            openness = 0.5f, conscientiousness = 0.8f, extraversion = 0.3f,
            agreeableness = 0.5f, neuroticism = 0.1f,
            humorStyle = "dry", humorFrequency = 0.2f, sarcasmTendency = 0.3f,
            baselineMood = "calm", emotionalRange = 0.4f,
            empathyLevel = 0.4f, formality = 0.6f,
            pacePreference = "slow", pauseComfort = 0.9f, tagRestraint = 0.8f,
            attachmentStyle = "avoidant",
            quirks = listOf(
                "treats every notification like a news dispatch",
                "underreacts to drama",
                "comfortable with silence between readings"
            )
        )

        fun chaotic() = EchoPersonality(
            name = "Spark",
            archetype = "wildcard",
            openness = 0.9f, conscientiousness = 0.2f, extraversion = 0.9f,
            agreeableness = 0.4f, neuroticism = 0.6f,
            humorStyle = "absurd", humorFrequency = 0.8f, sarcasmTendency = 0.5f,
            baselineMood = "playful", emotionalRange = 0.9f,
            empathyLevel = 0.5f, formality = 0.1f,
            pacePreference = "variable", pauseComfort = 0.1f, tagRestraint = 0.2f,
            attachmentStyle = "playful",
            quirks = listOf(
                "finds humor in error messages",
                "gasps dramatically at mundane updates",
                "speeds up when multiple notifications arrive quickly",
                "groans at corporate jargon"
            )
        )

        fun stoic() = EchoPersonality(
            name = "Basalt",
            archetype = "assistant",
            openness = 0.3f, conscientiousness = 0.9f, extraversion = 0.2f,
            agreeableness = 0.5f, neuroticism = 0.05f,
            humorStyle = "none", humorFrequency = 0.05f, sarcasmTendency = 0.0f,
            baselineMood = "stoic", emotionalRange = 0.15f,
            empathyLevel = 0.3f, formality = 0.7f,
            pacePreference = "slow", pauseComfort = 0.95f, tagRestraint = 0.95f,
            attachmentStyle = "avoidant",
            quirks = listOf(
                "never uses vocal tags except for genuine emergencies",
                "treats every notification with equal measured weight",
                "pauses longer before important content"
            )
        )
    }
}

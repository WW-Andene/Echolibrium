package com.kokoro.reader

import org.json.JSONArray
import org.json.JSONObject

// ── Voice classification from Kokoro naming convention ───────────────────────
data class VoiceInfo(
    val name: String,
    val gender: String,     // Female | Male | Unknown
    val nationality: String // American | British | Unknown
) {
    val displayName: String get() = when {
        nationality != "Unknown" && gender != "Unknown" -> "$name ($nationality $gender)"
        gender != "Unknown" -> "$name ($gender)"
        else -> name
    }

    companion object {
        fun from(voiceName: String): VoiceInfo {
            val n = voiceName.lowercase()
            val gender = when {
                n.startsWith("af_") || n.startsWith("bf_") -> "Female"
                n.startsWith("am_") || n.startsWith("bm_") -> "Male"
                else -> "Unknown"
            }
            val nationality = when {
                n.startsWith("af_") || n.startsWith("am_") -> "American"
                n.startsWith("bf_") || n.startsWith("bm_") -> "British"
                else -> "Unknown"
            }
            return VoiceInfo(voiceName, gender, nationality)
        }
    }
}

// ── Gimmick model ─────────────────────────────────────────────────────────────
data class GimmickConfig(
    val type: String,
    val frequency: Int,
    val position: String = "RANDOM"
) {
    fun toJson() = JSONObject().apply {
        put("type", type); put("frequency", frequency); put("position", position)
    }

    fun toTransform() = VoiceTransform.Gimmick(
        type, frequency,
        VoiceTransform.GimmickPosition.valueOf(position)
    )

    companion object {
        fun fromJson(j: JSONObject) = GimmickConfig(
            j.optString("type", "sigh"),
            j.optInt("frequency", 0),
            j.optString("position", "RANDOM")
        )
    }
}

// ── Voice Profile ─────────────────────────────────────────────────────────────
data class VoiceProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Profile",
    val emoji: String = "🎙️",

    // Base TTS
    val voiceName: String = "",
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,

    // Breathiness
    val breathIntensity: Int = 0,
    val breathCurvePosition: Float = 0f,
    val breathPause: Int = 0,

    // Stuttering
    val stutterIntensity: Int = 0,
    val stutterPosition: Float = 0f,
    val stutterFrequency: Int = 0,
    val stutterPause: Int = 30,

    // Intonation
    val intonationIntensity: Int = 0,
    val intonationVariation: Float = 0.5f,

    // Gimmicks
    val gimmicks: List<GimmickConfig> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("emoji", emoji)
        put("voiceName", voiceName); put("pitch", pitch); put("speed", speed)
        put("breathIntensity", breathIntensity)
        put("breathCurvePosition", breathCurvePosition)
        put("breathPause", breathPause)
        put("stutterIntensity", stutterIntensity)
        put("stutterPosition", stutterPosition)
        put("stutterFrequency", stutterFrequency)
        put("stutterPause", stutterPause)
        put("intonationIntensity", intonationIntensity)
        put("intonationVariation", intonationVariation)
        val ga = JSONArray(); gimmicks.forEach { ga.put(it.toJson()) }; put("gimmicks", ga)
    }

    companion object {
        fun fromJson(j: JSONObject) = VoiceProfile(
            id = j.optString("id", java.util.UUID.randomUUID().toString()),
            name = j.optString("name", "Profile"),
            emoji = j.optString("emoji", "🎙️"),
            voiceName = j.optString("voiceName", ""),
            pitch = j.optDouble("pitch", 1.0).toFloat(),
            speed = j.optDouble("speed", 1.0).toFloat(),
            breathIntensity = j.optInt("breathIntensity", 0),
            breathCurvePosition = j.optDouble("breathCurvePosition", 0.0).toFloat(),
            breathPause = j.optInt("breathPause", 0),
            stutterIntensity = j.optInt("stutterIntensity", 0),
            stutterPosition = j.optDouble("stutterPosition", 0.0).toFloat(),
            stutterFrequency = j.optInt("stutterFrequency", 0),
            stutterPause = j.optInt("stutterPause", 30),
            intonationIntensity = j.optInt("intonationIntensity", 0),
            intonationVariation = j.optDouble("intonationVariation", 0.5).toFloat(),
            gimmicks = j.optJSONArray("gimmicks")?.let { arr ->
                (0 until arr.length()).map { GimmickConfig.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList()
        )

        // ── Personality presets ───────────────────────────────────────────────
        val PRESETS = listOf(
            VoiceProfile(name = "Natural",   emoji = "😐",
                pitch = 1.0f, speed = 1.0f),

            VoiceProfile(name = "Excited",   emoji = "🎉",
                pitch = 1.45f, speed = 1.4f,
                intonationIntensity = 70, intonationVariation = 0.85f,
                stutterIntensity = 20, stutterFrequency = 15, stutterPosition = 0.0f,
                gimmicks = listOf(
                    GimmickConfig("woah", 40, "START"),
                    GimmickConfig("laugh", 30, "END")
                )),

            VoiceProfile(name = "Bored",     emoji = "😒",
                pitch = 0.85f, speed = 0.75f,
                intonationIntensity = 10, intonationVariation = 0.1f,
                gimmicks = listOf(
                    GimmickConfig("yawn", 50, "START"),
                    GimmickConfig("hmm", 35, "END")
                )),

            VoiceProfile(name = "Depressed", emoji = "😔",
                pitch = 0.72f, speed = 0.65f,
                breathIntensity = 30, breathCurvePosition = 1.0f, breathPause = 20,
                intonationIntensity = 5, intonationVariation = 0.05f,
                gimmicks = listOf(
                    GimmickConfig("sigh", 70, "START"),
                    GimmickConfig("hmm", 30, "END")
                )),

            VoiceProfile(name = "Flirty",    emoji = "😏",
                pitch = 1.25f, speed = 0.88f,
                breathIntensity = 35, breathCurvePosition = 0.5f, breathPause = 30,
                intonationIntensity = 55, intonationVariation = 0.7f,
                gimmicks = listOf(
                    GimmickConfig("giggle", 45, "END"),
                    GimmickConfig("sigh", 25, "MID"),
                    GimmickConfig("mmm", 30, "START")
                )),

            VoiceProfile(name = "Gentle",    emoji = "🌸",
                pitch = 1.12f, speed = 0.82f,
                breathIntensity = 25, breathCurvePosition = 0.4f, breathPause = 40,
                intonationIntensity = 30, intonationVariation = 0.4f,
                gimmicks = listOf(
                    GimmickConfig("mmm", 25, "START"),
                    GimmickConfig("aww", 20, "END")
                )),

            VoiceProfile(name = "Happy",     emoji = "😄",
                pitch = 1.35f, speed = 1.15f,
                intonationIntensity = 60, intonationVariation = 0.75f,
                gimmicks = listOf(
                    GimmickConfig("laugh", 35, "END"),
                    GimmickConfig("woah", 20, "START"),
                    GimmickConfig("aww", 20, "RANDOM")
                )),

            VoiceProfile(name = "Hangry",    emoji = "😤",
                pitch = 1.05f, speed = 1.25f,
                intonationIntensity = 65, intonationVariation = 0.6f,
                gimmicks = listOf(
                    GimmickConfig("ugh", 55, "START"),
                    GimmickConfig("tsk", 40, "END"),
                    GimmickConfig("huh", 35, "MID")
                )),

            VoiceProfile(name = "Nervous",   emoji = "😰",
                pitch = 1.2f, speed = 1.1f,
                stutterIntensity = 45, stutterFrequency = 40, stutterPosition = 0.0f, stutterPause = 60,
                gimmicks = listOf(
                    GimmickConfig("huh", 30, "MID"),
                    GimmickConfig("hmm", 25, "RANDOM")
                )),

            VoiceProfile(name = "Whispery",  emoji = "🤫",
                pitch = 1.1f, speed = 0.72f,
                breathIntensity = 65, breathCurvePosition = 0.55f, breathPause = 50,
                gimmicks = listOf(
                    GimmickConfig("sigh", 20, "START")
                )),

            VoiceProfile(name = "Robot",     emoji = "🤖",
                pitch = 0.5f, speed = 0.78f,
                intonationIntensity = 0),

            VoiceProfile(name = "Drunk",     emoji = "🥴",
                pitch = 0.88f, speed = 0.82f,
                stutterIntensity = 35, stutterFrequency = 45, stutterPosition = 0.4f,
                intonationIntensity = 55, intonationVariation = 0.95f,
                gimmicks = listOf(
                    GimmickConfig("huh", 40, "RANDOM"),
                    GimmickConfig("hmm", 30, "MID"),
                    GimmickConfig("laugh", 25, "END")
                )),

            VoiceProfile(name = "Elder",     emoji = "🧓",
                pitch = 0.78f, speed = 0.7f,
                breathIntensity = 22, breathCurvePosition = 1.0f, breathPause = 15,
                gimmicks = listOf(
                    GimmickConfig("hmm", 40, "START"),
                    GimmickConfig("yawn", 20, "END")
                )),

            VoiceProfile(name = "Child",     emoji = "🧒",
                pitch = 1.85f, speed = 1.2f,
                intonationIntensity = 45, intonationVariation = 0.7f,
                gimmicks = listOf(
                    GimmickConfig("woah", 35, "START"),
                    GimmickConfig("giggle", 40, "END")
                )),

            VoiceProfile(name = "Dramatic",  emoji = "🎭",
                pitch = 1.0f, speed = 0.82f,
                intonationIntensity = 90, intonationVariation = 0.95f,
                gimmicks = listOf(
                    GimmickConfig("gasp", 50, "START"),
                    GimmickConfig("sigh", 35, "END")
                )),

            VoiceProfile(name = "Sarcastic", emoji = "🙄",
                pitch = 1.15f, speed = 0.9f,
                intonationIntensity = 70, intonationVariation = 0.8f,
                gimmicks = listOf(
                    GimmickConfig("hmm", 50, "START"),
                    GimmickConfig("tsk", 40, "END"),
                    GimmickConfig("huh", 35, "MID")
                )),
        )

        fun saveAll(profiles: List<VoiceProfile>, prefs: android.content.SharedPreferences) {
            val arr = JSONArray(); profiles.forEach { arr.put(it.toJson()) }
            prefs.edit().putString("voice_profiles", arr.toString()).apply()
        }

        fun loadAll(prefs: android.content.SharedPreferences): MutableList<VoiceProfile> {
            val json = prefs.getString("voice_profiles", null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.toMutableList()
            } catch (e: Exception) { mutableListOf() }
        }
    }
}

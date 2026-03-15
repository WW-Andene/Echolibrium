package com.echolibrium.kyokan

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Voice profile — named container with voice selection, pitch, and speed.
 *
 * Backward-compatible: reads old JSON profiles and ignores removed fields.
 * Schema version (_v) tracks format changes for future migration paths.
 */
data class VoiceProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Profile",
    val emoji: String = "🎙️",
    val voiceName: String = "",
    val pitch: Float = 1.0f,  // Valid range: 0.50–2.00 (SeekBar maps 0–150 → +50 / 100)
    val speed: Float = 1.0f   // Valid range: 0.50–3.00 (SeekBar maps 0–250 → +50 / 100)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("_v", SCHEMA_VERSION)
        put("id", id); put("name", name); put("emoji", emoji)
        put("voiceName", voiceName); put("pitch", pitch); put("speed", speed)
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val MIN_PITCH = 0.5f
        private const val MAX_PITCH = 2.0f
        private const val MIN_SPEED = 0.5f
        private const val MAX_SPEED = 3.0f

        fun fromJson(j: JSONObject): VoiceProfile = VoiceProfile(
            id = j.optString("id", java.util.UUID.randomUUID().toString()),
            name = j.optString("name", "Profile"),
            emoji = j.optString("emoji", "🎙️"),
            voiceName = j.optString("voiceName", ""),
            pitch = j.optDouble("pitch", 1.0).toFloat().coerceIn(MIN_PITCH, MAX_PITCH),
            speed = j.optDouble("speed", 1.0).toFloat().coerceIn(MIN_SPEED, MAX_SPEED)
        )

        fun saveAll(profiles: List<VoiceProfile>, prefs: android.content.SharedPreferences) {
            val arr = JSONArray(); profiles.forEach { arr.put(it.toJson()) }
            prefs.edit().putString("voice_profiles", arr.toString()).commit()
        }

        fun loadAll(prefs: android.content.SharedPreferences): MutableList<VoiceProfile> {
            val json = prefs.getString("voice_profiles", null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.toMutableList()
            } catch (e: Exception) {
                Log.e("VoiceProfile", "Failed to parse voice_profiles JSON, returning empty list. Data may be corrupted.", e)
                mutableListOf()
            }
        }
    }
}

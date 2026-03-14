package com.echolibrium.kyokan

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Voice profile — named container with voice selection, pitch, and speed.
 *
 * Backward-compatible: reads old JSON profiles and ignores removed fields.
 */
data class VoiceProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Profile",
    val emoji: String = "🎙️",
    val voiceName: String = "",
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("emoji", emoji)
        put("voiceName", voiceName); put("pitch", pitch); put("speed", speed)
    }

    companion object {
        fun fromJson(j: JSONObject): VoiceProfile = VoiceProfile(
            id = j.optString("id", java.util.UUID.randomUUID().toString()),
            name = j.optString("name", "Profile"),
            emoji = j.optString("emoji", "🎙️"),
            voiceName = j.optString("voiceName", ""),
            pitch = j.optDouble("pitch", 1.0).toFloat(),
            speed = j.optDouble("speed", 1.0).toFloat()
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
            } catch (e: Exception) {
                Log.e("VoiceProfile", "Failed to parse voice_profiles JSON, returning empty list. Data may be corrupted.", e)
                mutableListOf()
            }
        }
    }
}

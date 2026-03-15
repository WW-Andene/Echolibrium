package com.echolibrium.kyokan

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

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
        put("voiceName", voiceName)
        put("pitch", pitch.toDouble()); put("speed", speed.toDouble())
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
            pitch = j.optDouble("pitch", 1.0).roundToFloat().coerceIn(MIN_PITCH, MAX_PITCH),
            speed = j.optDouble("speed", 1.0).roundToFloat().coerceIn(MIN_SPEED, MAX_SPEED)
        )

        /** Round Double to 2 decimal places then convert to Float — avoids precision loss (L3). */
        private fun Double.roundToFloat(): Float = ((this * 100).roundToInt() / 100f)

        fun saveAll(profiles: List<VoiceProfile>, prefs: android.content.SharedPreferences) {
            val arr = JSONArray(); profiles.forEach { arr.put(it.toJson()) }
            val json = arr.toString()
            if (json.length > PREFS_SIZE_WARN_BYTES) {
                Log.w("VoiceProfile", "voice_profiles JSON is ${json.length} bytes — consider pruning old profiles")
            }
            prefs.edit().putString("voice_profiles", json)
                .putInt("data_version", prefs.getInt("data_version", 0) + 1) // O-01: migration tracking
                .apply()
        }

        /** L5: Warn threshold for SharedPreferences value size (512KB). */
        private const val PREFS_SIZE_WARN_BYTES = 512 * 1024

        fun loadAll(prefs: android.content.SharedPreferences): MutableList<VoiceProfile> {
            val json = prefs.getString("voice_profiles", null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(json)
                // B-06: Per-entry try/catch — salvage valid profiles when one is corrupted
                (0 until arr.length()).mapNotNull { i ->
                    try {
                        fromJson(arr.getJSONObject(i))
                    } catch (e: Exception) {
                        Log.w("VoiceProfile", "Skipping corrupted profile at index $i", e)
                        null
                    }
                }.toMutableList()
            } catch (e: Exception) {
                Log.e("VoiceProfile", "Failed to parse voice_profiles JSON array — backing up corrupted data", e)
                prefs.edit().putString("voice_profiles_backup_corrupted", json).apply()
                mutableListOf()
            }
        }
    }
}

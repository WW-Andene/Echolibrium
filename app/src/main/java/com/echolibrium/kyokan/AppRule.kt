package com.echolibrium.kyokan

import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/** O-01: Room entity for per-app notification rules. */
@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val readMode: String = "full",
    val profileId: String = "",
    /** C-04: Force local TTS — never send this app's notifications to cloud. */
    val forceLocal: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("_v", SCHEMA_VERSION)
        put("packageName", packageName); put("appLabel", appLabel)
        put("enabled", enabled); put("readMode", readMode); put("profileId", profileId)
        put("forceLocal", forceLocal)
    }
    companion object {
        private const val SCHEMA_VERSION = 1

        fun fromJson(j: JSONObject) = AppRule(
            j.optString("packageName"), j.optString("appLabel"),
            j.optBoolean("enabled", true), j.optString("readMode", "full"), j.optString("profileId", ""),
            j.optBoolean("forceLocal", false)
        )
        @Deprecated("Use SettingsRepository.saveAppRules() instead", ReplaceWith("repo.saveAppRules(rules)"))
        fun saveAll(rules: List<AppRule>, prefs: android.content.SharedPreferences) {
            val arr = JSONArray(); rules.forEach { arr.put(it.toJson()) }
            prefs.edit().putString("app_rules", arr.toString())
                .putInt("data_version", prefs.getInt("data_version", 0) + 1) // O-01: migration tracking
                .apply()
        }
        /** Parse a JSON array string into a list of rules (used by migration + import). */
        fun parseJsonArray(json: String): List<AppRule> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                try {
                    fromJson(arr.getJSONObject(i))
                } catch (e: Exception) {
                    Log.w("AppRule", "Skipping corrupted rule at index $i", e)
                    null
                }
            }
        }

        @Deprecated("Use SettingsRepository.getAppRules() instead", ReplaceWith("repo.getAppRules()"))
        fun loadAll(prefs: android.content.SharedPreferences): MutableList<AppRule> {
            val json = prefs.getString("app_rules", null) ?: return mutableListOf()
            return try {
                parseJsonArray(json).toMutableList()
            } catch (e: Exception) {
                Log.e("AppRule", "Failed to parse app_rules JSON array — backing up corrupted data", e)
                prefs.edit().putString("app_rules_backup_corrupted", json).apply()
                mutableListOf()
            }
        }
    }
}

package com.echolibrium.kyokan

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class AppRule(
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val readMode: String = "full",
    val profileId: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("_v", SCHEMA_VERSION)
        put("packageName", packageName); put("appLabel", appLabel)
        put("enabled", enabled); put("readMode", readMode); put("profileId", profileId)
    }
    companion object {
        private const val SCHEMA_VERSION = 1

        fun fromJson(j: JSONObject) = AppRule(
            j.optString("packageName"), j.optString("appLabel"),
            j.optBoolean("enabled", true), j.optString("readMode", "full"), j.optString("profileId", "")
        )
        fun saveAll(rules: List<AppRule>, prefs: android.content.SharedPreferences) {
            val arr = JSONArray(); rules.forEach { arr.put(it.toJson()) }
            prefs.edit().putString("app_rules", arr.toString()).apply()
        }
        fun loadAll(prefs: android.content.SharedPreferences): MutableList<AppRule> {
            val json = prefs.getString("app_rules", null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.toMutableList()
            } catch (e: Exception) {
                Log.e("AppRule", "Failed to parse app_rules JSON, returning empty list. Data may be corrupted.", e)
                mutableListOf()
            }
        }
    }
}

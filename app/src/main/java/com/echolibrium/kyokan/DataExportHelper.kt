package com.echolibrium.kyokan

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONObject

/**
 * B-10: Simple data export/import for backup and device migration.
 * Exports voice profiles, app rules, word rules, and key settings as JSON.
 */
object DataExportHelper {

    private const val EXPORT_VERSION = 1

    /** Keys to include in export — covers all user-created data. */
    private val EXPORT_KEYS = listOf(
        "voice_profiles", "app_rules", "wording_rules", "active_profile_id",
        "lang_routing_enabled", "notif_read_once", "notif_skip_swiped",
        "notif_stop_on_swipe", "notif_read_ongoing", "notif_cooldown", "notif_max_queue"
    )

    fun exportAll(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val export = JSONObject().apply {
            put("_version", EXPORT_VERSION)
            put("_exported", System.currentTimeMillis())
            for (key in EXPORT_KEYS) {
                val value = prefs.all[key]
                if (value != null) put(key, value)
            }
        }
        return export.toString(2)
    }

    fun importAll(ctx: Context, json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val version = obj.optInt("_version", 0)
            if (version < 1) return false
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val editor = prefs.edit()
            for (key in EXPORT_KEYS) {
                if (!obj.has(key)) continue
                when (val value = obj.get(key)) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
            editor.apply()
            true
        } catch (_: Exception) {
            false
        }
    }
}

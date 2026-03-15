package com.echolibrium.kyokan

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray

/**
 * I-07 / O-01: Typed repository wrapping Room (structured data) and
 * SharedPreferences (simple key-value settings).
 *
 * All data access in the app should go through this single entry point.
 * Change listeners are notified with the same key strings that
 * SharedPreferences used, so existing consumer patterns work unchanged.
 */
class SettingsRepository(
    private val db: KyokanDatabase,
    /** SharedPreferences for simple key-value settings. */
    val prefs: SharedPreferences
) {
    companion object {
        private const val TAG = "SettingsRepository"
        private const val MIGRATION_DONE_KEY = "room_migration_done"
    }

    private val profileDao = db.voiceProfileDao()
    private val appRuleDao = db.appRuleDao()
    private val wordRuleDao = db.wordRuleDao()

    private val changeListeners = mutableListOf<(String) -> Unit>()
    private val listenersLock = Any()

    /** Forward SharedPreferences changes for simple settings. */
    private val spListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) notifyChanged(key)
    }

    init {
        migrateFromSharedPreferences()
        prefs.registerOnSharedPreferenceChangeListener(spListener)
    }

    // ── Change notification ─────────────────────────────────────────────────

    fun addChangeListener(listener: (key: String) -> Unit) {
        synchronized(listenersLock) { changeListeners.add(listener) }
    }

    fun removeChangeListener(listener: (key: String) -> Unit) {
        synchronized(listenersLock) { changeListeners.remove(listener) }
    }

    private fun notifyChanged(key: String) {
        val snapshot = synchronized(listenersLock) { changeListeners.toList() }
        snapshot.forEach { it(key) }
    }

    // ── Voice Profiles (Room) ───────────────────────────────────────────────

    fun getProfiles(): List<VoiceProfile> = profileDao.getAll()

    fun saveProfile(profile: VoiceProfile) {
        profileDao.insert(profile)
        notifyChanged("voice_profiles")
    }

    fun saveProfiles(profiles: List<VoiceProfile>) {
        db.runInTransaction {
            profileDao.deleteAll()
            profileDao.insertAll(profiles)
        }
        notifyChanged("voice_profiles")
    }

    fun deleteProfile(id: String) {
        profileDao.deleteById(id)
        notifyChanged("voice_profiles")
    }

    // ── App Rules (Room) ────────────────────────────────────────────────────

    fun getAppRules(): List<AppRule> = appRuleDao.getAll()

    fun saveAppRule(rule: AppRule) {
        appRuleDao.insert(rule)
        notifyChanged("app_rules")
    }

    fun saveAppRules(rules: List<AppRule>) {
        db.runInTransaction {
            appRuleDao.deleteAll()
            appRuleDao.insertAll(rules)
        }
        notifyChanged("app_rules")
    }

    // ── Word Rules (Room) ───────────────────────────────────────────────────

    fun getWordRules(): List<WordRule> = wordRuleDao.getAll()

    fun saveWordRules(rules: List<WordRule>) {
        db.runInTransaction {
            wordRuleDao.deleteAll()
            wordRuleDao.insertAll(rules)
        }
        notifyChanged("wording_rules")
    }

    // ── Simple Settings (SharedPreferences) ─────────────────────────────────

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun putString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) =
        prefs.edit().putBoolean(key, value).apply()

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun putInt(key: String, value: Int) =
        prefs.edit().putInt(key, value).apply()

    /** Convenience: active profile ID stored in SharedPreferences. */
    var activeProfileId: String
        get() = getString("active_profile_id")
        set(value) = putString("active_profile_id", value)

    // ── Export / Import (B-10) ──────────────────────────────────────────────

    /** Export keys that live in SharedPreferences. */
    private val spExportKeys = listOf(
        "active_profile_id", "lang_routing_enabled",
        "notif_read_once", "notif_skip_swiped", "notif_stop_on_swipe",
        "notif_read_ongoing", "notif_cooldown", "notif_max_queue"
    )

    fun exportAll(): org.json.JSONObject {
        val obj = org.json.JSONObject()
        obj.put("_version", 1)
        obj.put("_exported", System.currentTimeMillis())

        // Structured data from Room
        val profilesArr = JSONArray()
        getProfiles().forEach { profilesArr.put(it.toJson()) }
        obj.put("voice_profiles", profilesArr.toString())

        val rulesArr = JSONArray()
        getAppRules().forEach { rulesArr.put(it.toJson()) }
        obj.put("app_rules", rulesArr.toString())

        val wordArr = JSONArray()
        getWordRules().forEach { wordArr.put(org.json.JSONObject().apply { put("find", it.find); put("replace", it.replace) }) }
        obj.put("wording_rules", wordArr.toString())

        // Simple settings from SharedPreferences
        for (key in spExportKeys) {
            val value = prefs.all[key]
            if (value != null) obj.put(key, value)
        }
        return obj
    }

    fun importAll(json: org.json.JSONObject): Boolean {
        return try {
            val version = json.optInt("_version", 0)
            if (version < 1) return false

            // Import structured data — all-or-nothing within a transaction
            db.runInTransaction {
                json.optString("voice_profiles", "").takeIf { it.isNotBlank() }?.let { raw ->
                    val profiles = VoiceProfile.parseJsonArray(raw)
                    if (profiles.isNotEmpty()) {
                        profileDao.deleteAll()
                        profileDao.insertAll(profiles)
                    }
                }
                json.optString("app_rules", "").takeIf { it.isNotBlank() }?.let { raw ->
                    val rules = AppRule.parseJsonArray(raw)
                    if (rules.isNotEmpty()) {
                        appRuleDao.deleteAll()
                        appRuleDao.insertAll(rules)
                    }
                }
                json.optString("wording_rules", "").takeIf { it.isNotBlank() }?.let { raw ->
                    val arr = JSONArray(raw)
                    val rules = (0 until arr.length()).mapNotNull { i ->
                        try {
                            val obj = arr.getJSONObject(i)
                            WordRule(find = obj.optString("find", ""), replace = obj.optString("replace", ""))
                        } catch (_: Exception) { null }
                    }
                    if (rules.isNotEmpty()) {
                        wordRuleDao.deleteAll()
                        wordRuleDao.insertAll(rules)
                    }
                }
            }
            notifyChanged("voice_profiles")
            notifyChanged("app_rules")
            notifyChanged("wording_rules")

            // Import simple settings
            val editor = prefs.edit()
            for (key in spExportKeys) {
                if (!json.has(key)) continue
                when (val value = json.get(key)) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            false
        }
    }

    // ── Migration (SP → Room, one-time) ─────────────────────────────────────

    private fun migrateFromSharedPreferences() {
        if (prefs.getBoolean(MIGRATION_DONE_KEY, false)) return
        Log.i(TAG, "Migrating structured data from SharedPreferences to Room…")

        var migrated = false

        db.runInTransaction {
            // Migrate voice profiles
            prefs.getString("voice_profiles", null)?.let { json ->
                try {
                    val profiles = VoiceProfile.parseJsonArray(json)
                    if (profiles.isNotEmpty() && profileDao.count() == 0) {
                        profileDao.insertAll(profiles)
                        Log.i(TAG, "Migrated ${profiles.size} voice profiles to Room")
                        migrated = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate voice profiles", e)
                }
                Unit
            }

            // Migrate app rules
            prefs.getString("app_rules", null)?.let { json ->
                try {
                    val rules = AppRule.parseJsonArray(json)
                    if (rules.isNotEmpty() && appRuleDao.count() == 0) {
                        appRuleDao.insertAll(rules)
                        Log.i(TAG, "Migrated ${rules.size} app rules to Room")
                        migrated = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate app rules", e)
                }
                Unit
            }

            // Migrate word rules
            prefs.getString("wording_rules", null)?.let { json ->
                try {
                    val arr = JSONArray(json)
                    val rules = (0 until arr.length()).mapNotNull { i ->
                        try {
                            val obj = arr.getJSONObject(i)
                            val find = obj.optString("find", "")
                            val replace = obj.optString("replace", "")
                            if (find.isNotBlank()) WordRule(find = find, replace = replace) else null
                        } catch (_: Exception) { null }
                    }
                    if (rules.isNotEmpty() && wordRuleDao.count() == 0) {
                        wordRuleDao.insertAll(rules)
                        Log.i(TAG, "Migrated ${rules.size} word rules to Room")
                        migrated = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate word rules", e)
                }
                Unit
            }
        }

        // Mark migration done — old SP keys kept for backup safety
        prefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
        if (migrated) {
            Log.i(TAG, "SharedPreferences → Room migration complete")
        } else {
            Log.i(TAG, "No data to migrate (fresh install or already migrated)")
        }
    }
}

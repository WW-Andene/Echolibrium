package com.kokoro.reader

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.preference.PreferenceManager
import org.json.JSONArray

class NotificationReaderService : NotificationListenerService() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private var dailyCount = 0
    private var lastCountDay = -1

    // Deduplication: track notification keys we already read
    private val readKeys = LinkedHashMap<String, Long>(128, 0.75f, true)
    // Track swiped notification keys so queued items can be skipped
    private val swipedKeys = mutableSetOf<String>()
    // Per-app cooldown: package → last read timestamp
    private val appLastRead = mutableMapOf<String, Long>()

    companion object {
        var instance: NotificationReaderService? = null
        private const val KEY_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes

        /** Last spoken notification text — used by VoiceCommandHandler repeat */
        @Volatile var lastSpokenText: String = ""
        /** Timestamp of last notification — used by VoiceCommandHandler time-ago */
        @Volatile var lastNotificationTime: Long = 0L
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AudioPipeline.start(this)
        TtsAliveService.start(this)
    }

    override fun onDestroy() {
        instance = null
        AudioPipeline.shutdown()
        TtsAliveService.stop(this)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("service_enabled", true)) return
        if (sbn.packageName == packageName) return
        if (isDndActive()) return

        // Skip ongoing notifications unless allowed
        if (sbn.isOngoing && !prefs.getBoolean("notif_read_ongoing", false)) return

        val rules = AppRule.loadAll(prefs)
        val rule  = rules.find { it.packageName == sbn.packageName }
        if (rule?.readMode == "skip" || rule?.enabled == false) return

        val now = System.currentTimeMillis()

        // Deduplication: skip if already read
        if (prefs.getBoolean("notif_read_once", true)) {
            val key = sbn.key
            synchronized(readKeys) {
                if (readKeys.containsKey(key)) return
                readKeys[key] = now
                cleanupOldKeys(now)
            }
        }

        // Per-app cooldown
        val cooldownMs = prefs.getInt("notif_cooldown", 3) * 1000L
        if (cooldownMs > 0) {
            synchronized(appLastRead) {
                val lastRead = appLastRead[sbn.packageName] ?: 0L
                if (now - lastRead < cooldownMs) return
                appLastRead[sbn.packageName] = now
            }
        }

        val extras  = sbn.notification?.extras ?: return
        val appName = getAppName(sbn.packageName)
        val title   = extras.getString("android.title") ?: ""
        val text    = extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (today != lastCountDay) { dailyCount = 0; lastCountDay = today }
        dailyCount++

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val signal = SignalExtractor.extract(
            packageName = sbn.packageName,
            appName     = appName,
            title       = title,
            text        = text,
            hourOfDay   = hour,
            floodCount  = dailyCount
        )

        val profiles  = VoiceProfile.loadAll(prefs)
        val profileId = rule?.profileId?.takeIf { it.isNotEmpty() }
            ?: resolveProfileByLanguage(title, text, profiles)
            ?: prefs.getString("active_profile_id", "")
        val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
        val modulated = VoiceModulator.modulate(profile, signal)

        val readMode = rule?.readMode ?: prefs.getString("read_mode", "full") ?: "full"
        val rawText = buildMessage(appName, title, text, readMode)
        if (rawText.isBlank()) return

        // Track for voice commands (repeat, time-ago)
        lastSpokenText = rawText
        lastNotificationTime = now

        // Update mood state
        val mood = MoodState.load(prefs).decayed()
        val updatedMood = MoodUpdater.update(mood, signal)
        MoodState.save(prefs, updatedMood)

        // Check max queue size
        val maxQueue = prefs.getInt("notif_max_queue", 10).coerceAtLeast(1)

        AudioPipeline.enqueue(AudioPipeline.Item(
            rawText   = rawText,
            profile   = profile,
            modulated = modulated,
            signal    = signal,
            rules     = loadWordingRules(),
            priority  = signal.urgencyType == UrgencyType.EXPIRING
        ), maxQueue)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (prefs.getBoolean("notif_skip_swiped", true)) {
            synchronized(swipedKeys) {
                swipedKeys.add(sbn.key)
            }
            // Also remove from read tracking so if it comes back it can be read again
            synchronized(readKeys) {
                readKeys.remove(sbn.key)
            }
        }
    }

    private fun cleanupOldKeys(now: Long) {
        val iter = readKeys.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value > KEY_EXPIRY_MS) iter.remove() else break
        }
    }

    private fun buildMessage(appName: String, title: String, text: String, mode: String) =
        when (mode) {
            "app_only"   -> appName
            "title_only" -> "$appName. $title"
            "text_only"  -> text
            else         -> buildString {
                if (prefs.getBoolean("read_app_name", true)) append("$appName. ")
                if (title.isNotBlank()) append("$title. ")
                if (text.isNotBlank()) append(text)
            }
        }

    private fun isDndActive(): Boolean {
        if (!prefs.getBoolean("dnd_enabled", false)) return false
        val start = prefs.getInt("dnd_start", 22)
        val end   = prefs.getInt("dnd_end",   8)
        val hour  = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (start > end) hour >= start || hour < end else hour in start until end
    }

    private fun getAppName(pkg: String) = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) { pkg }

    private fun loadWordingRules(): List<Pair<String, String>> {
        val json = prefs.getString("wording_rules", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Pair(o.optString("find"), o.optString("replace"))
            }
        } catch (e: Exception) { emptyList() }
    }

    /**
     * Detect language from notification text and return the mapped profile ID,
     * or null if language routing is disabled or no match.
     */
    private fun resolveProfileByLanguage(title: String, text: String, profiles: List<VoiceProfile>): String? {
        if (!prefs.getBoolean("lang_routing_enabled", false)) return null
        val combined = "$title $text"
        val lang = detectLanguage(combined)
        val prefKey = when (lang) {
            "fr" -> "lang_profile_fr"
            else -> "lang_profile_en"
        }
        val id = prefs.getString(prefKey, "") ?: ""
        return id.ifEmpty { null }
    }

    /**
     * Simple language detection: returns "fr" if the text looks French, "en" otherwise.
     * Uses French-specific accented characters and common French words.
     */
    private fun detectLanguage(text: String): String {
        val lower = text.lowercase()
        // Count French accent characters
        val frenchAccents = lower.count { it in "éèêëàâùûôîïç" }
        if (frenchAccents >= 2) return "fr"

        // Check for common French words (bounded by word boundaries)
        val frenchWords = listOf(
            "\\ble\\b", "\\bla\\b", "\\bles\\b", "\\bun\\b", "\\bune\\b", "\\bdes\\b",
            "\\bdu\\b", "\\bau\\b", "\\baux\\b", "\\bde\\b",
            "\\best\\b", "\\bsont\\b", "\\bpas\\b", "\\bque\\b", "\\bqui\\b",
            "\\bje\\b", "\\btu\\b", "\\bil\\b", "\\bnous\\b", "\\bvous\\b", "\\bils\\b",
            "\\bpour\\b", "\\bavec\\b", "\\bdans\\b", "\\bsur\\b",
            "\\bbonjour\\b", "\\bmerci\\b", "\\bsalut\\b", "\\bbonsoir\\b",
            "\\bcomment\\b", "\\bpourquoi\\b", "\\bquand\\b",
            "\\bc'est\\b", "\\bj'ai\\b", "\\bn'est\\b", "\\bqu'\\b"
        )
        val frenchHits = frenchWords.count { Regex(it).containsMatchIn(lower) }
        if (frenchHits >= 3) return "fr"

        return "en"
    }

    fun testSpeak(text: String, profile: VoiceProfile, rules: List<Pair<String, String>>) {
        val signal = SignalMap(
            sourceType     = SourceType.PERSONAL,
            senderType     = SenderType.HUMAN,
            warmth         = WarmthLevel.MEDIUM,
            register       = Register.CASUAL,
            stakesLevel    = StakesLevel.LOW,
            urgencyType    = UrgencyType.NONE,
            intensityLevel = 0.3f,
            trajectory     = Trajectory.FLAT
        )
        val modulated = VoiceModulator.modulate(profile, signal)
        AudioPipeline.enqueue(AudioPipeline.Item(
            rawText   = text,
            profile   = profile,
            modulated = modulated,
            signal    = signal,
            rules     = rules
        ))
    }

    fun stopSpeaking() = AudioPipeline.stop()
}

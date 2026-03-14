package com.echolibrium.kyokan

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.preference.PreferenceManager
import org.json.JSONArray
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

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
    // Background thread for notification processing — keeps main thread unblocked
    // so ML Kit translation (which needs main looper for Task dispatch) doesn't deadlock
    private val processingExecutor: ExecutorService = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(20),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    companion object {
        var instance: NotificationReaderService? = null
        private const val KEY_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes

        /** Last spoken notification text — used by VoiceCommandHandler repeat */
        @Volatile var lastSpokenText: String = ""
        /** Timestamp of last notification — used by VoiceCommandHandler time-ago */
        @Volatile var lastNotificationTime: Long = 0L

        // Pre-compiled regex patterns for detectLanguage() — compiled once at class load
        private val FR_CONTRACTION_PATTERNS = listOf(
            "\\bc'est\\b", "\\bj'ai\\b", "\\bn'est\\b", "\\bqu'\\b",
            "\\bl'\\w", "\\bd'\\w", "\\bs'\\w", "\\bn'\\w"
        ).map { Regex(it) }

        private val FR_WORD_PATTERNS = listOf(
            "\\bune\\b", "\\bles\\b", "\\bdes\\b", "\\baux\\b",
            "\\bsont\\b", "\\bpas\\b", "\\bque\\b", "\\bqui\\b",
            "\\bje\\b", "\\btu\\b", "\\bnous\\b", "\\bvous\\b", "\\bils\\b",
            "\\bpour\\b", "\\bavec\\b", "\\bdans\\b",
            "\\bbonjour\\b", "\\bmerci\\b", "\\bsalut\\b", "\\bbonsoir\\b",
            "\\bcomment\\b", "\\bpourquoi\\b", "\\bquand\\b",
            "\\bactivé\\b", "\\bdésactivé\\b", "\\bêtes\\b"
        ).map { Regex(it) }
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        instance = this
        AudioPipeline.start(this)
        TtsAliveService.start(this)
        CustomCoreEngine.start(this)
        // Start voice command listener if enabled and mic permission granted
        if (prefs.getBoolean("listening_enabled", false)) {
            try {
                val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasPerm) VoiceCommandListener.start(this)
            } catch (e: Exception) {
                android.util.Log.e("NotifReader", "Error starting voice commands", e)
            }
        }
    }

    override fun onDestroy() {
        instance = null
        lastSpokenText = ""
        lastNotificationTime = 0L
        VoiceCommandListener.stop()
        CustomCoreEngine.stop()
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
        val floodSnapshot = dailyCount
        val pkgName = sbn.packageName

        // Move heavy processing (translation, signal extraction, enqueue) off main thread.
        // ML Kit translation Tasks need the main looper free to dispatch completion;
        // blocking main thread with latch.await() causes deadlock and 5s timeout.
        processingExecutor.execute {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val signal = SignalExtractor.extract(
                packageName = pkgName,
                appName     = appName,
                title       = title,
                text        = text,
                hourOfDay   = hour,
                floodCount  = floodSnapshot
            )

            val profiles  = VoiceProfile.loadAll(prefs)
            val activeId = prefs.getString("active_profile_id", "") ?: ""

            val readMode = rule?.readMode ?: prefs.getString("read_mode", "full") ?: "full"
            var rawText = buildMessage(appName, title, text, readMode)
            if (rawText.isBlank()) return@execute

            // Detect content language from text, using app locale as hint for ambiguous cases
            val appLang = getAppLanguage(pkgName)
            val detectedLang = detectLanguage("$title $text", appLang)
            val translateEnabled = when (detectedLang) {
                "fr" -> prefs.getBoolean("translate_fr_enabled", false)
                else -> prefs.getBoolean("translate_en_enabled", false)
            }
            var effectiveLang = detectedLang
            var wasTranslated = false
            if (translateEnabled) {
                val targetLang = when (detectedLang) {
                    "fr" -> prefs.getString("translate_fr_lang", "") ?: ""
                    else -> prefs.getString("translate_en_lang", "") ?: ""
                }
                if (targetLang.isNotBlank() && targetLang != detectedLang) {
                    val translated = NotificationTranslator.translate(rawText, detectedLang, targetLang)
                    if (translated != rawText) {
                        rawText = translated
                        wasTranslated = true
                    }
                    effectiveLang = targetLang
                }
            }

            // Pick voice profile: per-app rule > language route (using output language) > active
            val profileId = rule?.profileId?.takeIf { it.isNotEmpty() }
                ?: resolveProfileByLanguage(effectiveLang, profiles)
                ?: activeId
            val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
            val modulated = VoiceModulator.modulate(profile, signal)

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
                rawText    = rawText,
                profile    = profile,
                modulated  = modulated,
                signal     = signal,
                rules      = loadWordingRules(),
                priority   = signal.urgencyType == UrgencyType.EXPIRING,
                translated = wasTranslated
            ), maxQueue)
        }
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

        // Stop reading immediately when notification is swiped/cleared
        if (prefs.getBoolean("notif_stop_on_swipe", false)) {
            AudioPipeline.stop()
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
        if (start == end) return true  // same hour = 24h DND
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
     * Return the mapped voice profile ID for the given language,
     * or null if language routing is disabled or no match.
     */
    private fun resolveProfileByLanguage(lang: String, profiles: List<VoiceProfile>): String? {
        if (!prefs.getBoolean("lang_routing_enabled", false)) return null
        val prefKey = when (lang) {
            "fr" -> "lang_profile_fr"
            else -> "lang_profile_en"
        }
        val id = prefs.getString(prefKey, "") ?: ""
        return id.ifEmpty { null }
    }

    /**
     * Get the UI language the notification-sending app is running in.
     * NOTE: This is the app's UI language, not necessarily the content language.
     * A French Gmail can show an English email. Used as a hint, not as truth.
     */
    private fun getAppLanguage(packageName: String): String? {
        return try {
            val appCtx = createPackageContext(packageName, 0)
            val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                appCtx.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                appCtx.resources.configuration.locale
            }
            locale.language.take(2).lowercase().ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Detect the language of the notification content.
     *
     * Text analysis is primary (it sees the actual words). App locale is a hint
     * for ambiguous cases — e.g. a French app with short text like "OK" or "Photo"
     * that has no clear language markers.
     *
     * @param text The notification text to analyze
     * @param appLangHint The sending app's UI locale (may differ from content language)
     */
    private fun detectLanguage(text: String, appLangHint: String? = null): String {
        val lower = text.lowercase()

        // ── Strong signals: these override everything ──

        // French accents are definitive — English text doesn't have éèêëàâùûôîïç
        val frenchAccents = lower.count { it in "éèêëàâùûôîïç" }
        if (frenchAccents >= 2) return "fr"

        // French contractions don't exist in English (pre-compiled patterns)
        val strongHits = FR_CONTRACTION_PATTERNS.count { it.containsMatchIn(lower) }
        if (strongHits >= 1) return "fr"

        // ── Medium signals: French-only words (pre-compiled patterns) ──
        val frenchHits = FR_WORD_PATTERNS.count { it.containsMatchIn(lower) }
        if (frenchHits >= 3) return "fr"

        // ── Ambiguous zone: some French signals but not enough to be sure ──
        // Use app locale as tiebreaker: if the app is French AND there are some
        // French-looking words, it's probably French content
        if (frenchHits >= 1 && appLangHint == "fr") return "fr"

        // ── No French signals at all: default to English ──
        return "en"
    }

    fun testSpeak(text: String, profile: VoiceProfile, rules: List<Pair<String, String>>) {
        val signal = SignalMap(
            source    = SourceContext(sourceType = SourceType.PERSONAL, senderType = SenderType.HUMAN),
            emotion   = EmotionalSignals(warmth = WarmthLevel.MEDIUM),
            intensity = IntensityMetrics(
                urgencyType    = UrgencyType.NONE,
                intensityLevel = 0.3f,
                trajectory     = Trajectory.FLAT,
                register       = Register.CASUAL
            ),
            stakes    = StakesContext(stakesLevel = StakesLevel.LOW)
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

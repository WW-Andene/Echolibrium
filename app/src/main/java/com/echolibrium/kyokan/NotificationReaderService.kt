package com.echolibrium.kyokan

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.preference.PreferenceManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class NotificationReaderService : NotificationListenerService() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private var dailyCount = 0
    private var lastCountDay = -1

    private val readKeys = LinkedHashMap<String, Long>(128, 0.75f, true)
    private val swipedKeys = mutableSetOf<String>()
    private val appLastRead = mutableMapOf<String, Long>()
    private val processingExecutor: ExecutorService = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(20),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    companion object {
        var instance: NotificationReaderService? = null
        private const val KEY_EXPIRY_MS = 5 * 60 * 1000L

        @Volatile var lastSpokenText: String = ""
        @Volatile var lastNotificationTime: Long = 0L

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
        AudioPipeline.shutdown()
        TtsAliveService.stop(this)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("service_enabled", true)) return
        if (sbn.packageName == packageName) return
        if (isDndActive()) return
        if (sbn.isOngoing && !prefs.getBoolean("notif_read_ongoing", false)) return

        val rules = AppRule.loadAll(prefs)
        val rule  = rules.find { it.packageName == sbn.packageName }
        if (rule?.readMode == "skip" || rule?.enabled == false) return

        val now = System.currentTimeMillis()

        if (prefs.getBoolean("notif_read_once", true)) {
            val key = sbn.key
            synchronized(readKeys) {
                if (readKeys.containsKey(key)) return
                readKeys[key] = now
                cleanupOldKeys(now)
            }
        }

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

        val pkgName = sbn.packageName

        processingExecutor.execute {
            val profiles  = VoiceProfile.loadAll(prefs)
            val activeId = prefs.getString("active_profile_id", "") ?: ""

            val readMode = rule?.readMode ?: prefs.getString("read_mode", "full") ?: "full"
            var rawText = buildMessage(appName, title, text, readMode)
            if (rawText.isBlank()) return@execute

            // Language detection
            val appLang = getAppLanguage(pkgName)
            val detectedLang = detectLanguage("$title $text", appLang)
            val translateEnabled = when (detectedLang) {
                "fr" -> prefs.getBoolean("translate_fr_enabled", false)
                else -> prefs.getBoolean("translate_en_enabled", false)
            }
            var effectiveLang = detectedLang
            if (translateEnabled) {
                val targetLang = when (detectedLang) {
                    "fr" -> prefs.getString("translate_fr_lang", "") ?: ""
                    else -> prefs.getString("translate_en_lang", "") ?: ""
                }
                if (targetLang.isNotBlank() && targetLang != detectedLang) {
                    val translated = NotificationTranslator.translate(rawText, detectedLang, targetLang)
                    if (translated != rawText) rawText = translated
                    effectiveLang = targetLang
                }
            }

            // Pick voice profile: per-app rule > language route > active
            val profileId = rule?.profileId?.takeIf { it.isNotEmpty() }
                ?: resolveProfileByLanguage(effectiveLang, profiles)
                ?: activeId
            val profile = profiles.find { it.id == profileId } ?: VoiceProfile()

            lastSpokenText = rawText
            lastNotificationTime = now

            val maxQueue = prefs.getInt("notif_max_queue", 10).coerceAtLeast(1)

            AudioPipeline.enqueue(AudioPipeline.Item(
                text     = rawText,
                voiceId  = profile.voiceName,
                pitch    = profile.pitch,
                speed    = profile.speed,
                language = effectiveLang
            ), maxQueue)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (prefs.getBoolean("notif_skip_swiped", true)) {
            synchronized(swipedKeys) { swipedKeys.add(sbn.key) }
            synchronized(readKeys) { readKeys.remove(sbn.key) }
        }
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
        if (start == end) return true
        val hour  = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (start > end) hour >= start || hour < end else hour in start until end
    }

    private fun getAppName(pkg: String) = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) { pkg }

    private fun resolveProfileByLanguage(lang: String, profiles: List<VoiceProfile>): String? {
        if (!prefs.getBoolean("lang_routing_enabled", false)) return null
        val prefKey = when (lang) { "fr" -> "lang_profile_fr"; else -> "lang_profile_en" }
        val id = prefs.getString(prefKey, "") ?: ""
        return id.ifEmpty { null }
    }

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
        } catch (e: Exception) { null }
    }

    private fun detectLanguage(text: String, appLangHint: String? = null): String {
        val lower = text.lowercase()
        val frenchAccents = lower.count { it in "éèêëàâùûôîïç" }
        if (frenchAccents >= 2) return "fr"
        val strongHits = FR_CONTRACTION_PATTERNS.count { it.containsMatchIn(lower) }
        if (strongHits >= 1) return "fr"
        val frenchHits = FR_WORD_PATTERNS.count { it.containsMatchIn(lower) }
        if (frenchHits >= 3) return "fr"
        if (frenchHits >= 1 && appLangHint == "fr") return "fr"
        return "en"
    }

    fun testSpeak(text: String, profile: VoiceProfile) {
        AudioPipeline.enqueue(AudioPipeline.Item(
            text    = text,
            voiceId = profile.voiceName,
            pitch   = profile.pitch,
            speed   = profile.speed
        ))
    }

    fun stopSpeaking() = AudioPipeline.stop()
}

package com.echolibrium.kyokan

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Thread safety (M2): readKeys, swipedKeys, appLastRead are accessed from both the
 * binder thread (onNotificationPosted/Removed) and the single-threaded processingExecutor.
 * All accesses are protected by synchronized(readKeys/swipedKeys/appLastRead) blocks.
 * The cachedRules/cachedProfiles volatiles are read from the executor and invalidated
 * from the main thread via repository listener — safe due to @Volatile.
 *
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class NotificationReaderService : NotificationListenerService() {

    private val c by lazy { container }
    private val repo by lazy { c.repo }

    // Guarded by synchronized(readKeys) — accessed from binder + executor threads
    private val readKeys = LinkedHashMap<String, Long>(128, 0.75f, true)
    // Guarded by synchronized(swipedKeys)
    private val swipedKeys = mutableSetOf<String>()
    // Guarded by synchronized(appLastRead)
    private val appLastRead = mutableMapOf<String, Long>()

    // Cached data — invalidated via repository listener (QW6)
    @Volatile private var cachedRules: List<AppRule>? = null
    @Volatile private var cachedProfiles: List<VoiceProfile>? = null
    @Volatile private var cachedWordRules: List<Pair<String, String>>? = null

    private val repoListener: (String) -> Unit = { key ->
        when (key) {
            "app_rules" -> cachedRules = null
            "voice_profiles", "active_profile_id" -> cachedProfiles = null
            "wording_rules" -> cachedWordRules = null
        }
    }
    private val processingExecutor: ExecutorService = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(20),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    companion object {
        @Volatile var instance: NotificationReaderService? = null
        private const val KEY_EXPIRY_MS = 5 * 60 * 1000L

        private const val TAG = "NotifReader"

        @Volatile var lastSpokenText: String = ""
        @Volatile var lastNotificationTime: Long = 0L
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        instance = this
        repo.addChangeListener(repoListener)
        c.audioPipeline.start(this)
        TtsAliveService.start(this)
        if (repo.getBoolean("listening_enabled", false)) {
            try {
                val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasPerm) c.voiceCommandListener.start(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting voice commands", e)
            }
        }
    }

    override fun onDestroy() {
        repo.removeChangeListener(repoListener)
        instance = null
        lastSpokenText = ""
        lastNotificationTime = 0L
        cachedRules = null
        cachedProfiles = null
        cachedWordRules = null
        c.voiceCommandListener.stop()
        c.audioPipeline.shutdown()
        TtsAliveService.stop(this)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!repo.getBoolean("service_enabled", true)) return
        if (sbn.packageName == packageName) return
        if (isDndActive()) return
        if (sbn.isOngoing && !repo.getBoolean("notif_read_ongoing", false)) return

        val rules = cachedRules ?: repo.getAppRules().also { cachedRules = it }
        val rule  = rules.find { it.packageName == sbn.packageName }
        if (rule?.readMode == "skip" || rule?.enabled == false) return

        val now = System.currentTimeMillis()

        // Skip notifications that the user already swiped away
        if (repo.getBoolean("notif_skip_swiped", true)) {
            synchronized(swipedKeys) {
                if (swipedKeys.remove(sbn.key)) return
            }
        }

        if (repo.getBoolean("notif_read_once", true)) {
            val key = sbn.key
            synchronized(readKeys) {
                if (readKeys.containsKey(key)) return
                readKeys[key] = now
                cleanupOldKeys(now)
            }
        }

        val cooldownMs = repo.getInt("notif_cooldown", 3) * 1000L
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
            val profiles  = cachedProfiles ?: repo.getProfiles().also { cachedProfiles = it }
            val activeId = repo.activeProfileId

            val readMode = rule?.readMode ?: repo.getString("read_mode", "full")
            val readAppName = repo.getBoolean("read_app_name", true)
            var rawText = NotificationFormatter.buildMessage(appName, title, text, readMode, readAppName)
            if (rawText.isBlank()) return@execute

            // Apply word replacement rules
            rawText = applyWordRules(rawText)

            // Language detection — L26: generic routing for all supported languages
            val appLang = getAppLanguage(pkgName)
            val detectedLang = detectLanguage("$title $text", appLang)
            val translateEnabled = repo.getBoolean("translate_${detectedLang}_enabled", false)
            var effectiveLang = detectedLang
            if (translateEnabled) {
                val targetLang = repo.getString("translate_${detectedLang}_lang")
                if (targetLang.isNotBlank() && targetLang != detectedLang) {
                    val translated = c.notificationTranslator.translate(rawText, detectedLang, targetLang)
                    if (translated != rawText) rawText = translated
                    effectiveLang = targetLang
                }
            }

            // Pick voice profile: per-app rule > language route > active
            val profileId = rule?.profileId?.takeIf { it.isNotEmpty() }
                ?: resolveProfileByLanguage(effectiveLang, profiles)
                ?: activeId
            val profile = profiles.find { it.id == profileId } ?: VoiceProfile()

            // C-04: Force local TTS for privacy-sensitive apps
            val effectiveVoiceId = if (rule?.forceLocal == true && VoiceRegistry.isCloud(profile.voiceName)) {
                KokoroVoices.default().id
            } else {
                profile.voiceName
            }

            lastSpokenText = rawText
            lastNotificationTime = now

            val maxQueue = repo.getInt("notif_max_queue", 10).coerceAtLeast(1)

            c.audioPipeline.enqueue(AudioPipeline.Item(
                text     = rawText,
                voiceId  = effectiveVoiceId,
                pitch    = profile.pitch,
                speed    = profile.speed,
                language = effectiveLang
            ), maxQueue)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (repo.getBoolean("notif_skip_swiped", true)) {
            synchronized(swipedKeys) { swipedKeys.add(sbn.key) }
            synchronized(readKeys) { readKeys.remove(sbn.key) }
        }
        if (repo.getBoolean("notif_stop_on_swipe", false)) {
            c.audioPipeline.stop()
        }
    }

    private fun cleanupOldKeys(now: Long) {
        val iter = readKeys.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value > KEY_EXPIRY_MS) iter.remove() else break
        }
    }

    /**
     * Apply word replacement rules to text before speaking (H11).
     * Rules are find→replace pairs configured in the Rules tab.
     * Case-insensitive matching (handles "lol", "LOL", "Lol" etc).
     */
    private fun applyWordRules(text: String): String {
        val rules = cachedWordRules ?: loadWordRules().also { cachedWordRules = it }
        return NotificationFormatter.applyWordRules(text, rules)
    }

    private fun loadWordRules(): List<Pair<String, String>> {
        return repo.getWordRules()
            .filter { it.find.isNotBlank() }
            .map { it.find to it.replace }
    }

    private fun isDndActive(): Boolean {
        if (!repo.getBoolean("dnd_enabled", false)) return false
        val start = repo.getInt("dnd_start", 22)
        val end   = repo.getInt("dnd_end",   8)
        val hour  = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return NotificationFormatter.isDndActiveForHour(hour, start, end)
    }

    private fun getAppName(pkg: String) = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) { pkg }

    private fun resolveProfileByLanguage(lang: String, profiles: List<VoiceProfile>): String? {
        if (!repo.getBoolean("lang_routing_enabled", false)) return null
        val id = repo.getString("lang_profile_$lang")
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

    private val languageIdentifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.3f)
                .build()
        )
    }

    private fun detectLanguage(text: String, appLangHint: String? = null): String {
        if (text.isBlank()) return appLangHint ?: "en"

        val latch = CountDownLatch(1)
        var detected = "und"
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { langCode -> detected = langCode; latch.countDown() }
            .addOnFailureListener { e ->
                Log.w(TAG, "ML Kit language ID failed", e)
                latch.countDown()
            }

        // Wait up to 500ms — this runs on the processingExecutor background thread
        latch.await(500, TimeUnit.MILLISECONDS)

        if (detected == "und") return appLangHint ?: "en"
        return detected
    }

    /** Speak text directly using the given profile (preview, voice commands). */
    fun speakDirect(text: String, profile: VoiceProfile) {
        c.audioPipeline.enqueue(AudioPipeline.Item(
            text    = text,
            voiceId = profile.voiceName,
            pitch   = profile.pitch,
            speed   = profile.speed
        ))
    }

    fun stopSpeaking() = c.audioPipeline.stop()
}

package com.kokoro.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray

class NotificationReaderService : NotificationListenerService() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private var dailyCount = 0
    private var lastCountDay = -1

    companion object {
        private const val TAG = "NotiReaderService"
        private const val CHANNEL_ID = "kokoro_foreground"
        private const val FOREGROUND_ID = 1

        var instance: NotificationReaderService? = null

        /** Last spoken text — used by "can you repeat?" voice command */
        var lastSpokenText: String = ""
            private set

        /** Timestamp of last notification — used by "how long ago?" voice command */
        var lastNotificationTime: Long = 0L
            private set

        fun recordSpoken(text: String) {
            lastSpokenText = text
            lastNotificationTime = System.currentTimeMillis()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AudioPipeline.start(this)
        // Eagerly warm up the TTS engine so first synthesis has zero lag
        SherpaEngine.warmUp(this)
        // Start foreground notification to keep the service alive in background
        startForegroundNotification()
        Log.d(TAG, "Service created, foreground notification started")
    }

    override fun onDestroy() {
        instance = null
        VoiceCommandListener.stop()
        AudioPipeline.shutdown()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    /**
     * Called when the listener is connected to the notification system.
     * This confirms the notification access permission is active.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Listener connected — notification access is active")
        prefs.edit().putBoolean("listener_connected", true).apply()
    }

    /**
     * Called when the listener is disconnected (e.g. user revoked access).
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected — notification access may have been revoked")
        prefs.edit().putBoolean("listener_connected", false).apply()
        // Request rebind — Android will reconnect the service if permission is still granted
        requestRebind(android.content.ComponentName(this, NotificationReaderService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("service_enabled", true)) return
        if (sbn.packageName == packageName) return
        if (isDndActive()) return

        val rules = AppRule.loadAll(prefs)
        val rule  = rules.find { it.packageName == sbn.packageName }
        if (rule?.readMode == "skip" || rule?.enabled == false) return

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
            ?: prefs.getString("active_profile_id", "")
        val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
        val modulated = VoiceModulator.modulate(profile, signal)

        val readMode = rule?.readMode ?: prefs.getString("read_mode", "full") ?: "full"
        val rawText = buildMessage(appName, title, text, readMode)
        if (rawText.isBlank()) return

        // Record for "can you repeat?" / "how long ago?" voice commands
        recordSpoken(rawText)

        AudioPipeline.enqueue(AudioPipeline.Item(
            rawText   = rawText,
            profile   = profile,
            modulated = modulated,
            signal    = signal,
            rules     = loadWordingRules(),
            priority  = signal.urgencyType == UrgencyType.EXPIRING
        ))
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

    // ── Foreground notification ──────────────────────────────────────────────

    private fun startForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kokoro Reader Background",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Kokoro Reader active in the background"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kokoro Reader")
            .setContentText("Listening for notifications")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        try {
            startForeground(FOREGROUND_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }
}

package com.kokoro.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray

class NotificationReaderService : NotificationListenerService() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    @Volatile private var dailyCount = 0
    @Volatile private var lastCountDay = -1
    private val countLock = Object()

    /**
     * Tracks the last notification content per package to avoid re-reading
     * identical text when an app updates an existing notification
     * (e.g. WhatsApp appending messages to the same notification).
     */
    private val lastNotificationContent = LinkedHashMap<String, String>(32, 0.75f, true)
    private val dedupLock = Object()

    // ── Sender history (§7.0) ────────────────────────────────────────────────
    // Key = title (sender name heuristic), Value = SenderRecord
    private data class SenderRecord(
        val senderId: String,
        val count: Int = 1,
        val lastTimestamp: Long = System.currentTimeMillis()
    ) {
        fun pressure(now: Long): Float {
            val recencyMinutes = (now - lastTimestamp) / 60000f
            val recencyDecay = (1f - recencyMinutes / 30f).coerceIn(0f, 1f)
            return (count / 5f * recencyDecay).coerceIn(0f, 1f)
        }
    }
    private val senderHistory = LinkedHashMap<String, SenderRecord>(32, 0.75f, true)
    private val senderLock = Object()

    // ── MoodState (§1.0) ─────────────────────────────────────────────────────
    @Volatile private var currentMood: MoodState? = null
    private val moodLock = Object()

    // ── Conversation grouping (§3.8) ──────────────────────────────────────
    // Buffers rapid messages from the same sender to read as a batch.
    private data class PendingMessage(
        val appName: String,
        val title: String,
        val text: String,
        val sbn: android.service.notification.StatusBarNotification
    )
    private val groupBuffer = LinkedHashMap<String, MutableList<PendingMessage>>()
    private val groupPendingFlush = HashMap<String, Runnable>()
    private val groupLock = Object()
    private val groupHandler = android.os.Handler(android.os.Looper.getMainLooper())
    /** How long to wait for more messages before flushing a group (ms). */
    private val GROUP_DELAY_MS = 1500L

    companion object {
        private const val TAG = "NotiReaderService"
        private const val CHANNEL_ID = "kokoro_foreground"
        private const val FOREGROUND_ID = 1

        var instance: NotificationReaderService? = null

        /** Last spoken text — used by "can you repeat?" voice command */
        @Volatile var lastSpokenText: String = ""
            private set

        /** Timestamp of last notification — used by "how long ago?" voice command */
        @Volatile var lastNotificationTime: Long = 0L
            private set

        @Synchronized fun recordSpoken(text: String) {
            lastSpokenText = text
            lastNotificationTime = System.currentTimeMillis()
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            instance = this
            AudioPipeline.start(this)
            currentMood = MoodState.load(prefs)
            startForegroundNotification()

            // Write "alive" immediately so the UI knows the service is running,
            // even if warmUp() detects a crash loop and never starts the engine.
            TtsBridge.writeStatus(this, ready = false, status = "starting", error = null, alive = true)

            // Request battery optimization exemption BEFORE model loading.
            // HyperOS aggressively kills background processes during the long
            // model loading phase. Battery exemption + foreground service is the
            // strongest protection against being killed mid-init.
            if (OemProtection.isBatteryOptimized(this)) {
                OemProtection.requestBatteryExemption(this)
            }

            // Eager engine init in the :tts process.
            updateForegroundText("Loading TTS engine…")
            SherpaEngine.onReadyCallback = {
                updateForegroundText("Listening for notifications")
                Log.d(TAG, "Engine ready — service fully operational")
            }
            SherpaEngine.warmUp(this)
            // warmUp() calls syncStatus() which overwrites the status file with the
            // actual engine state (loading, error, etc.). The "starting" above is just
            // a brief placeholder until warmUp's syncStatus runs.

            // Start voice commands if enabled
            val voiceCmdEnabled = prefs.getBoolean("voice_commands_enabled", false)
            if (voiceCmdEnabled) {
                VoiceCommandListener.start(this.applicationContext)
            }

            Log.d(TAG, "Service created, engine warming up in background")
        } catch (e: Throwable) {
            Log.e(TAG, "Error during service creation — service may not function correctly", e)
        }
    }

    override fun onDestroy() {
        try {
            // Flush any pending conversation groups before shutdown
            groupHandler.removeCallbacksAndMessages(null)
            synchronized(groupLock) {
                for (key in groupBuffer.keys.toList()) flushGroup(key)
            }
            // Persist mood before shutdown
            currentMood?.let { MoodState.save(prefs, it) }
            instance = null
            VoiceCommandListener.stop()
            AudioPipeline.shutdown()
            // Clear alive status for the UI process
            TtsBridge.writeStatus(this, ready = false, status = "stopped", error = null, alive = false)
        } catch (e: Throwable) {
            Log.e(TAG, "Error during service destruction", e)
        }
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    /**
     * Called when the listener is connected to the notification system.
     * This confirms the notification access permission is active.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            instance = this
            Log.d(TAG, "Listener connected — notification access is active")
            prefs.edit().putBoolean("listener_connected", true).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onListenerConnected", e)
        }
    }

    /**
     * Called when the listener is disconnected (e.g. user revoked access).
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            Log.w(TAG, "Listener disconnected — notification access may have been revoked")
            prefs.edit().putBoolean("listener_connected", false).apply()
            // Request rebind — Android will reconnect the service if permission is still granted
            requestRebind(android.content.ComponentName(this, NotificationReaderService::class.java))
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onListenerDisconnected", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handleNotification(sbn)
        } catch (e: Throwable) {
            Log.e(TAG, "Error handling notification from ${sbn.packageName}", e)
        }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("service_enabled", true)) return
        if (sbn.packageName == packageName) return
        if (isDndActive()) return

        val rules = AppRule.loadAll(prefs)
        val rule  = rules.find { it.packageName == sbn.packageName }
        if (rule?.readMode == "skip" || rule?.enabled == false) return

        val extras  = sbn.notification?.extras ?: return
        val appName = getAppName(sbn.packageName)
        val title   = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        // Skip duplicate content — apps like WhatsApp update the same notification
        // repeatedly with identical text. Only read if content actually changed.
        val contentKey = "${sbn.packageName}:${sbn.id}"
        val contentValue = "$title|$text"
        synchronized(dedupLock) {
            if (lastNotificationContent[contentKey] == contentValue) return
            lastNotificationContent[contentKey] = contentValue
            // Evict least-recently-used entries to prevent unbounded growth
            while (lastNotificationContent.size > 100) {
                val it = lastNotificationContent.iterator()
                it.next()
                it.remove()
            }
        }

        // ── Conversation grouping (§3.8) ─────────────────────────────────
        // Buffer messages from the same sender and flush after a short delay.
        // This batches rapid chat messages into a single reading:
        //   "3 messages from John: [msg1]. [msg2]. [msg3]."
        val groupKey = "${sbn.packageName}|$title"
        val pending = PendingMessage(appName, title, text, sbn)

        // Urgent notifications bypass grouping — read immediately
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val quickSignal = SignalExtractor.extract(
            packageName = sbn.packageName, appName = appName,
            title = title, text = text, hourOfDay = hour, floodCount = 0
        )
        if (quickSignal.urgencyType == UrgencyType.EXPIRING) {
            // Flush any pending messages for this sender first, then process immediately
            flushGroup(groupKey)
            dispatchNotification(sbn, appName, title, text, rule)
            return
        }

        synchronized(groupLock) {
            val list = groupBuffer.getOrPut(groupKey) { mutableListOf() }
            list.add(pending)
            // Cancel any existing delayed flush for this group key
            groupPendingFlush.remove(groupKey)?.let { groupHandler.removeCallbacks(it) }
            // Schedule a new delayed flush — if no more messages arrive within
            // GROUP_DELAY_MS, the group is flushed and read as a batch.
            val flushRunnable = Runnable { flushGroup(groupKey) }
            groupPendingFlush[groupKey] = flushRunnable
            groupHandler.postDelayed(flushRunnable, GROUP_DELAY_MS)
        }
    }

    /** Flush a pending group: read all buffered messages as a batch. */
    private fun flushGroup(groupKey: String) {
        val messages: List<PendingMessage>
        synchronized(groupLock) {
            messages = groupBuffer.remove(groupKey) ?: return
            groupPendingFlush.remove(groupKey)?.let { groupHandler.removeCallbacks(it) }
        }
        if (messages.isEmpty()) return

        val rules = AppRule.loadAll(prefs)
        val first = messages.first()
        val rule = rules.find { it.packageName == first.sbn.packageName }

        if (messages.size == 1) {
            // Single message — dispatch normally
            dispatchNotification(first.sbn, first.appName, first.title, first.text, rule)
        } else {
            // Batched: combine texts into a single reading
            val readMode = rule?.readMode ?: prefs.getString("read_mode", "full") ?: "full"
            val combined = when (readMode) {
                "text_only" -> messages.joinToString(". ") { it.text }
                "app_only"  -> first.appName
                else -> {
                    val prefix = if (prefs.getBoolean("read_app_name", true)) "${first.appName}. " else ""
                    val count = messages.size
                    val sender = first.title.ifBlank { first.appName }
                    val texts = messages.joinToString(". ") { it.text }
                    "$prefix$count messages from $sender: $texts"
                }
            }
            if (combined.isBlank()) return

            Log.d(TAG, "Conversation group: ${messages.size} messages from ${first.title}")
            recordSpoken(combined)
            // Use first message's context for signal extraction
            dispatchText(combined, first.sbn, first.appName, first.title, first.text, rule)
        }
    }

    /** Dispatch a single notification through the full pipeline. */
    private fun dispatchNotification(
        sbn: StatusBarNotification, appName: String, title: String, text: String,
        rule: AppRule?
    ) {
        val readMode = rule?.readMode ?: prefs.getString("read_mode", "full") ?: "full"
        val rawText = buildMessage(appName, title, text, readMode)
        if (rawText.isBlank()) return
        recordSpoken(rawText)
        dispatchText(rawText, sbn, appName, title, text, rule)
    }

    /** Common dispatch: extract signal → mood → modulate → enqueue. */
    private fun dispatchText(
        rawText: String, sbn: StatusBarNotification,
        appName: String, title: String, text: String, rule: AppRule?
    ) {
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        val floodCount = synchronized(countLock) {
            if (today != lastCountDay) { dailyCount = 0; lastCountDay = today }
            dailyCount++
            dailyCount
        }

        // ── Sender history lookup (§7.0) ──────────────────────────────────
        val now = System.currentTimeMillis()
        val senderId = title.ifBlank { sbn.packageName }
        val senderResult = synchronized(senderLock) {
            val existing = senderHistory[senderId]
            val previousTimestamp = existing?.lastTimestamp ?: now
            val updated = if (existing != null) {
                existing.copy(count = existing.count + 1, lastTimestamp = now)
            } else {
                SenderRecord(senderId, 1, now)
            }
            senderHistory[senderId] = updated
            // Evict old entries
            while (senderHistory.size > 100) {
                val it = senderHistory.iterator()
                it.next()
                it.remove()
            }
            Pair(updated, previousTimestamp)
        }
        val senderRecord = senderResult.first
        val previousTimestamp = senderResult.second

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val signal = SignalExtractor.extract(
            packageName = sbn.packageName,
            appName     = appName,
            title       = title,
            text        = text,
            hourOfDay   = hour,
            floodCount  = floodCount
        ).copy(
            // Inject sender history into signal (§7.0)
            senderRepeat   = senderRecord.count,
            senderRecency  = now - previousTimestamp,
            senderPressure = senderRecord.pressure(now)
        )

        val profiles  = VoiceProfile.loadAll(prefs)
        val profileId = rule?.profileId?.takeIf { it.isNotEmpty() }
            ?: prefs.getString("active_profile_id", "")
        val profile = profiles.find { it.id == profileId } ?: VoiceProfile()

        // ── MoodState update + modulation (§1.0) ─────────────────────────
        val mood = synchronized(moodLock) {
            val loaded = currentMood ?: MoodState.load(prefs)
            val updated = MoodUpdater.update(loaded.decayed(profile.sensitivity.moodDecayRate), signal, profile.sensitivity.moodVelocity)
            currentMood = updated
            // Persist mood periodically (every 10 notifications)
            if (updated.sessionCount % 10 == 0) MoodState.save(prefs, updated)
            updated
        }

        val modulated = VoiceModulator.modulate(profile, signal, mood, hour)

        AudioPipeline.enqueue(AudioPipeline.Item(
            rawText   = rawText,
            profile   = profile,
            modulated = modulated,
            signal    = signal,
            rules     = loadWordingRules(),
            priority  = signal.urgencyType == UrgencyType.EXPIRING,
            mood      = mood
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
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(pkg, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") packageManager.getApplicationInfo(pkg, 0)
        }
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
        try {
            AudioPipeline.testSpeak(this, text, profile, rules)
        } catch (e: Exception) {
            Log.e(TAG, "Error in testSpeak", e)
        }
    }

    fun stopSpeaking() = AudioPipeline.stop()

    // ── Foreground notification ──────────────────────────────────────────────

    private fun startForegroundNotification() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            if (nm == null) {
                Log.e(TAG, "NotificationManager unavailable — cannot start foreground")
                return
            }

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kyōkan Background",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Kyōkan active in the background"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)

            val notification = buildForegroundNotification("Starting…")

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FOREGROUND_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(FOREGROUND_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun buildForegroundNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kyōkan")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Updates the foreground notification text (e.g. "Loading TTS engine…" → "Listening").
     * Safe to call from any thread.
     */
    private fun updateForegroundText(text: String) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.notify(FOREGROUND_ID, buildForegroundNotification(text))
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to update foreground notification", e)
        }
    }
}

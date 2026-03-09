package com.kokoro.reader

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.preference.PreferenceManager
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class NotificationReaderService : NotificationListenerService() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val queue = LinkedBlockingQueue<Pair<String, VoiceProfile>>()
    private val isSpeaking = AtomicBoolean(false)
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    companion object {
        var instance: NotificationReaderService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { isSpeaking.set(true) }
                    override fun onDone(id: String?)  { isSpeaking.set(false); processQueue() }
                    override fun onError(id: String?) { isSpeaking.set(false) }
                })
                ttsReady = true
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("service_enabled", true)) return
        if (sbn.packageName == packageName) return
        if (isDndActive()) return

        val rules = AppRule.loadAll(prefs)
        val rule = rules.find { it.packageName == sbn.packageName }
        if (rule?.readMode == "skip" || rule?.enabled == false) return

        val extras = sbn.notification?.extras ?: return
        val appName = getAppName(sbn.packageName)
        val title   = extras.getString("android.title") ?: ""
        val text    = extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        val readMode = rule?.readMode ?: prefs.getString("read_mode", "full") ?: "full"
        val raw = buildMessage(appName, title, text, readMode)
        if (raw.isBlank()) return

        val profiles = VoiceProfile.loadAll(prefs)
        val profileId = rule?.profileId?.takeIf { it.isNotEmpty() }
            ?: prefs.getString("active_profile_id", "")
        val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
        val wordingRules = loadWordingRules()
        val processed = VoiceTransform.process(raw, profile, wordingRules)

        queue.offer(Pair(processed, profile))
        if (!isSpeaking.get()) processQueue()
    }

    private fun buildMessage(appName: String, title: String, text: String, mode: String) = when (mode) {
        "app_only"   -> appName
        "title_only" -> "$appName. $title"
        "text_only"  -> text
        else -> buildString {
            if (prefs.getBoolean("read_app_name", true)) append("$appName. ")
            if (title.isNotBlank()) append("$title. ")
            if (text.isNotBlank()) append(text)
        }
    }

    private fun processQueue() {
        if (!ttsReady) return
        val (msg, profile) = queue.poll() ?: return
        tts?.voices?.find { it.name == profile.voiceName }?.let { tts?.voice = it }
        tts?.setPitch(profile.pitch)
        tts?.setSpeechRate(profile.speed)
        tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, "n_${System.currentTimeMillis()}")
    }

    private fun isDndActive(): Boolean {
        if (!prefs.getBoolean("dnd_enabled", false)) return false
        val start = prefs.getInt("dnd_start", 22)
        val end   = prefs.getInt("dnd_end", 8)
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

    fun stopSpeaking() { queue.clear(); tts?.stop(); isSpeaking.set(false) }

    fun testSpeak(text: String, profile: VoiceProfile, rules: List<Pair<String, String>>) {
        val processed = VoiceTransform.process(text, profile, rules)
        tts?.voices?.find { it.name == profile.voiceName }?.let { tts?.voice = it }
        tts?.setPitch(profile.pitch)
        tts?.setSpeechRate(profile.speed)
        tts?.speak(processed, TextToSpeech.QUEUE_FLUSH, null, "test")
    }

    override fun onDestroy() { instance = null; tts?.shutdown(); super.onDestroy() }
}

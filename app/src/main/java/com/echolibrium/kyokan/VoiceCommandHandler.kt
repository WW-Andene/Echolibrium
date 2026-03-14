package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Processes recognized voice commands and generates spoken responses.
 *
 * Supported commands:
 *   - "can you repeat?" / "repeat that" / "say that again"
 *   - "how long ago?" / "when was that?"
 *   - "stop" / "shut up" / "be quiet"
 *   - "what time is it?"
 */
object VoiceCommandHandler {

    private const val TAG = "VoiceCommandHandler"

    private val REPEAT_TRIGGERS = listOf(
        "can you repeat", "repeat that", "say that again", "what did you say",
        "say it again", "repeat please", "repeat", "one more time"
    )
    private val TIME_AGO_TRIGGERS = listOf(
        "how long ago", "when was that", "how long ago was that",
        "when did that come"
    )
    private val STOP_TRIGGERS = listOf(
        "stop", "shut up", "be quiet", "silence", "quiet", "enough", "stop talking"
    )
    private val TIME_TRIGGERS = listOf(
        "what time is it", "what's the time", "tell me the time", "current time"
    )

    fun handleCommand(ctx: Context, candidates: List<String>): Boolean {
        val normalized = candidates.map { it.lowercase().trim() }
        return when {
            matchesAny(normalized, REPEAT_TRIGGERS) -> { handleRepeat(ctx); true }
            matchesAny(normalized, TIME_AGO_TRIGGERS) -> { handleTimeAgo(ctx); true }
            matchesAny(normalized, STOP_TRIGGERS) -> { handleStop(); true }
            matchesAny(normalized, TIME_TRIGGERS) -> { handleTime(ctx); true }
            else -> false
        }
    }

    private fun matchesAny(candidates: List<String>, triggers: List<String>): Boolean =
        candidates.any { candidate -> triggers.any { trigger -> candidate.contains(trigger) } }

    private fun handleRepeat(ctx: Context) {
        val lastText = NotificationReaderService.lastSpokenText
        if (lastText.isBlank()) {
            speak(ctx, "No notification has been read yet.")
        } else {
            speak(ctx, "Repeating: $lastText")
        }
        Log.d(TAG, "Handled: repeat command")
    }

    private fun handleTimeAgo(ctx: Context) {
        val lastTime = NotificationReaderService.lastNotificationTime
        if (lastTime == 0L) {
            speak(ctx, "No notification has been received yet.")
        } else {
            val elapsed = System.currentTimeMillis() - lastTime
            speak(ctx, "The last notification was ${formatElapsed(elapsed)} ago.")
        }
        Log.d(TAG, "Handled: time ago command")
    }

    private fun handleStop() {
        AudioPipeline.stop()
        Log.d(TAG, "Handled: stop command")
    }

    private fun handleTime(ctx: Context) {
        val timeStr = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date())
        speak(ctx, "It is $timeStr.")
        Log.d(TAG, "Handled: time command")
    }

    private fun speak(ctx: Context, text: String) {
        val service = NotificationReaderService.instance ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val profiles = VoiceProfile.loadAll(prefs)
        val profileId = prefs.getString("active_profile_id", "") ?: ""
        val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
        service.testSpeak(text, profile)
    }

    private fun formatElapsed(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> {
                val rm = minutes % 60
                if (rm > 0) "$hours hour${if (hours > 1) "s" else ""} and $rm minute${if (rm > 1) "s" else ""}"
                else "$hours hour${if (hours > 1) "s" else ""}"
            }
            minutes > 0 -> {
                val rs = seconds % 60
                if (rs > 0) "$minutes minute${if (minutes > 1) "s" else ""} and $rs second${if (rs > 1) "s" else ""}"
                else "$minutes minute${if (minutes > 1) "s" else ""}"
            }
            seconds > 0 -> "$seconds second${if (seconds > 1) "s" else ""}"
            else -> "less than a second"
        }
    }
}

package com.kokoro.reader

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Processes recognized voice commands and generates spoken responses.
 *
 * Supported commands:
 *   • "can you repeat?" / "repeat that" / "say that again" / "what did you say?"
 *     → Repeats the last notification that was spoken
 *
 *   • "how long ago?" / "when was that?" / "how long ago was that?"
 *     → Says how much time passed since the last notification
 *
 *   • "stop" / "shut up" / "be quiet" / "silence"
 *     → Stops current speech
 *
 *   • "what time is it?" / "what's the time?"
 *     → Speaks the current time
 */
object VoiceCommandHandler {

    private const val TAG = "VoiceCommandHandler"

    // Command patterns — checked with fuzzy matching (contains, not exact)
    private val REPEAT_TRIGGERS = listOf(
        "can you repeat", "repeat that", "say that again", "what did you say",
        "say it again", "repeat please", "repeat", "one more time",
        "play it again", "come again"
    )

    private val TIME_AGO_TRIGGERS = listOf(
        "how long ago", "when was that", "how long ago was that",
        "when did that come", "how old is that", "when was the last"
    )

    private val STOP_TRIGGERS = listOf(
        "stop", "shut up", "be quiet", "silence", "quiet", "enough", "stop talking"
    )

    private val TIME_TRIGGERS = listOf(
        "what time is it", "what's the time", "tell me the time", "current time"
    )

    /**
     * Try to match recognized speech against known commands.
     * @param ctx Application context
     * @param candidates List of recognition alternatives (most confident first)
     * @return true if a command was handled
     */
    fun handleCommand(ctx: Context, candidates: List<String>): Boolean {
        // Normalize all candidates to lowercase for matching
        val normalized = candidates.map { it.lowercase().trim() }

        return when {
            matchesAny(normalized, REPEAT_TRIGGERS) -> { handleRepeat(ctx); true }
            matchesAny(normalized, TIME_AGO_TRIGGERS) -> { handleTimeAgo(ctx); true }
            matchesAny(normalized, STOP_TRIGGERS) -> { handleStop(); true }
            matchesAny(normalized, TIME_TRIGGERS) -> { handleTime(ctx); true }
            else -> false
        }
    }

    private fun matchesAny(candidates: List<String>, triggers: List<String>): Boolean {
        return candidates.any { candidate ->
            triggers.any { trigger -> candidate.contains(trigger) }
        }
    }

    // ── Command handlers ────────────────────────────────────────────────────

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
            val text = formatElapsed(elapsed)
            speak(ctx, "The last notification was $text ago.")
        }
        Log.d(TAG, "Handled: time ago command")
    }

    private fun handleStop() {
        AudioPipeline.stop()
        Log.d(TAG, "Handled: stop command")
    }

    private fun handleTime(ctx: Context) {
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        speak(ctx, "It is %d:%02d.".format(hour, minute))
        Log.d(TAG, "Handled: time command")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun speak(ctx: Context, text: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val profiles = VoiceProfile.loadAll(prefs)
        val profileId = prefs.getString("active_profile_id", "") ?: ""
        val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
        val rules = emptyList<Pair<String, String>>()
        AudioPipeline.testSpeak(ctx, text, profile, rules)
    }

    private fun formatElapsed(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> {
                val remainMinutes = minutes % 60
                if (remainMinutes > 0) "$hours hour${if (hours > 1) "s" else ""} and $remainMinutes minute${if (remainMinutes > 1) "s" else ""}"
                else "$hours hour${if (hours > 1) "s" else ""}"
            }
            minutes > 0 -> {
                val remainSeconds = seconds % 60
                if (remainSeconds > 0) "$minutes minute${if (minutes > 1) "s" else ""} and $remainSeconds second${if (remainSeconds > 1) "s" else ""}"
                else "$minutes minute${if (minutes > 1) "s" else ""}"
            }
            seconds > 0 -> "$seconds second${if (seconds > 1) "s" else ""}"
            else -> "less than a second"
        }
    }
}

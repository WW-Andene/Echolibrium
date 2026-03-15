package com.echolibrium.kyokan

import android.content.Context
import android.util.Log

/**
 * Processes recognized voice commands and generates spoken responses.
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class VoiceCommandHandler {

    companion object {
        private const val TAG = "VoiceCommandHandler"
    }

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
            matchesAny(normalized, STOP_TRIGGERS) -> { handleStop(ctx); true }
            matchesAny(normalized, TIME_TRIGGERS) -> { handleTime(ctx); true }
            else -> false
        }
    }

    private fun matchesAny(candidates: List<String>, triggers: List<String>): Boolean =
        candidates.any { candidate -> triggers.any { trigger -> candidate.contains(trigger) } }

    private fun handleRepeat(ctx: Context) {
        val lastText = NotificationReaderService.lastSpokenText
        if (lastText.isBlank()) {
            speak(ctx, ctx.getString(R.string.cmd_no_notification_yet))
        } else {
            speak(ctx, ctx.getString(R.string.cmd_repeating, lastText))
        }
        Log.d(TAG, "Handled: repeat command")
    }

    private fun handleTimeAgo(ctx: Context) {
        val lastTime = NotificationReaderService.lastNotificationTime
        if (lastTime == 0L) {
            speak(ctx, ctx.getString(R.string.cmd_no_notification_received))
        } else {
            val elapsed = System.currentTimeMillis() - lastTime
            speak(ctx, ctx.getString(R.string.cmd_last_notification_ago, formatElapsed(ctx, elapsed)))
        }
        Log.d(TAG, "Handled: time ago command")
    }

    private fun handleStop(ctx: Context) {
        ctx.container.audioPipeline.stop()
        Log.d(TAG, "Handled: stop command")
    }

    private fun handleTime(ctx: Context) {
        val timeStr = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date())
        speak(ctx, ctx.getString(R.string.cmd_current_time, timeStr))
        Log.d(TAG, "Handled: time command")
    }

    private fun speak(ctx: Context, text: String) {
        val service = NotificationReaderService.instance ?: return
        val repo = ctx.container.repo
        val profiles = repo.getProfiles()
        val profileId = repo.activeProfileId
        val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
        service.speakDirect(text, profile)
    }

    private fun formatElapsed(ctx: Context, ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val remMinutes = minutes % 60
        val remSeconds = seconds % 60
        return when {
            hours > 0 && remMinutes > 0 -> when {
                hours > 1 && remMinutes > 1 -> ctx.getString(R.string.elapsed_hours_minutes, hours, remMinutes)
                hours == 1L && remMinutes > 1 -> ctx.getString(R.string.elapsed_hour_minutes, hours, remMinutes)
                hours > 1 && remMinutes == 1L -> ctx.getString(R.string.elapsed_hours_minute, hours, remMinutes)
                else -> ctx.getString(R.string.elapsed_hour_minute, hours, remMinutes)
            }
            hours > 0 -> if (hours > 1) ctx.getString(R.string.elapsed_hours, hours) else ctx.getString(R.string.elapsed_hour, hours)
            minutes > 0 && remSeconds > 0 -> when {
                minutes > 1 && remSeconds > 1 -> ctx.getString(R.string.elapsed_minutes_seconds, minutes, remSeconds)
                minutes == 1L && remSeconds > 1 -> ctx.getString(R.string.elapsed_minute_seconds, minutes, remSeconds)
                minutes > 1 && remSeconds == 1L -> ctx.getString(R.string.elapsed_minutes_second, minutes, remSeconds)
                else -> ctx.getString(R.string.elapsed_minute_second, minutes, remSeconds)
            }
            minutes > 0 -> if (minutes > 1) ctx.getString(R.string.elapsed_minutes, minutes) else ctx.getString(R.string.elapsed_minute, minutes)
            seconds > 0 -> if (seconds > 1) ctx.getString(R.string.elapsed_seconds, seconds) else ctx.getString(R.string.elapsed_second, seconds)
            else -> ctx.getString(R.string.elapsed_less_than_second)
        }
    }
}

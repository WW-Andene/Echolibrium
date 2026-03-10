package com.kokoro.reader

import android.content.SharedPreferences

/**
 * Persistent mood state that accumulates across notifications.
 *
 * Mood is the sustained background vocal state — not triggered by any
 * single event, but by accumulated exposure. It affects the voice baseline
 * before per-notification signal reactions are applied.
 *
 * Three dimensions:
 *   valence   : -1.0 (very negative) to +1.0 (very positive)
 *   arousal   : 0.0 (exhausted/flat) to 1.0 (highly activated)
 *   stability : 0.0 (erratic/volatile) to 1.0 (calm/steady)
 */
data class MoodState(
    val valence: Float = DEFAULT_VALENCE,
    val arousal: Float = DEFAULT_AROUSAL,
    val stability: Float = DEFAULT_STABILITY,
    val lastUpdatedMs: Long = System.currentTimeMillis(),
    val sessionCount: Int = 0
) {
    /**
     * Decay mood toward neutral based on elapsed time.
     * Called before mood is used for modulation.
     */
    fun decayed(decayRate: Float = 0.08f): MoodState {
        val elapsedMinutes = (System.currentTimeMillis() - lastUpdatedMs) / 60000f
        // Linear decay toward defaults: factor drops from 1→0 over time
        val factor = (1f - decayRate * elapsedMinutes).coerceAtLeast(0f)
        return copy(
            valence   = valence * factor,
            arousal   = (arousal - DEFAULT_AROUSAL) * factor + DEFAULT_AROUSAL,
            stability = (stability - DEFAULT_STABILITY) * factor + DEFAULT_STABILITY
        )
    }

    companion object {
        const val DEFAULT_VALENCE = 0f
        const val DEFAULT_AROUSAL = 0.3f
        const val DEFAULT_STABILITY = 1.0f

        private const val KEY_VALENCE = "mood_valence"
        private const val KEY_AROUSAL = "mood_arousal"
        private const val KEY_STABILITY = "mood_stability"
        private const val KEY_UPDATED = "mood_last_updated"
        private const val KEY_COUNT = "mood_session_count"

        fun load(prefs: SharedPreferences): MoodState = MoodState(
            valence       = prefs.getFloat(KEY_VALENCE, DEFAULT_VALENCE),
            arousal       = prefs.getFloat(KEY_AROUSAL, DEFAULT_AROUSAL),
            stability     = prefs.getFloat(KEY_STABILITY, DEFAULT_STABILITY),
            lastUpdatedMs = prefs.getLong(KEY_UPDATED, System.currentTimeMillis()),
            sessionCount  = prefs.getInt(KEY_COUNT, 0)
        )

        fun save(prefs: SharedPreferences, state: MoodState) {
            prefs.edit()
                .putFloat(KEY_VALENCE, state.valence)
                .putFloat(KEY_AROUSAL, state.arousal)
                .putFloat(KEY_STABILITY, state.stability)
                .putLong(KEY_UPDATED, state.lastUpdatedMs)
                .putInt(KEY_COUNT, state.sessionCount)
                .apply()
        }
    }
}

/**
 * Updates mood based on incoming signal.
 * Nudges are intentionally small — mood is slow-moving by design.
 */
object MoodUpdater {

    fun update(current: MoodState, signal: SignalMap, moodVelocity: Float = 1.0f): MoodState {
        var vNudge = 0f
        var aNudge = 0f
        var sNudge = 0f

        // ── Valence nudges ────────────────────────────────────────────────
        if (signal.warmth == WarmthLevel.HIGH)       vNudge += 0.06f
        if (signal.emojiHappy)                       vNudge += 0.06f
        if (signal.emojiLove)                        vNudge += 0.06f
        if (signal.has(Intent.GREETING))             vNudge += 0.04f
        if (signal.warmth == WarmthLevel.DISTRESSED) vNudge -= 0.08f
        if (signal.emojiSad)                         vNudge -= 0.08f
        if (signal.emojiAngry)                       vNudge -= 0.08f
        if (signal.urgencyType == UrgencyType.REAL ||
            signal.urgencyType == UrgencyType.EXPIRING) vNudge -= 0.05f
        if (signal.stakesType == StakesType.EMOTIONAL ||
            signal.stakesLevel == StakesLevel.HIGH)  vNudge -= 0.06f
        if (signal.floodCount > 20)                  vNudge -= 0.03f

        // ── Arousal nudges ────────────────────────────────────────────────
        if (signal.urgencyType == UrgencyType.REAL ||
            signal.urgencyType == UrgencyType.EXPIRING ||
            signal.urgencyType == UrgencyType.BLOCKING) aNudge += 0.07f
        if (signal.trajectory == Trajectory.PEAKED)  aNudge += 0.05f
        if (signal.capsRatio > 0.5f)                 aNudge += 0.04f
        if (signal.trajectory == Trajectory.COLLAPSED) aNudge -= 0.08f
        if (signal.hourOfDay in 22..23 || signal.hourOfDay in 0..6) aNudge -= 0.04f
        if (signal.floodCount > 30)                  aNudge -= 0.05f

        // ── Stability nudges ──────────────────────────────────────────────
        if (signal.register == Register.FORMAL ||
            signal.register == Register.MINIMAL)     sNudge += 0.03f
        if (signal.register == Register.DRAMATIC ||
            signal.register == Register.RAW)         sNudge -= 0.06f
        if (signal.unknownFactor)                    sNudge -= 0.03f
        if (signal.fragmented)                       sNudge -= 0.02f

        // Apply velocity multiplier (personality-dependent)
        vNudge *= moodVelocity
        aNudge *= moodVelocity
        sNudge *= moodVelocity

        return current.copy(
            valence       = (current.valence + vNudge).coerceIn(-1f, 1f),
            arousal       = (current.arousal + aNudge).coerceIn(0f, 1f),
            stability     = (current.stability + sNudge).coerceIn(0f, 1f),
            lastUpdatedMs = System.currentTimeMillis(),
            sessionCount  = current.sessionCount + 1
        )
    }
}

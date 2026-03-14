package com.echolibrium.kyokan

/**
 * Event-driven personality system.
 *
 * PersonalityEvents are emitted by the notification pipeline and consumed by
 * commentary triggers, mood updates, and the observation layer.
 *
 * This replaces the per-notification CommentaryCondition evaluation with a
 * reactive event bus. Commentary triggers subscribe to specific event types,
 * making the system extensible without modifying evaluation logic.
 *
 * Events flow:  NotificationReaderService → PersonalityEventBus → subscribers
 *   - CommentaryTrigger: selects commentary lines based on events
 *   - MoodUpdater: adjusts mood based on events
 *   - (Future) Observation layer: logs events to SQLite
 */

/** Sealed class hierarchy for all personality-relevant events */
sealed class PersonalityEvent {
    /** Timestamp when this event was created */
    val timestampMs: Long = System.currentTimeMillis()

    /** Mood shifted beyond a threshold */
    data class MoodShift(
        val from: MoodState,
        val to: MoodState,
        val dominantAxis: String  // "valence", "arousal", or "stability"
    ) : PersonalityEvent()

    /** Notification flood detected — app is overwhelming */
    data class FloodDetected(
        val tier: FloodTier,
        val app: String,
        val countInWindow: Int
    ) : PersonalityEvent()

    /** Silence broken after a period of no notifications */
    data class SilenceBroken(
        val silenceDurationMs: Long,
        val breakingApp: String,
        val breakingSignal: SignalMap
    ) : PersonalityEvent()

    /** New sender type encountered (first human message, first bot, etc.) */
    data class NewSenderType(
        val type: SenderType,
        val app: String
    ) : PersonalityEvent()

    /** High-stakes notification received */
    data class HighStakes(
        val stakesType: StakesType,
        val stakesLevel: StakesLevel,
        val app: String
    ) : PersonalityEvent()

    /** Emotion blend triggered — complex mixed state detected */
    data class EmotionBlendTriggered(
        val blend: EmotionBlend,
        val signal: SignalMap
    ) : PersonalityEvent()

    /** Time-of-day transition (morning, night, etc.) */
    data class TimeTransition(
        val hourOfDay: Int,
        val isNight: Boolean,
        val isMorning: Boolean
    ) : PersonalityEvent()
}

/**
 * Simple event bus for PersonalityEvents.
 *
 * Thread-safe via synchronized dispatch. Subscribers are invoked synchronously
 * on the dispatching thread (AudioPipeline's daemon thread), so they should
 * not block.
 */
object PersonalityEventBus {

    /** Listener interface for event subscribers */
    fun interface EventListener {
        fun onEvent(event: PersonalityEvent)
    }

    private val listeners = mutableListOf<EventListener>()
    private val lock = Any()

    /** Subscribe to all personality events */
    fun subscribe(listener: EventListener) {
        synchronized(lock) { listeners.add(listener) }
    }

    /** Unsubscribe a listener */
    fun unsubscribe(listener: EventListener) {
        synchronized(lock) { listeners.remove(listener) }
    }

    /** Dispatch an event to all subscribers */
    fun emit(event: PersonalityEvent) {
        val snapshot: List<EventListener>
        synchronized(lock) { snapshot = listeners.toList() }
        for (listener in snapshot) {
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                android.util.Log.w("PersonalityEventBus", "Listener error", e)
            }
        }
    }

    /** Clear all subscribers (for shutdown) */
    fun clear() {
        synchronized(lock) { listeners.clear() }
    }
}

/**
 * Commentary trigger that subscribes to PersonalityEvents.
 *
 * Replaces the per-notification CommentaryCondition.matches() approach
 * with event-driven commentary selection.
 */
class CommentaryTrigger(
    private val pools: List<CommentaryPool>
) : PersonalityEventBus.EventListener {

    /** Queued commentary lines ready to be spoken */
    private val pendingLines = mutableListOf<Pair<String, String>>()  // (position, line)
    private val pendingLock = Any()

    override fun onEvent(event: PersonalityEvent) {
        for (pool in pools) {
            if (shouldTrigger(pool, event)) {
                val roll = (1..100).random()
                if (roll <= pool.frequency && pool.lines.isNotEmpty()) {
                    val line = pool.lines.random()
                    synchronized(pendingLock) {
                        pendingLines.add(pool.position to line)
                    }
                }
            }
        }
    }

    /** Check if a pool should trigger for a given event */
    private fun shouldTrigger(pool: CommentaryPool, event: PersonalityEvent): Boolean {
        val condType = pool.condition.type
        return when (event) {
            is PersonalityEvent.MoodShift -> condType in listOf(
                "traj_building", "traj_peaked", "traj_collapsed"
            )
            is PersonalityEvent.FloodDetected -> condType == "flooded"
            is PersonalityEvent.SilenceBroken -> condType == "always"
            is PersonalityEvent.NewSenderType -> when (event.type) {
                SenderType.HUMAN -> condType == "sender_human"
                SenderType.BOT, SenderType.SYSTEM -> condType == "source_system"
                else -> condType == "sender_unknown"
            }
            is PersonalityEvent.HighStakes -> when {
                event.stakesType == StakesType.FINANCIAL -> condType == "stakes_financial"
                event.stakesType == StakesType.EMOTIONAL -> condType == "stakes_emotional"
                event.stakesLevel == StakesLevel.HIGH -> condType == "stakes_high"
                else -> false
            }
            is PersonalityEvent.EmotionBlendTriggered -> condType == "always"
            is PersonalityEvent.TimeTransition -> when {
                event.isNight -> condType == "time_night"
                event.isMorning -> condType == "time_morning"
                else -> false
            }
        }
    }

    /**
     * Drain pending commentary for a given position ("pre" or "post").
     * Returns null if no commentary is pending.
     */
    fun drain(position: String): String? {
        synchronized(pendingLock) {
            val match = pendingLines.firstOrNull { it.first == position } ?: return null
            pendingLines.remove(match)
            return match.second
        }
    }

    /** Clear all pending commentary */
    fun clearPending() {
        synchronized(pendingLock) { pendingLines.clear() }
    }
}

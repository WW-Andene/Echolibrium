package com.echolibrium.kyokan

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite observation layer — logs DSP pipeline timing and PersonalityEvents.
 *
 * Provides the data substrate for the Custom Core observation layer:
 *   - Per-node DSP timing for profiling and optimization
 *   - PersonalityEvent history for behavior analysis
 *   - Utterance metadata for pattern detection
 *
 * Data is append-only with automatic pruning (keeps last 7 days).
 * All writes happen on the AudioPipeline daemon thread — no contention.
 */
class ObservationDb private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE dsp_timing (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp_ms INTEGER NOT NULL,
                utterance_id TEXT NOT NULL,
                node_name TEXT NOT NULL,
                duration_us INTEGER NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                samples_count INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE personality_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp_ms INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                event_data TEXT,
                app_package TEXT
            )
        """)
        db.execSQL("""
            CREATE TABLE utterances (
                id TEXT PRIMARY KEY,
                timestamp_ms INTEGER NOT NULL,
                voice_id TEXT NOT NULL,
                text_length INTEGER NOT NULL,
                total_dsp_us INTEGER NOT NULL DEFAULT 0,
                sample_rate INTEGER NOT NULL DEFAULT 0,
                pcm_samples INTEGER NOT NULL DEFAULT 0,
                emotion_blend TEXT,
                flood_tier TEXT,
                mood_valence REAL,
                mood_arousal REAL
            )
        """)

        // Indexes for time-range queries and aggregation
        db.execSQL("CREATE INDEX idx_dsp_timing_ts ON dsp_timing(timestamp_ms)")
        db.execSQL("CREATE INDEX idx_dsp_timing_utterance ON dsp_timing(utterance_id)")
        db.execSQL("CREATE INDEX idx_events_ts ON personality_events(timestamp_ms)")
        db.execSQL("CREATE INDEX idx_events_type ON personality_events(event_type)")
        db.execSQL("CREATE INDEX idx_utterances_ts ON utterances(timestamp_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        // Simple drop-and-recreate — observation data is expendable
        db.execSQL("DROP TABLE IF EXISTS dsp_timing")
        db.execSQL("DROP TABLE IF EXISTS personality_events")
        db.execSQL("DROP TABLE IF EXISTS utterances")
        onCreate(db)
    }

    // ── DSP Timing ─────────────────────────────────────────────────────────

    /**
     * Log timing for a single DSP node processing pass.
     */
    fun logNodeTiming(
        utteranceId: String,
        nodeName: String,
        durationUs: Long,
        enabled: Boolean,
        samplesCount: Int
    ) {
        writableDatabase.insert("dsp_timing", null, ContentValues().apply {
            put("timestamp_ms", System.currentTimeMillis())
            put("utterance_id", utteranceId)
            put("node_name", nodeName)
            put("duration_us", durationUs)
            put("enabled", if (enabled) 1 else 0)
            put("samples_count", samplesCount)
        })
    }

    /**
     * Log timing for an entire DSP chain pass.
     */
    fun logChainTiming(utteranceId: String, timings: List<NodeTiming>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (t in timings) {
                db.insert("dsp_timing", null, ContentValues().apply {
                    put("timestamp_ms", now)
                    put("utterance_id", utteranceId)
                    put("node_name", t.nodeName)
                    put("duration_us", t.durationUs)
                    put("enabled", if (t.enabled) 1 else 0)
                    put("samples_count", t.samplesCount)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── PersonalityEvents ──────────────────────────────────────────────────

    /**
     * Log a PersonalityEvent.
     */
    fun logEvent(event: PersonalityEvent, appPackage: String? = null) {
        writableDatabase.insert("personality_events", null, ContentValues().apply {
            put("timestamp_ms", event.timestampMs)
            put("event_type", event::class.simpleName ?: "Unknown")
            put("event_data", eventToData(event))
            put("app_package", appPackage)
        })
    }

    // ── Utterances ─────────────────────────────────────────────────────────

    /**
     * Log utterance metadata.
     */
    fun logUtterance(
        utteranceId: String,
        voiceId: String,
        textLength: Int,
        totalDspUs: Long,
        sampleRate: Int,
        pcmSamples: Int,
        emotionBlend: EmotionBlend?,
        floodTier: FloodTier?,
        moodValence: Float?,
        moodArousal: Float?
    ) {
        writableDatabase.insertWithOnConflict("utterances", null, ContentValues().apply {
            put("id", utteranceId)
            put("timestamp_ms", System.currentTimeMillis())
            put("voice_id", voiceId)
            put("text_length", textLength)
            put("total_dsp_us", totalDspUs)
            put("sample_rate", sampleRate)
            put("pcm_samples", pcmSamples)
            put("emotion_blend", emotionBlend?.name)
            put("flood_tier", floodTier?.name)
            put("mood_valence", moodValence)
            put("mood_arousal", moodArousal)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ── Queries ────────────────────────────────────────────────────────────

    /**
     * Average DSP node timing over the last N utterances.
     */
    fun avgNodeTimings(lastN: Int = 100): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val cursor = readableDatabase.rawQuery("""
            SELECT node_name, AVG(duration_us) as avg_us
            FROM dsp_timing
            WHERE enabled = 1
            AND utterance_id IN (
                SELECT DISTINCT utterance_id FROM dsp_timing
                ORDER BY timestamp_ms DESC LIMIT ?
            )
            GROUP BY node_name
            ORDER BY avg_us DESC
        """, arrayOf(lastN.toString()))
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(0)] = it.getLong(1)
            }
        }
        return result
    }

    /**
     * Count of PersonalityEvents by type in the last N hours.
     */
    fun eventCountsByType(lastHours: Int = 24): Map<String, Int> {
        val cutoff = System.currentTimeMillis() - lastHours * 3600_000L
        val result = mutableMapOf<String, Int>()
        val cursor = readableDatabase.rawQuery("""
            SELECT event_type, COUNT(*) as cnt
            FROM personality_events
            WHERE timestamp_ms > ?
            GROUP BY event_type
            ORDER BY cnt DESC
        """, arrayOf(cutoff.toString()))
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(0)] = it.getInt(1)
            }
        }
        return result
    }

    /**
     * Recent utterance stats: count, avg DSP time, avg text length.
     */
    fun utteranceStats(lastHours: Int = 24): UtteranceStats {
        val cutoff = System.currentTimeMillis() - lastHours * 3600_000L
        val cursor = readableDatabase.rawQuery("""
            SELECT COUNT(*), AVG(total_dsp_us), AVG(text_length), AVG(pcm_samples)
            FROM utterances WHERE timestamp_ms > ?
        """, arrayOf(cutoff.toString()))
        return cursor.use {
            if (it.moveToNext()) UtteranceStats(
                count = it.getInt(0),
                avgDspUs = it.getLong(1),
                avgTextLength = it.getInt(2),
                avgPcmSamples = it.getInt(3)
            ) else UtteranceStats()
        }
    }

    // ── Maintenance ────────────────────────────────────────────────────────

    /**
     * Prune data older than retentionDays. Call periodically (e.g., daily).
     */
    fun prune(retentionDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - retentionDays * 86400_000L
        val db = writableDatabase
        db.delete("dsp_timing", "timestamp_ms < ?", arrayOf(cutoff.toString()))
        db.delete("personality_events", "timestamp_ms < ?", arrayOf(cutoff.toString()))
        db.delete("utterances", "timestamp_ms < ?", arrayOf(cutoff.toString()))
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun eventToData(event: PersonalityEvent): String = when (event) {
        is PersonalityEvent.MoodShift ->
            "axis=${event.dominantAxis},v=${event.to.valence},a=${event.to.arousal},s=${event.to.stability}"
        is PersonalityEvent.FloodDetected ->
            "tier=${event.tier},app=${event.app},count=${event.countInWindow}"
        is PersonalityEvent.SilenceBroken ->
            "duration_ms=${event.silenceDurationMs},app=${event.breakingApp}"
        is PersonalityEvent.NewSenderType ->
            "type=${event.type},app=${event.app}"
        is PersonalityEvent.HighStakes ->
            "type=${event.stakesType},level=${event.stakesLevel},app=${event.app}"
        is PersonalityEvent.EmotionBlendTriggered ->
            "blend=${event.blend}"
        is PersonalityEvent.TimeTransition ->
            "hour=${event.hourOfDay},night=${event.isNight},morning=${event.isMorning}"
    }

    companion object {
        private const val DB_NAME = "observation.db"
        private const val DB_VERSION = 1

        @Volatile private var instance: ObservationDb? = null

        fun getInstance(context: Context): ObservationDb =
            instance ?: synchronized(this) {
                instance ?: ObservationDb(context.applicationContext).also { instance = it }
            }
    }
}

/** Timing result for a single DSP node */
data class NodeTiming(
    val nodeName: String,
    val durationUs: Long,
    val enabled: Boolean,
    val samplesCount: Int
)

/** Aggregated utterance statistics */
data class UtteranceStats(
    val count: Int = 0,
    val avgDspUs: Long = 0,
    val avgTextLength: Int = 0,
    val avgPcmSamples: Int = 0
)

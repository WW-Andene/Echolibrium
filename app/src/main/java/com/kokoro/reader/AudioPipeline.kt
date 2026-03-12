package com.kokoro.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Replaces the old Android TextToSpeech queue.
 *
 * Pipeline per item:
 *   1. VoiceTransform  — text → transformed text (gimmicks, commentary, stutter, etc.)
 *   2. SherpaEngine    — transformed text → PCM FloatArray
 *   3. AudioDsp        — apply breathiness to PCM
 *   4. AudioPlayer     — play via AudioTrack
 *
 * Runs on a single background thread so notifications are spoken in order.
 */
object AudioPipeline {

    private const val TAG = "AudioPipeline"

    // AudioTrack.SAMPLE_RATE_HZ_MIN / MAX are @hide in the public SDK; use literal values.
    private const val AUDIO_TRACK_MIN_SAMPLE_RATE_HZ = 4000
    private const val AUDIO_TRACK_MAX_SAMPLE_RATE_HZ = 192000
    private const val DEFAULT_PITCH = 1.0f

    data class Item(
        val rawText: String,
        val profile: VoiceProfile,
        val modulated: ModulatedVoice,
        val signal: SignalMap,
        val rules: List<Pair<String, String>>,
        val priority: Boolean = false,   // true = interrupt and jump queue (phone calls)
        val mood: MoodState? = null      // §1.0: mood state for commentary/filler decisions
    )

    private val queue = LinkedBlockingQueue<Item>()
    @Volatile private var running = false
    @Volatile private var currentTrack: AudioTrack? = null
    private val trackLock = Object()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Synchronized
    fun start(ctx: Context) {
        if (running) return
        running = true
        Thread { loop(ctx) }.apply { name = "AudioPipeline-loop"; isDaemon = true; start() }
    }

    fun stop() {
        queue.clear()
        stopCurrentPlayback()
    }

    fun shutdown() {
        running = false
        stop()
        SherpaEngine.release()
    }

    // ── Enqueue ───────────────────────────────────────────────────────────────

    fun enqueue(item: Item) {
        if (item.priority) {
            // Phone call / expiring urgency — clear queue and interrupt now
            queue.clear()
            stopCurrentPlayback()
        }
        queue.offer(item)
    }

    // ── Standalone test (works without NotificationReaderService) ────────────

    /**
     * Quick test from the UI — starts pipeline if needed and speaks the text.
     * Does NOT require the NotificationReaderService to be running.
     */
    fun testSpeak(ctx: Context, text: String, profile: VoiceProfile, rules: List<Pair<String, String>>) {
        start(ctx)
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
        enqueue(Item(
            rawText   = text,
            profile   = profile,
            modulated = modulated,
            signal    = signal,
            rules     = rules
        ))
    }

    // ── Processing loop ───────────────────────────────────────────────────────

    private fun loop(ctx: Context) {
        Log.d(TAG, "Pipeline loop started")
        while (running) {
            try {
                val item = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                processItem(ctx, item)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Pipeline loop interrupted")
                Thread.currentThread().interrupt()
                break
            } catch (e: Throwable) {
                Log.e(TAG, "Error processing item", e)
            }
        }
        Log.d(TAG, "Pipeline loop ended")
    }

    /**
     * Waits for the TTS engine to become ready (warm-up runs in background).
     * Returns true if ready, false if init failed or timed out.
     */
    private fun ensureEngineReady(ctx: Context): Boolean {
        if (SherpaEngine.isReady) return true

        // If the engine already has an error (crash loop, timeout, etc.), don't retry
        if (SherpaEngine.errorMessage != null) return false

        // Engine is warming up in the background — wait for it instead of dropping
        Log.d(TAG, "Engine not ready yet, waiting for warm-up…")
        val deadline = System.currentTimeMillis() + 50_000L // 50s max wait
        while (!SherpaEngine.isReady && SherpaEngine.errorMessage == null
            && System.currentTimeMillis() < deadline && running) {
            try { Thread.sleep(200) } catch (_: InterruptedException) { return false }
        }

        if (SherpaEngine.isReady) return true

        if (SherpaEngine.errorMessage != null) {
            Log.w(TAG, "Engine has error: ${SherpaEngine.errorMessage}")
        }
        return false
    }

    // ── Sentence splitter for chunked synthesis ────────────────────────────

    /** Minimum text length to bother chunking. Below this, single-shot is fine. */
    private const val CHUNK_MIN_LENGTH = 100

    /**
     * Splits text into sentence-like chunks at natural boundaries.
     * Keeps sentences together (split on `. `, `! `, `? `, `; `) and
     * never produces empty chunks.
     */
    private fun splitSentences(text: String): List<String> {
        if (text.length < CHUNK_MIN_LENGTH) return listOf(text)
        // Split on sentence-ending punctuation followed by whitespace
        val parts = text.split(Regex("(?<=[.!?;])\\s+")).filter { it.isNotBlank() }
        if (parts.size <= 1) return listOf(text)
        // Merge very short fragments with their predecessor to avoid choppy speech
        val merged = mutableListOf<String>()
        for (part in parts) {
            if (merged.isNotEmpty() && merged.last().length < 30) {
                merged[merged.lastIndex] = "${merged.last()} $part"
            } else {
                merged.add(part)
            }
        }
        return merged
    }

    private fun processItem(ctx: Context, item: Item) {
        // ── Step 1: Transform text ─────────────────────────────────────────
        val processed = try {
            VoiceTransform.process(
                text      = item.rawText,
                profile   = item.profile,
                modulated = item.modulated,
                signal    = item.signal,
                rules     = item.rules,
                mood      = item.mood
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error in VoiceTransform.process", e)
            item.rawText  // Fallback to raw text on transform error
        }
        if (processed.isBlank()) return

        // ── Chunked synthesis: split into sentences for lower latency ────
        val chunks = splitSentences(processed)
        if (chunks.size > 1) {
            Log.d(TAG, "Chunked synthesis: ${chunks.size} chunks from ${processed.length} chars")
        }

        for (chunk in chunks) {
            if (!running) return  // Pipeline was stopped
            if (queue.isNotEmpty() && !item.priority) return  // Newer item waiting, yield
            synthesizeAndPlay(ctx, item, chunk)
        }
    }

    /**
     * Synthesize a single text chunk and play it immediately.
     * Extracted from processItem to support both single-shot and chunked paths.
     */
    private fun synthesizeAndPlay(ctx: Context, item: Item, text: String) {
        // ── Synthesize via Piper engine ───────────────────────────────
        val voiceId = item.profile.voiceName
        val piperVoice = PiperVoiceCatalog.byId(voiceId)

        val result: Pair<FloatArray, Int>? = when {
            // Piper voice: loaded directly from assets by SherpaEngine
            piperVoice != null -> {
                if (!ensureEngineReady(ctx)) {
                    Log.w(TAG, "Engine not ready — cannot synthesize")
                    null
                } else {
                    try {
                        SherpaEngine.synthesizePiper(
                            ctx     = ctx,
                            text    = text,
                            voiceId = voiceId,
                            speed   = item.modulated.speed
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "Piper synthesis crashed for $voiceId, trying fallback", e)
                        SherpaEngine.synthesizeWithFallback(ctx, text, speed = item.modulated.speed)
                    }
                }
            }

            // Fallback: try whatever engine is available
            else -> {
                Log.w(TAG, "Voice '$voiceId' not recognized, using best available engine")
                if (!ensureEngineReady(ctx)) null
                else SherpaEngine.synthesizeWithFallback(ctx, text, speed = item.modulated.speed)
            }
        }

        result ?: return
        val (rawPcm, sampleRate) = result
        if (rawPcm.isEmpty()) return

        // ── Phonic analysis (landmark detection) ────────────────────────
        val landmarks = try {
            PhonicAnalyzer.analyze(rawPcm, sampleRate)
        } catch (e: Throwable) {
            Log.w(TAG, "PhonicAnalyzer failed, DSP will run blind", e)
            null
        }

        // ── Apply DSP (landmark-aware) ──────────────────────────────────
        val pcm = try {
            AudioDsp.apply(rawPcm, sampleRate, item.modulated, landmarks)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in AudioDsp.apply", e)
            rawPcm  // Fallback to raw PCM on DSP error
        }

        // ── Play ────────────────────────────────────────────────────────
        playPcm(pcm, sampleRate, item.modulated.pitch)
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playPcm(samples: FloatArray, sampleRate: Int, pitch: Float) {
        if (samples.isEmpty()) return
        if (sampleRate < AUDIO_TRACK_MIN_SAMPLE_RATE_HZ || sampleRate > AUDIO_TRACK_MAX_SAMPLE_RATE_HZ) {
            Log.e(TAG, "Invalid sample rate: $sampleRate — skipping playback")
            return
        }
        val safePitch = if (pitch.isNaN() || pitch.isInfinite() || pitch <= 0f) DEFAULT_PITCH else pitch
        val bufferBytes = samples.size * 4  // Float = 4 bytes

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(bufferBytes, 4096))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create AudioTrack", e)
            return
        }

        synchronized(trackLock) { currentTrack = track }

        try {
            // Write PCM data first — MODE_STATIC requires data before playback config
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)

            // Apply pitch shift via playback rate AFTER write
            // Setting playbackRate before write is unreliable on some devices in MODE_STATIC
            val shiftedRate = (sampleRate * safePitch).toInt().coerceIn(
                AUDIO_TRACK_MIN_SAMPLE_RATE_HZ,
                AUDIO_TRACK_MAX_SAMPLE_RATE_HZ
            )
            track.playbackRate = shiftedRate

            track.setNotificationMarkerPosition((samples.size - 1).coerceAtLeast(1))

            val latch = CountDownLatch(1)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { latch.countDown() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })

            track.play()
            val completed = latch.await(60, TimeUnit.SECONDS)
            track.setPlaybackPositionUpdateListener(null)
            if (!completed) Log.w(TAG, "Playback marker timeout")

        } finally {
            try { track.stop() } catch (e: Throwable) {}
            try { track.release() } catch (e: Throwable) {}
            synchronized(trackLock) { if (currentTrack === track) currentTrack = null }
        }
    }

    private fun stopCurrentPlayback() {
        synchronized(trackLock) {
            currentTrack?.let {
                try { it.stop() } catch (e: Throwable) {}
                try { it.release() } catch (e: Throwable) {}
            }
            currentTrack = null
        }
    }
}

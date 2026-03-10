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

    data class Item(
        val rawText: String,
        val profile: VoiceProfile,
        val modulated: ModulatedVoice,
        val signal: SignalMap,
        val rules: List<Pair<String, String>>,
        val priority: Boolean = false   // true = interrupt and jump queue (phone calls)
    )

    private val queue = LinkedBlockingQueue<Item>()
    @Volatile private var running = false
    @Volatile private var currentTrack: AudioTrack? = null
    private val trackLock = Object()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start(ctx: Context) {
        if (running) return
        running = true
        Thread { loop(ctx) }.apply { isDaemon = true; start() }
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
                val item = queue.poll(10, TimeUnit.MILLISECONDS) ?: continue
                processItem(ctx, item)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Pipeline loop interrupted")
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error processing item", e)
            }
        }
        Log.d(TAG, "Pipeline loop ended")
    }

    private fun processItem(ctx: Context, item: Item) {
        // ── Step 1: Transform text ─────────────────────────────────────────
        val processed = VoiceTransform.process(
            text      = item.rawText,
            profile   = item.profile,
            modulated = item.modulated,
            signal    = item.signal,
            rules     = item.rules
        )
        if (processed.isBlank()) return

        // ── Step 2: Synthesize (route to Kokoro or Piper engine) ──────────
        val voiceId = item.profile.voiceName
        val kokoroVoice = KokoroVoices.byId(voiceId)
        val piperVoice  = PiperVoiceCatalog.byId(voiceId)

        val result: Pair<FloatArray, Int>? = when {
            // Kokoro voice: use Kokoro engine with speaker ID
            kokoroVoice != null -> {
                if (!SherpaEngine.initializeKokoro(ctx)) {
                    Log.w(TAG, "Kokoro engine not ready")
                    return
                }
                SherpaEngine.synthesize(
                    text  = processed,
                    sid   = kokoroVoice.sid,
                    speed = item.modulated.speed
                )
            }

            // Piper voice: use Piper/VITS engine
            piperVoice != null && PiperVoiceManager.isVoiceReady(ctx, voiceId) -> {
                try {
                    SherpaEngine.synthesizePiper(
                        ctx     = ctx,
                        text    = processed,
                        voiceId = voiceId,
                        speed   = item.modulated.speed
                    )
                } catch (e: Throwable) {
                    // Catch native crashes from Piper engine and fall back to Kokoro
                    Log.e(TAG, "Piper synthesis crashed for $voiceId, falling back to Kokoro", e)
                    if (!SherpaEngine.initializeKokoro(ctx)) return
                    SherpaEngine.synthesize(
                        text  = processed,
                        sid   = KokoroVoices.default().sid,
                        speed = item.modulated.speed
                    )
                }
            }

            // Fallback: default Kokoro voice
            else -> {
                if (!SherpaEngine.initializeKokoro(ctx)) {
                    Log.w(TAG, "Kokoro engine not ready (fallback)")
                    return
                }
                Log.w(TAG, "Voice '$voiceId' not available, using default Kokoro")
                SherpaEngine.synthesize(
                    text  = processed,
                    sid   = KokoroVoices.default().sid,
                    speed = item.modulated.speed
                )
            }
        }

        result ?: return
        val (rawPcm, sampleRate) = result

        // ── Step 3: Apply DSP ─────────────────────────────────────────────
        val pcm = AudioDsp.apply(rawPcm, sampleRate, item.modulated)

        // ── Step 4: Play ──────────────────────────────────────────────────
        playPcm(pcm, sampleRate, item.modulated.pitch)
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playPcm(samples: FloatArray, sampleRate: Int, pitch: Float) {
        val bufferBytes = samples.size * 4  // Float = 4 bytes

        val track = AudioTrack.Builder()
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

        synchronized(trackLock) { currentTrack = track }

        try {
            // Write PCM data first — MODE_STATIC requires data before playback config
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)

            // Apply pitch shift via playback rate AFTER write
            // Setting playbackRate before write is unreliable on some devices in MODE_STATIC
            val shiftedRate = (sampleRate * pitch).toInt().coerceIn(
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
            if (!completed) Log.w(TAG, "Playback marker timeout")

        } finally {
            try { track.stop() } catch (e: Exception) {}
            try { track.release() } catch (e: Exception) {}
            synchronized(trackLock) { if (currentTrack === track) currentTrack = null }
        }
    }

    private fun stopCurrentPlayback() {
        synchronized(trackLock) {
            currentTrack?.let {
                try { it.stop() } catch (e: Exception) {}
                try { it.release() } catch (e: Exception) {}
            }
            currentTrack = null
        }
    }
}

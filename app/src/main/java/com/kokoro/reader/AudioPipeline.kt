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

    // ── Processing loop ───────────────────────────────────────────────────────

    private fun loop(ctx: Context) {
        Log.d(TAG, "Pipeline loop started")
        while (running) {
            val item = queue.poll(1, TimeUnit.SECONDS) ?: continue
            try {
                processItem(ctx, item)
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

        // ── Step 2: Initialize engine (if not already) ────────────────────
        if (!SherpaEngine.initialize(ctx)) {
            Log.w(TAG, "SherpaEngine not ready — model may still be downloading")
            return
        }

        // ── Step 3: Synthesize ────────────────────────────────────────────
        val voiceId = item.profile.voiceName
        val voice   = KokoroVoices.byId(voiceId) ?: KokoroVoices.default()
        val result  = SherpaEngine.synthesize(
            text  = processed,
            sid   = voice.sid,
            speed = item.modulated.speed
        ) ?: return

        val (rawPcm, sampleRate) = result

        // ── Step 4: Apply DSP ─────────────────────────────────────────────
        val pcm = AudioDsp.apply(rawPcm, sampleRate, item.modulated)

        // ── Step 5: Play ──────────────────────────────────────────────────
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
            // Apply pitch shift via playback rate
            // AudioTrack can adjust playback rate to shift pitch within limits
            val shiftedRate = (sampleRate * pitch).toInt().coerceIn(
                AudioTrack.SAMPLE_RATE_HZ_MIN,
                AudioTrack.SAMPLE_RATE_HZ_MAX
            )
            track.playbackRate = shiftedRate

            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
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

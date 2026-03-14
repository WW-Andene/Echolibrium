package com.echolibrium.kyokan

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
 * Audio pipeline: text → TTS engine → pitch shift → playback.
 *
 * Engine priority:
 *   1. Orpheus (cloud via DeepInfra) — if API key configured and voice is cloud
 *   2. Kokoro (local via SherpaEngine) — offline fallback
 *   3. Piper (local via SherpaEngine) — offline fallback
 *
 * Runs on a single background thread so notifications are spoken in order.
 */
object AudioPipeline {

    private const val TAG = "AudioPipeline"

    private const val AUDIO_TRACK_MIN_SAMPLE_RATE_HZ = 4000
    private const val AUDIO_TRACK_MAX_SAMPLE_RATE_HZ = 192000

    data class Item(
        val text: String,
        val voiceId: String,
        val pitch: Float = 1.0f,
        val speed: Float = 1.0f,
        val priority: Boolean = false,
        val language: String? = null
    )

    private val queue = LinkedBlockingQueue<Item>()
    @Volatile private var running = false
    private var pipelineThread: Thread? = null
    @Volatile private var currentTrack: AudioTrack? = null
    private val trackLock = Object()

    /** Called on the pipeline thread when synthesis fails — use Handler to post to UI. */
    @Volatile var onSynthesisError: ((voiceId: String, reason: String) -> Unit)? = null

    // Crossfade state
    private const val CROSSFADE_MS = 40
    private var prevTail: FloatArray? = null
    private var prevSampleRate: Int = 0

    // ── Lifecycle ───────────────────────────────────────────────────────────

    fun start(ctx: Context) {
        if (running && pipelineThread?.isAlive == true) return
        running = true
        initCloudTts(ctx)

        pipelineThread = Thread({ loop(ctx) }, "AudioPipelineLoop").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Configure CloudTtsEngine with proxy URL and/or DeepInfra API key.
     * Proxy (Cloudflare Worker) takes priority — when set, no local API key is needed.
     * API key priority: 1) EncryptedSharedPreferences (in-app entry), 2) BuildConfig (compile-time).
     */
    private fun initCloudTts(ctx: Context) {
        val proxyUrl = BuildConfig.PROXY_BASE_URL

        val userKey = try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(ctx)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                ctx, "kyokan_secure_prefs", masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            securePrefs.getString("deepinfra_api_key", "") ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Could not read secure prefs for API key", e)
            ""
        }
        val key = userKey.ifBlank { BuildConfig.DEEPINFRA_API_KEY }
        CloudTtsEngine.configure(key, proxyUrl)
    }

    fun stop() {
        queue.clear()
        stopCurrentPlayback()
        prevTail = null
    }

    fun shutdown() {
        running = false
        stop()
        SherpaEngine.release()
    }

    // ── Enqueue ─────────────────────────────────────────────────────────────

    fun enqueue(item: Item, maxQueue: Int = 0) {
        if (item.priority) {
            queue.clear()
            stopCurrentPlayback()
        }
        if (maxQueue > 0) {
            while (queue.size >= maxQueue) queue.poll()
        }
        queue.offer(item)
    }

    // ── Processing loop ─────────────────────────────────────────────────────

    private fun loop(ctx: Context) {
        Log.d(TAG, "Pipeline loop started")
        while (running) {
            try {
                val item = queue.poll(1, TimeUnit.SECONDS) ?: continue
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
        if (item.text.isBlank()) return

        val voiceId = item.voiceId

        // ── Synthesize ──────────────────────────────────────────────────
        val result: Pair<FloatArray, Int> = if (VoiceRegistry.isCloud(voiceId)) {
            // Try Orpheus cloud
            synthesizeWithCloud(item.text, item)
                ?: run {
                    Log.w(TAG, "Cloud voice $voiceId unavailable, no fallback for cloud voices")
                    onSynthesisError?.invoke(voiceId, "Cloud voice failed — check internet or proxy setup")
                    return
                }
        } else if (PiperVoices.isPiperVoice(voiceId)) {
            synthesizeWithPiper(ctx, voiceId, item.text, item.speed)
                ?: run {
                    onSynthesisError?.invoke(voiceId, "Piper voice not downloaded yet")
                    return
                }
        } else {
            synthesizeWithKokoro(ctx, voiceId, item.text, item.speed)
                ?: run {
                    onSynthesisError?.invoke(voiceId, "Kokoro model not ready")
                    return
                }
        }

        val (pcm, sampleRate) = result

        // ── Play ────────────────────────────────────────────────────────
        playPcm(pcm, sampleRate)
    }

    // ── Engine methods ──────────────────────────────────────────────────────

    private fun synthesizeWithCloud(text: String, item: Item): Pair<FloatArray, Int>? {
        if (!CloudTtsEngine.isEnabled()) return null

        val cloudVoice = VoiceRegistry.cloudVoiceById(item.voiceId)
        val voice = cloudVoice?.apiVoiceName

        return CloudTtsEngine.synthesize(
            text = text,
            voice = voice,
            language = item.language
        )
    }

    private fun synthesizeWithKokoro(
        ctx: Context, voiceId: String, text: String, speed: Float
    ): Pair<FloatArray, Int>? {
        if (!SherpaEngine.initialize(ctx)) {
            Log.w(TAG, "Kokoro not ready — model may still be downloading")
            return null
        }
        val voice = KokoroVoices.byId(voiceId) ?: KokoroVoices.default()
        return SherpaEngine.synthesize(text = text, sid = voice.sid, speed = speed)
    }

    private fun synthesizeWithPiper(
        ctx: Context, voiceId: String, text: String, speed: Float
    ): Pair<FloatArray, Int>? {
        if (!SherpaEngine.initPiper(ctx, voiceId)) {
            Log.w(TAG, "Piper voice $voiceId not ready — may still be downloading")
            return null
        }
        return SherpaEngine.synthesizePiper(voiceId = voiceId, text = text, speed = speed)
    }

    // ── Playback ────────────────────────────────────────────────────────────

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val pcm = applyCrossfade(samples, sampleRate)
        if (pcm.isEmpty()) return

        val safeRate = sampleRate.coerceIn(
            AUDIO_TRACK_MIN_SAMPLE_RATE_HZ, AUDIO_TRACK_MAX_SAMPLE_RATE_HZ
        )
        val bufferBytes = pcm.size * 4

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
                        .setSampleRate(safeRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(bufferBytes, 4096))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack creation failed", e)
            return
        }

        synchronized(trackLock) { currentTrack = track }

        try {
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            track.setNotificationMarkerPosition((pcm.size - 1).coerceAtLeast(1))

            val latch = CountDownLatch(1)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { latch.countDown() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })

            track.play()
            val durationMs = (pcm.size * 1000L / safeRate) + 2000L
            val timeoutMs = durationMs.coerceAtMost(30_000L)
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Playback marker timeout after ${timeoutMs}ms")
            }
        } finally {
            try { track.stop() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
            synchronized(trackLock) { if (currentTrack === track) currentTrack = null }
        }
    }

    private fun applyCrossfade(samples: FloatArray, sampleRate: Int): FloatArray {
        val tail = prevTail
        val tailRate = prevSampleRate
        val result = samples.copyOf()

        val fadeSamples = (sampleRate * CROSSFADE_MS / 1000).coerceAtMost(samples.size / 4)
        if (fadeSamples > 0) {
            val start = (samples.size - fadeSamples).coerceAtLeast(0)
            prevTail = samples.sliceArray(start until samples.size)
            prevSampleRate = sampleRate
        }

        if (tail != null && tailRate == sampleRate && tail.isNotEmpty()) {
            val crossLen = minOf(tail.size, fadeSamples, result.size)
            for (i in 0 until crossLen) {
                val t = i.toFloat() / crossLen
                result[i] = tail[tail.size - crossLen + i] * (1f - t) + result[i] * t
            }
        } else if (tail == null) {
            val fadeIn = (sampleRate * 0.005f).toInt().coerceAtMost(result.size)
            for (i in 0 until fadeIn) {
                result[i] *= i.toFloat() / fadeIn
            }
        }

        return result
    }

    private fun stopCurrentPlayback() {
        synchronized(trackLock) {
            currentTrack?.let {
                try { it.stop() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
            currentTrack = null
        }
    }
}

package com.echolibrium.kyokan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
class AudioPipeline(
    private val cloudTtsEngine: CloudTtsEngine,
    private val sherpaEngine: SherpaEngine
) {

    companion object {
        private const val TAG = "AudioPipeline"
        private const val AUDIO_TRACK_MIN_SAMPLE_RATE_HZ = 4000
        private const val AUDIO_TRACK_MAX_SAMPLE_RATE_HZ = 192000
        private const val CROSSFADE_MS = 40
        /** L9: Maximum prevTail samples to retain for crossfade (~8KB at 48kHz). */
        private const val MAX_PREV_TAIL_SAMPLES = 2048
        /** L10: PCM size threshold (256KB) above which MODE_STREAM is used instead of MODE_STATIC. */
        private const val STREAM_MODE_THRESHOLD_BYTES = 256 * 1024
    }

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

    // D-01: Audio focus — prevents speech from overlapping calls, music, navigation
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    /** Called on the pipeline thread when synthesis fails — use Handler to post to UI. */
    private val synthesisErrorListeners = mutableListOf<(voiceId: String, reason: String) -> Unit>()
    private val listenersLock = Object()

    fun addSynthesisErrorListener(listener: (voiceId: String, reason: String) -> Unit) {
        synchronized(listenersLock) { synthesisErrorListeners.add(listener) }
    }

    fun removeSynthesisErrorListener(listener: (voiceId: String, reason: String) -> Unit) {
        synchronized(listenersLock) { synthesisErrorListeners.remove(listener) }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun notifySynthesisError(voiceId: String, reason: String) {
        val snapshot = synchronized(listenersLock) { synthesisErrorListeners.toList() }
        mainHandler.post { snapshot.forEach { it(voiceId, reason) } }
        // F-06: Log for background visibility
        Log.w(TAG, "Synthesis error for $voiceId: $reason")
        lastError = reason
    }

    /** F-06: Last synthesis error — visible via TtsAliveService notification update. */
    @Volatile var lastError: String? = null
        private set

    fun clearError() { lastError = null }

    // Crossfade state
    private var prevTail: FloatArray? = null
    private var prevSampleRate: Int = 0

    // ── Lifecycle ───────────────────────────────────────────────────────────

    fun start(ctx: Context) {
        if (running && pipelineThread?.isAlive == true) return
        running = true
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
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
            SecureKeyStore.getDeepInfraKey(ctx) ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Could not read secure prefs for API key", e)
            ""
        }
        val key = userKey.ifBlank { BuildConfig.DEEPINFRA_API_KEY }
        cloudTtsEngine.configure(key, proxyUrl, ctx.container.repo)
    }

    fun stop() {
        queue.clear()
        stopCurrentPlayback()
        prevTail = null
    }

    fun shutdown() {
        running = false
        stop()
        sherpaEngine.release()
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
            // Try Orpheus cloud, fall back to Kokoro default if connectivity lost (K-02)
            synthesizeWithCloud(item.text, item)
                ?: run {
                    Log.w(TAG, "Cloud voice $voiceId failed, attempting local Kokoro fallback")
                    notifySynthesisError(voiceId, "Cloud failed — falling back to local voice")
                    synthesizeWithKokoro(ctx, KokoroVoices.default().id, item.text, item.speed)
                }
                ?: run {
                    notifySynthesisError(voiceId, "Cloud and local fallback both failed")
                    return
                }
        } else if (VoiceRegistry.isPiper(voiceId)) {
            synthesizeWithPiper(ctx, voiceId, item.text, item.speed)
                ?: run {
                    notifySynthesisError(voiceId, "Piper voice not downloaded yet")
                    return
                }
        } else {
            synthesizeWithKokoro(ctx, voiceId, item.text, item.speed)
                ?: run {
                    notifySynthesisError(voiceId, "Kokoro model not ready")
                    return
                }
        }

        val (pcm, sampleRate) = result

        // ── Play ────────────────────────────────────────────────────────
        if (!playPcm(pcm, sampleRate)) {
            // Bug 6: Audio focus denied (e.g. phone call active) — notify so
            // TtsAliveService notification and UI listeners can show feedback
            notifySynthesisError(voiceId, "Audio busy — notification skipped")
        }
    }

    // ── Engine methods ──────────────────────────────────────────────────────

    private fun synthesizeWithCloud(text: String, item: Item): Pair<FloatArray, Int>? {
        if (!cloudTtsEngine.isEnabled()) return null

        val cloudVoice = VoiceRegistry.cloudVoiceById(item.voiceId)
        val voice = cloudVoice?.apiVoiceName

        return cloudTtsEngine.synthesize(
            text = text,
            voice = voice,
            language = item.language
        )
    }

    private fun synthesizeWithKokoro(
        ctx: Context, voiceId: String, text: String, speed: Float
    ): Pair<FloatArray, Int>? {
        if (!sherpaEngine.initialize(ctx)) {
            Log.w(TAG, "Kokoro not ready — model may still be downloading")
            return null
        }
        val voice = KokoroVoices.byId(voiceId) ?: KokoroVoices.default()
        return sherpaEngine.synthesize(text = text, sid = voice.sid, speed = speed)
    }

    private fun synthesizeWithPiper(
        ctx: Context, voiceId: String, text: String, speed: Float
    ): Pair<FloatArray, Int>? {
        if (!sherpaEngine.initPiper(ctx, voiceId)) {
            Log.w(TAG, "Piper voice $voiceId not ready — may still be downloading")
            return null
        }
        return sherpaEngine.synthesizePiper(voiceId = voiceId, text = text, speed = speed)
    }

    // ── Playback ────────────────────────────────────────────────────────────

    /** D-01: Request transient audio focus — returns true if granted. */
    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true // proceed without focus if no manager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { /* no-op for transient */ }
            .build()
        focusRequest = request
        return am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** D-01: Release audio focus after playback. */
    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        val request = focusRequest ?: return
        am.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    /** @return true if playback completed (or at least started), false if audio focus was denied */
    private fun playPcm(samples: FloatArray, sampleRate: Int): Boolean {
        val pcm = applyCrossfade(samples, sampleRate)
        if (pcm.isEmpty()) return true // empty is not a focus error

        // D-01: Request audio focus before playback — skip if denied (e.g. during phone call)
        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus denied — skipping playback")
            return false
        }

        val safeRate = sampleRate.coerceIn(
            AUDIO_TRACK_MIN_SAMPLE_RATE_HZ, AUDIO_TRACK_MAX_SAMPLE_RATE_HZ
        )
        val bufferBytes = pcm.size * 4
        val useStream = bufferBytes > STREAM_MODE_THRESHOLD_BYTES

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
                .setBufferSizeInBytes(if (useStream) maxOf(safeRate * 4, 4096) else maxOf(bufferBytes, 4096))
                .setTransferMode(if (useStream) AudioTrack.MODE_STREAM else AudioTrack.MODE_STATIC)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack creation failed", e)
            return true // not a focus denial — don't trigger "audio busy" error
        }

        synchronized(trackLock) { currentTrack = track }

        try {
            if (useStream) {
                track.play()
                var offset = 0
                val chunkSize = safeRate // ~1 second of audio per chunk
                while (offset < pcm.size && running) {
                    val len = minOf(chunkSize, pcm.size - offset)
                    val written = track.write(pcm, offset, len, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) { Log.e(TAG, "Stream write error: $written"); break }
                    offset += written
                }
            } else {
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
            }
        } finally {
            try { track.stop() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
            synchronized(trackLock) { if (currentTrack === track) currentTrack = null }
            abandonAudioFocus()
        }
        return true
    }

    private fun applyCrossfade(samples: FloatArray, sampleRate: Int): FloatArray {
        val tail = prevTail
        val tailRate = prevSampleRate
        val result = samples.copyOf()

        val fadeSamples = (sampleRate * CROSSFADE_MS / 1000).coerceAtMost(samples.size / 4)

        // A-03: Apply previous crossfade BEFORE saving new tail to avoid compounding artifacts
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

        // Save tail for next crossfade after applying current one
        if (fadeSamples > 0) {
            val start = (samples.size - fadeSamples).coerceAtLeast(0)
            val tailSize = (samples.size - start).coerceAtMost(MAX_PREV_TAIL_SAMPLES)
            prevTail = result.sliceArray((result.size - tailSize) until result.size)
            prevSampleRate = sampleRate
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

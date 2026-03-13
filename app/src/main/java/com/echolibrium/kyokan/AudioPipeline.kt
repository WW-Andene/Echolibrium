package com.echolibrium.kyokan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.io.File
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
        val priority: Boolean = false,  // true = interrupt and jump queue (phone calls)
        val translated: Boolean = false // true = text was machine-translated, skip text effects
    )

    private val queue = LinkedBlockingQueue<Item>()
    @Volatile private var running = false
    @Volatile private var currentTrack: AudioTrack? = null
    private val trackLock = Object()

    // Crossfade state — store tail of previous playback for smooth transitions
    private const val CROSSFADE_MS = 40  // 40ms crossfade between clips
    private var prevTail: FloatArray? = null
    private var prevSampleRate: Int = 0

    // ── Direct ORT engine (Mirror Project Phase 2) ──────────────────────────
    private var directOrtEngine: DirectOrtEngine? = null
    private var yatagamiSynthesizer: YatagamiSynthesizer? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start(ctx: Context) {
        if (running) return
        running = true
        initDirectOrt(ctx)
        Thread { loop(ctx) }.apply { isDaemon = true; start() }
    }

    /**
     * Initialize DirectOrtEngine + YatagamiSynthesizer for pre-synthesis emotional control.
     * Loads Kokoro model via direct ONNX Runtime if the model is already downloaded.
     * If not ready yet (model still downloading), this is a no-op — SherpaEngine handles it.
     */
    private fun initDirectOrt(ctx: Context) {
        if (!VoiceDownloadManager.isModelReady(ctx)) {
            Log.d(TAG, "DirectOrt: model not ready yet, will use SherpaEngine only")
            return
        }
        try {
            val modelDir = VoiceDownloadManager.getModelDir(ctx)
            val engine = DirectOrtEngine(ctx)
            val ok = engine.initialize(
                kokoroModelPath = File(modelDir, "model.onnx").absolutePath,
                voicesBinPath = File(modelDir, "voices.bin").absolutePath,
                numThreads = 2
            )
            if (ok) {
                directOrtEngine = engine
                yatagamiSynthesizer = YatagamiSynthesizer(engine)
                Log.i(TAG, "DirectOrt + YatagamiSynthesizer initialized")
            } else {
                Log.w(TAG, "DirectOrt init returned false, using SherpaEngine only")
                engine.cleanup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "DirectOrt init failed, using SherpaEngine only", e)
        }
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
        directOrtEngine?.cleanup()
        directOrtEngine = null
        yatagamiSynthesizer = null
    }

    // ── Enqueue ───────────────────────────────────────────────────────────────

    fun enqueue(item: Item, maxQueue: Int = 0) {
        if (item.priority) {
            // Phone call / expiring urgency — clear queue and interrupt now
            queue.clear()
            stopCurrentPlayback()
        }
        // Respect max queue size — drop oldest if full
        if (maxQueue > 0) {
            while (queue.size >= maxQueue) queue.poll()
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
        // When text was machine-translated, only apply wording rules (clean substitutions).
        // Skip gimmicks, breathiness, stutter, intonation — they insert garbage characters
        // (like "h", "Ha") into the translated text that TTS reads as literal sounds.
        val processed = if (item.translated) {
            VoiceTransform.applyWordingRules(item.rawText, item.rules)
        } else {
            VoiceTransform.process(
                text      = item.rawText,
                profile   = item.profile,
                modulated = item.modulated,
                signal    = item.signal,
                rules     = item.rules
            )
        }
        if (processed.isBlank()) return

        // ── Step 2+3: Route to correct engine and synthesize ────────────
        // Try YatagamiSynthesizer (direct ORT + style sculpting) first,
        // fall back to SherpaEngine if Yatagami isn't ready or fails.
        // NOTE: Yatagami requires tokenized input (LongArray of phoneme IDs).
        // Until Phase 4 adds the eSpeak-NG tokenizer, we pass null tokenIds
        // and Yatagami returns null → SherpaEngine handles all synthesis.
        val voiceId = item.profile.voiceName
        val yatagamiResult = synthesizeWithYatagami(item)
        val result = if (yatagamiResult != null) {
            Pair(yatagamiResult.pcm, yatagamiResult.sampleRate)
        } else if (PiperVoices.isPiperVoice(voiceId)) {
            synthesizeWithPiper(ctx, voiceId, processed, item.modulated.speed)
        } else {
            synthesizeWithKokoro(ctx, voiceId, processed, item.modulated.speed)
        } ?: return

        val (rawPcm, sampleRate) = result

        // ── Step 4: Analyze PCM for context-aware DSP ─────────────────────
        val landmarks = try {
            PhonicAnalyzer.analyze(rawPcm, sampleRate)
        } catch (e: Exception) {
            Log.w(TAG, "PhonicAnalyzer failed, proceeding without landmarks", e)
            null
        }

        // ── Step 5: Apply DSP ─────────────────────────────────────────────
        val dspPcm = AudioDsp.apply(rawPcm, sampleRate, item.modulated, landmarks)

        // ── Step 6: Pitch shift (resampling-based, preserves duration) ───
        val pcm = AudioDsp.pitchShift(dspPcm, item.modulated.pitch)

        // ── Step 7: Play at native sample rate (no playbackRate hack) ────
        playPcm(pcm, sampleRate)
    }

    /**
     * Attempt synthesis via YatagamiSynthesizer (direct ORT + StyleSculptor/ScaleMapper).
     *
     * Returns null if:
     *   - DirectOrtEngine not initialized (model not downloaded at start time)
     *   - No tokenizer available yet (Phase 4 — eSpeak-NG phonemization)
     *   - Direct ORT synthesis fails for any reason
     *
     * When null is returned, the caller falls through to SherpaEngine.
     */
    private fun synthesizeWithYatagami(item: Item): YatagamiSynthesizer.SynthResult? {
        val yatagami = yatagamiSynthesizer ?: return null

        // Phase 4 gate: tokenizer not yet implemented.
        // When added, this will be:
        //   val tokenIds = tokenizer.tokenize(text, voiceId) ?: return null
        // For now, return null to always fall through to SherpaEngine.
        val tokenIds: LongArray? = null  // TODO: Phase 4 — eSpeak-NG tokenizer

        if (tokenIds == null) return null

        return try {
            yatagami.synthesize(
                tokenIds = tokenIds,
                modulated = item.modulated,
                signal = item.signal,
                profile = item.profile,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Yatagami synthesis error, falling back to SherpaEngine", e)
            null
        }
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

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        // Apply crossfade from previous clip's tail for smooth transitions
        val pcm = applyCrossfade(samples, sampleRate)

        val bufferBytes = pcm.size * 4  // Float = 4 bytes

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
            // Pitch is now applied via AudioDsp.pitchShift() before playback
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            track.setNotificationMarkerPosition((pcm.size - 1).coerceAtLeast(1))

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

    /**
     * Crossfade between previous clip's tail and current clip's head.
     * Prevents abrupt transitions when slider parameters change between utterances.
     */
    private fun applyCrossfade(samples: FloatArray, sampleRate: Int): FloatArray {
        val tail = prevTail
        val result = samples.copyOf()

        // Store this clip's tail for next crossfade
        val fadeSamples = (sampleRate * CROSSFADE_MS / 1000).coerceAtMost(samples.size / 4)
        if (fadeSamples > 0) {
            val start = (samples.size - fadeSamples).coerceAtLeast(0)
            prevTail = samples.sliceArray(start until samples.size)
            prevSampleRate = sampleRate
        }

        // Crossfade with previous clip's tail if available and compatible
        if (tail != null && prevSampleRate == sampleRate && tail.isNotEmpty()) {
            val crossLen = minOf(tail.size, fadeSamples, result.size)
            for (i in 0 until crossLen) {
                val t = i.toFloat() / crossLen  // 0→1
                // Fade out previous tail, fade in current head
                result[i] = tail[tail.size - crossLen + i] * (1f - t) + result[i] * t
            }
        } else {
            // No previous clip — apply a short fade-in to avoid click
            val fadeIn = (sampleRate * 0.005f).toInt().coerceAtMost(result.size)  // 5ms
            for (i in 0 until fadeIn) {
                result[i] *= i.toFloat() / fadeIn
            }
        }

        return result
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

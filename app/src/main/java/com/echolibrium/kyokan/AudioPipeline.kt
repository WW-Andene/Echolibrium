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
        val translated: Boolean = false, // true = text was machine-translated, skip text effects
        val language: String? = null     // detected content language (ISO 639-1), for cloud TTS routing
    )

    private val queue = LinkedBlockingQueue<Item>()
    @Volatile private var running = false
    private var pipelineThread: Thread? = null
    @Volatile private var currentTrack: AudioTrack? = null
    private val trackLock = Object()

    // Crossfade state — store tail of previous playback for smooth transitions
    private const val CROSSFADE_MS = 40  // 40ms crossfade between clips
    private var prevTail: FloatArray? = null
    private var prevSampleRate: Int = 0

    // ── Direct ORT engine (Mirror Project Phase 2-4) ────────────────────────
    @Volatile private var directOrtEngine: DirectOrtEngine? = null
    @Volatile private var yatagamiSynthesizer: YatagamiSynthesizer? = null
    @Volatile private var kokoroTokenizer: PhonemeTokenizer? = null

    // ── Modular DSP pipeline (Section 6.2) ────────────────────────────────
    private val dspChain: DspChain = DspChain.default()

    // ── Observation layer (P3 #15) ────────────────────────────────────────
    private var observationDb: ObservationDb? = null
    private var utteranceCounter = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start(ctx: Context) {
        if (running && pipelineThread?.isAlive == true) return
        running = true
        observationDb = ObservationDb.getInstance(ctx)
        ParameterTrajectory.loadConfig(ctx)
        initCloudTts(ctx)

        // Move model loading to background — avoids ANR in NotificationReaderService.onCreate()
        // Pipeline loop starts immediately using SherpaEngine fallback until DirectOrt is ready
        Thread({
            try {
                initDirectOrt(ctx)
                Log.i(TAG, "DirectOrt initialized on background thread")
            } catch (e: Exception) {
                Log.e(TAG, "DirectOrt init failed", e)
            }
        }, "DirectOrtInit").start()

        pipelineThread = Thread({ loop(ctx) }, "AudioPipelineLoop").apply {
            isDaemon = true
            start()
        }
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
                val yatagami = YatagamiSynthesizer(engine)
                // Apply persisted engine mode preference (Phase 5 A/B toggle)
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
                yatagami.directOrtEnabled = prefs.getBoolean("engine_sculpted_mode", true)
                yatagamiSynthesizer = yatagami
                kokoroTokenizer = PhonemeTokenizer.loadKokoro(ctx)
                // Phase 6: Calibrate breath direction from real voice data
                try {
                    StyleSculptor.calibrateBreathDirection(engine)
                    Log.i(TAG, "StyleSculptor breath direction calibrated")
                } catch (e: Exception) {
                    Log.w(TAG, "Breath direction calibration failed, using heuristic", e)
                }
                Log.i(TAG, "DirectOrt + YatagamiSynthesizer + tokenizer initialized")
            } else {
                Log.w(TAG, "DirectOrt init returned false, using SherpaEngine only")
                engine.cleanup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "DirectOrt init failed, using SherpaEngine only", e)
        }
    }

    /**
     * Configure CloudTtsEngine with the DeepInfra API key from BuildConfig.
     * Key is injected at compile time from local.properties (gitignored).
     * If no key is set, cloud TTS stays disabled and local engines are used.
     */
    private fun initCloudTts(ctx: Context) {
        val key = BuildConfig.DEEPINFRA_API_KEY
        CloudTtsEngine.configure(key, observationDb)
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
        kokoroTokenizer = null
    }

    // ── Engine Mode (Phase 5 A/B toggle) ─────────────────────────────────────

    /**
     * Toggle between Sculpted mode (YatagamiSynthesizer + reduced DSP)
     * and Classic mode (SherpaEngine + full DSP chain).
     */
    fun setSculptedMode(enabled: Boolean) {
        val synth = yatagamiSynthesizer ?: return
        synth.directOrtEnabled = enabled
        Log.i(TAG, "Engine mode: ${if (enabled) "Sculpted" else "Classic"}")
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
            try {
                val item = queue.poll(1, TimeUnit.SECONDS) ?: continue
                processItem(ctx, item)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Pipeline loop interrupted")
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error processing item", e)
                // Continue the loop — don't let one bad item kill everything
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
        // Priority order:
        //   1. Cloud TTS (DeepInfra) — highest quality, auto-routes to best engine
        //   2. YatagamiSynthesizer  — local ORT + style sculpting
        //   3. SherpaEngine         — local Kokoro/Piper fallback
        val voiceId = item.profile.voiceName

        // Try cloud TTS first — routes to Orpheus/Chatterbox/Qwen3 based on content
        val cloudResult = synthesizeWithCloud(processed, item)
        val yatagamiResult: YatagamiSynthesizer.SynthResult?

        val result = if (cloudResult != null) {
            yatagamiResult = null
            cloudResult
        } else {
            // Fall back to local engines
            yatagamiResult = synthesizeWithYatagami(ctx, item, processed)
            if (yatagamiResult != null) {
                Pair(yatagamiResult.pcm, yatagamiResult.sampleRate)
            } else if (PiperVoices.isPiperVoice(voiceId)) {
                synthesizeWithPiper(ctx, voiceId, processed, item.modulated.speed)
            } else {
                synthesizeWithKokoro(ctx, voiceId, processed, item.modulated.speed)
            }
        } ?: return

        val (rawPcm, sampleRate) = result

        // ── Step 4: Analyze PCM for context-aware DSP ─────────────────────
        val landmarks = try {
            PhonicAnalyzer.analyze(rawPcm, sampleRate)
        } catch (e: Exception) {
            Log.w(TAG, "PhonicAnalyzer failed, proceeding without landmarks", e)
            null
        }

        // ── Step 5: Apply DSP via modular DspChain (profiled) ─────────────
        // When using the sculpted path, reduce DSP intensity for breath and jitter
        // since the emotional parameters are already baked into the style vector.
        val dspModulated = if (yatagamiResult?.engine == YatagamiSynthesizer.EngineType.KOKORO_SCULPTED) {
            item.modulated.copy(
                breathIntensity = (item.modulated.breathIntensity * 0.3f).toInt(),  // 70% already in style
                jitterAmount = item.modulated.jitterAmount * 0.12f                  // 88% already in style
            )
        } else {
            item.modulated
        }

        val utteranceId = "u_${System.currentTimeMillis()}_${utteranceCounter++}"
        val trajectory = ParameterTrajectory.fromSignal(item.signal, dspModulated)
        val utteranceCtx = UtteranceContext(
            modulated = dspModulated,
            signal = item.signal,
            landmarks = landmarks,
            sampleRate = sampleRate,
            trajectory = trajectory
        )
        val (dspPcm, timings) = dspChain.processProfiled(rawPcm, utteranceCtx)

        // Log DSP timing + utterance metadata to observation DB
        val db = observationDb
        if (db != null) {
            try {
                db.logChainTiming(utteranceId, timings)
                val totalDspUs = timings.sumOf { it.durationUs }
                db.logUtterance(
                    utteranceId = utteranceId,
                    voiceId = voiceId,
                    textLength = processed.length,
                    totalDspUs = totalDspUs,
                    sampleRate = sampleRate,
                    pcmSamples = dspPcm.size,
                    emotionBlend = item.signal.emotionBlend,
                    floodTier = item.signal.floodTier,
                    moodValence = null,
                    moodArousal = null
                )
            } catch (e: Exception) {
                Log.w(TAG, "Observation logging failed", e)
            }
        }

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
    private fun synthesizeWithYatagami(
        ctx: Context, item: Item, text: String
    ): YatagamiSynthesizer.SynthResult? {
        val yatagami = yatagamiSynthesizer ?: return null
        val engine = directOrtEngine ?: return null

        // Phase 3: Lazy-load Piper voice into DirectOrtEngine if needed.
        val voiceId = item.profile.voiceName
        val isPiper = PiperVoices.isPiperVoice(voiceId)
        if (isPiper) {
            engine.initPiper(ctx, voiceId)
        }

        // Phase 4: Tokenize text → phoneme IDs via PhonemeTokenizer.
        // Uses rule-based English G2P. Returns null for text it can't handle,
        // which falls through to SherpaEngine (eSpeak-NG phonemization).
        val tokenizer = kokoroTokenizer ?: return null
        val tokenIds = tokenizer.tokenize(text)
        if (tokenIds == null) {
            Log.d(TAG, "Tokenizer returned null, falling back to SherpaEngine")
            return null
        }

        // Enrich signal with token count for style vector lookup (Phase 3)
        val enrichedSignal = item.signal.copy(temporal = item.signal.temporal.copy(tokenCount = tokenIds.size))

        return try {
            yatagami.synthesize(
                tokenIds = tokenIds,
                modulated = item.modulated,
                signal = enrichedSignal,
                profile = item.profile,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Yatagami synthesis error, falling back to SherpaEngine", e)
            null
        }
    }

    /**
     * Attempt synthesis via DeepInfra cloud TTS.
     * The router selects Orpheus/Chatterbox/Qwen3 based on priority, content tags,
     * and emotion signals. Enriches text with engine-specific emotion tags.
     * Returns null if cloud TTS is disabled or the request fails — falls through to local.
     */
    private fun synthesizeWithCloud(text: String, item: Item): Pair<FloatArray, Int>? {
        if (!CloudTtsEngine.isEnabled()) return null

        // Generate Qwen3 voice instruction from mood/signal analysis
        val voiceInstruction = CloudTtsEngine.voiceInstructionFromSignal(item.signal)

        val engine = CloudTtsEngine.selectEngine(
            text = text,
            priority = item.priority,
            voiceInstruction = voiceInstruction,
            language = item.language
        )

        // Enrich text with engine-specific emotion tags based on signal analysis
        val enrichedText = when (engine) {
            CloudTtsEngine.Engine.ORPHEUS -> CloudTtsEngine.enrichTextForOrpheus(text, item.signal)
            CloudTtsEngine.Engine.CHATTERBOX -> CloudTtsEngine.enrichTextForChatterbox(text, item.signal)
            CloudTtsEngine.Engine.QWEN3_TTS -> text // Qwen3 uses voiceInstruction instead
        }

        return CloudTtsEngine.synthesize(
            text = enrichedText,
            engine = engine,
            voiceInstruction = voiceInstruction,
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

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        // Apply crossfade from previous clip's tail for smooth transitions
        val pcm = applyCrossfade(samples, sampleRate)
        if (pcm.isEmpty()) return // Guard empty arrays

        val safeRate = sampleRate.coerceIn(
            AUDIO_TRACK_MIN_SAMPLE_RATE_HZ, AUDIO_TRACK_MAX_SAMPLE_RATE_HZ
        )
        val bufferBytes = pcm.size * 4  // Float = 4 bytes

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
            return // Don't kill the pipeline
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
            // Dynamic timeout based on audio duration + 2s buffer, capped at 30s
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
        } else if (tail == null) {
            // No previous clip at all — apply a short fade-in to avoid click
            // Skip when sample rate changed — just play clean, no soft start artifact
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
                try { it.stop() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
            currentTrack = null
        }
    }
}

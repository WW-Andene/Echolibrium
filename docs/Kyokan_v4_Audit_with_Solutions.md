# Kyōkan v4.0 — Code & Logic Audit with Solutions

**Package:** `com.echolibrium.kyokan` | **Version:** 4.0 (versionCode 4) | **Target SDK:** 35  
**Audit date:** 2026-03-14 | **Method:** Bytecode decompilation via androguard

---

## Executive Summary

The v4.0 APK contains ~80 app classes spanning a sophisticated TTS notification reader with dual engine architecture (SherpaONNX + DirectORT), a DSP pipeline, voice modulation, on-device translation, voice commands, and a self-tuning "Custom Core" via Groq API. The codebase is ambitious but the audit reveals several critical bugs, race conditions, and architectural issues that likely explain the reported instability.

---

## CRITICAL — Likely Crash/Hang Sources

---

### 1. `TtsAliveService.acquireWakeLock()` — Indefinite PARTIAL_WAKE_LOCK without timeout

**Problem:** The service acquires a `PARTIAL_WAKE_LOCK` with no timeout via `wakeLock.acquire()`. If `onDestroy()` is never called (killed by system, crash in another component), the wake lock leaks permanently until reboot. On Android 12+, the system may kill the app after detecting a stuck wake lock.

**Solution:**

```kotlin
// TtsAliveService.kt

private fun acquireWakeLock() {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "Kyokan::TtsAlive"
    ).apply {
        acquire(10 * 60 * 1000L) // 10-minute timeout
    }
    // Schedule re-acquisition before timeout expires
    handler.postDelayed(wakeLockRenewer, 9 * 60 * 1000L)
}

private val wakeLockRenewer = object : Runnable {
    override fun run() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            it.acquire(10 * 60 * 1000L)
            handler.postDelayed(this, 9 * 60 * 1000L)
        }
    }
}

private fun releaseWakeLock() {
    handler.removeCallbacks(wakeLockRenewer)
    wakeLock?.let {
        if (it.isHeld) it.release()
    }
    wakeLock = null
}
```

---

### 2. `AudioPipeline.playPcm()` — 60-second blocking await on audio thread

**Problem:** The pipeline thread blocks for up to 60 seconds per utterance waiting for an `OnPlaybackPositionUpdateListener` marker callback. If the AudioTrack is interrupted, stopped externally, or the marker position math produces 0 or -1 (from empty synthesis result), the thread hangs for a full minute — during which no new notifications are read and the queue backs up.

**Solution:**

```kotlin
// AudioPipeline.kt

private fun playPcm(pcm: FloatArray, sampleRate: Int) {
    val crossfaded = applyCrossfade(pcm, sampleRate)
    if (crossfaded.isEmpty()) return // Guard empty arrays

    val track = try {
        buildAudioTrack(crossfaded.size, sampleRate)
    } catch (e: Exception) {
        Log.e(TAG, "AudioTrack creation failed", e)
        return // Don't kill the pipeline
    }

    synchronized(trackLock) { currentTrack = track }

    try {
        track.write(crossfaded, 0, crossfaded.size, AudioTrack.WRITE_BLOCKING)

        // Safe marker position: at least 1, at most array length - 1
        val markerPos = (crossfaded.size - 1).coerceAtLeast(1)
        track.setNotificationMarkerPosition(markerPos)

        val latch = CountDownLatch(1)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) = latch.countDown()
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
        track.play()

        // Dynamic timeout based on audio duration + 2s buffer
        val durationMs = (crossfaded.size * 1000L / sampleRate) + 2000L
        val timeoutMs = durationMs.coerceAtMost(30_000L) // Cap at 30s
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "Playback marker timeout after ${timeoutMs}ms")
        }
    } finally {
        // Always clean up, even on exception
        try { track.stop() } catch (_: Exception) {}
        track.release()
        synchronized(trackLock) {
            if (currentTrack === track) currentTrack = null
        }
    }
}
```

---

### 3. `AudioPipeline.playPcm()` — New AudioTrack per utterance (resource exhaustion)

**Problem:** Every utterance creates a new `AudioTrack.Builder().build()`. No try-catch around the Builder means any failure (max tracks reached, invalid sample rate) crashes the pipeline thread entirely.

**Solution:**

```kotlin
// AudioPipeline.kt — Reusable AudioTrack with sample rate change detection

companion object {
    private var cachedTrack: AudioTrack? = null
    private var cachedSampleRate: Int = 0

    // Valid range per Android docs
    private const val MIN_SAMPLE_RATE = 4000
    private const val MAX_SAMPLE_RATE = 192000
}

private fun getOrCreateTrack(bufferBytes: Int, sampleRate: Int): AudioTrack? {
    val clampedRate = sampleRate.coerceIn(MIN_SAMPLE_RATE, MAX_SAMPLE_RATE)

    // Reuse if same sample rate and track is in a good state
    cachedTrack?.let {
        if (cachedSampleRate == clampedRate && it.state == AudioTrack.STATE_INITIALIZED) {
            return it
        }
        try { it.stop() } catch (_: Exception) {}
        it.release()
        cachedTrack = null
    }

    return try {
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
                    .setSampleRate(clampedRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferBytes, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM) // MODE_STREAM for reuse
            .build()
            .also {
                cachedTrack = it
                cachedSampleRate = clampedRate
            }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create AudioTrack", e)
        null
    }
}
```

---

### 4. `AudioPipeline.loop()` — Single exception kills the entire pipeline

**Problem:** If an uncaught exception occurs outside the `processItem()` try-catch, or if `running` is set to false by `shutdown()`, the loop exits permanently. The `start()` method has a `if (running) return` guard that prevents restart.

**Solution:**

```kotlin
// AudioPipeline.kt

private fun loop(ctx: Context) {
    Log.d(TAG, "Pipeline loop started")
    while (running) {
        try {
            val item = queue.poll(1, TimeUnit.SECONDS)
            if (item != null) {
                processItem(ctx, item)
            }
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

fun start(ctx: Context) {
    // Remove the early return; allow restart after shutdown
    if (running) return
    running = true

    observationDb = ObservationDb.getInstance(ctx)
    initDirectOrt(ctx)

    pipelineThread = Thread({
        loop(ctx)
    }, "AudioPipelineLoop").apply {
        isDaemon = true
        start()
    }
}

fun shutdown() {
    running = false
    stop()
    // ... existing cleanup ...
    // Don't need to guard start() anymore — running=false allows re-entry
}
```

---

### 5. `NotificationReaderService.onNotificationPosted()` — Unbounded executor

**Problem:** Notification processing is dispatched to `processingExecutor` with no queue bounding or rejection handling. During a notification flood, this spawns unlimited tasks competing for the same synthesis pipeline.

**Solution:**

```kotlin
// NotificationReaderService.kt

// Replace the unbounded executor with a bounded single-thread + bounded queue
private val processingExecutor: ExecutorService = ThreadPoolExecutor(
    1, 1,                               // single thread
    0L, TimeUnit.MILLISECONDS,
    LinkedBlockingQueue<Runnable>(20),   // max 20 pending
    ThreadPoolExecutor.DiscardOldestPolicy() // drop oldest on overflow
)
```

---

## HIGH — Race Conditions & Thread Safety

---

### 6. `AudioPipeline` static fields — Non-volatile, non-synchronized access

**Problem:** `directOrtEngine`, `yatagamiSynthesizer`, `kokoroTokenizer` are written on the main thread during `initDirectOrt()` and read from the daemon pipeline thread. No synchronization — classic TOCTOU race leading to NPE crashes.

**Solution:**

```kotlin
// AudioPipeline.kt — Use @Volatile + local copies

companion object {
    @Volatile private var directOrtEngine: DirectOrtEngine? = null
    @Volatile private var yatagamiSynthesizer: YatagamiSynthesizer? = null
    @Volatile private var kokoroTokenizer: PhonemeTokenizer? = null
}

// In processItem / synthesizeWithYatagami — capture to local val before use:
private fun synthesizeWithYatagami(ctx: Context, item: QueueItem, text: String): SynthResult? {
    val synth = yatagamiSynthesizer ?: return null    // local copy
    val ort = directOrtEngine ?: return null           // local copy
    val tokenizer = kokoroTokenizer ?: return null     // local copy

    // Now use local vals — they can't be nulled by another thread
    val tokens = tokenizer.tokenize(text) ?: return null
    return synth.synthesize(tokens, item.modulated, item.signal, item.profile)
}
```

---

### 7. `SherpaEngine` — Volatile boolean read outside synchronized blocks

**Problem:** `isKokoroReady` is volatile but `kokoroTts` is not. Between checking `isKokoroReady` and using `kokoroTts`, `release()` can null it from another thread.

**Solution:**

```kotlin
// SherpaEngine.kt — All access through synchronized

@Synchronized
fun synthesize(text: String, sid: Int, speed: Float): Pair<FloatArray, Int>? {
    // Already synchronized, just guard the reference
    val tts = kokoroTts ?: return null
    val audio = tts.generate(text, sid, speed)
    lastSampleRate = audio.sampleRate
    return Pair(audio.samples, audio.sampleRate)
}

// Remove the standalone isKokoroReady() / isReady() non-synchronized accessors,
// or make them @Synchronized too
@Synchronized
fun isReady(): Boolean = isKokoroReady && kokoroTts != null
```

---

### 8. `DirectOrtEngine.initPiper()` — Not synchronized, LRU eviction race

**Problem:** `initPiper()` reads/writes the `piperCache` LinkedHashMap without synchronization. Concurrent voice switches corrupt the cache or close an active session.

**Solution:**

```kotlin
// DirectOrtEngine.kt — Add synchronization

private val cacheLock = Any()

fun initPiper(ctx: Context, voiceId: String): Boolean {
    synchronized(cacheLock) {
        // Existing logic, now thread-safe
        if (piperCache.containsKey(voiceId)) {
            activePiperVoiceId = voiceId
            piperSession = piperCache[voiceId]
            isPiperReady = true
            return true
        }
        // ... rest of loading logic ...
    }
}

// Also guard synthesizePiper:
fun synthesizePiper(phonemeIds: LongArray, scales: FloatArray, sid: Long): Pair<FloatArray, Int>? {
    val session: OrtSession
    synchronized(cacheLock) {
        session = piperSession ?: return null
    }
    // Run inference outside the lock (long-running)
    // ...
}
```

---

### 9. `VoiceCommandListener` — Static state with minimal synchronization

**Problem:** `recognizer`, `isListening`, `consecutiveErrors` are modified from both the main thread and the mainHandler without synchronization.

**Solution:**

```kotlin
// VoiceCommandListener.kt

companion object {
    @Volatile private var isListening = false
    @Volatile private var recognizer: SpeechRecognizer? = null
    private val consecutiveErrors = AtomicInteger(0)
}

fun stop() {
    isListening = false
    mainHandler.post {
        recognizer?.let {
            try {
                it.stopListening()
                it.cancel()
                it.destroy()
            } catch (_: Exception) {}
        }
        recognizer = null
    }
}

private fun startListeningInternal() {
    val rec = recognizer ?: return  // local copy
    if (!isListening) return
    // Use local `rec` — safe even if recognizer is nulled concurrently
    try {
        rec.startListening(buildRecognizerIntent())
    } catch (e: Exception) {
        Log.e(TAG, "Error starting speech recognition", e)
        scheduleRestart(isError = true)
    }
}
```

---

## MEDIUM — Logic Bugs & Edge Cases

---

### 10. `detectLanguage()` — Regex compiled per notification

**Problem:** 34 regex patterns compiled from scratch on every notification. Massive CPU waste.

**Solution:**

```kotlin
// NotificationReaderService.kt

companion object {
    // Compile once at class load
    private val FR_CONTRACTION_PATTERNS = listOf(
        "\\bc'est\\b", "\\bj'ai\\b", "\\bn'est\\b", "\\bqu'\\b",
        "\\bl'\\w", "\\bd'\\w", "\\bs'\\w", "\\bn'\\w"
    ).map { Pattern.compile(it) }

    private val FR_WORD_PATTERNS = listOf(
        "\\bune\\b", "\\bles\\b", "\\bdes\\b", "\\baux\\b",
        "\\bsont\\b", "\\bpas\\b", "\\bque\\b", "\\bqui\\b",
        "\\bje\\b", "\\btu\\b", "\\bnous\\b", "\\bvous\\b",
        "\\bils\\b", "\\bpour\\b", "\\bavec\\b", "\\bdans\\b",
        "\\bbonjour\\b", "\\bmerci\\b", "\\bsalut\\b", "\\bbonsoir\\b",
        "\\bcomment\\b", "\\bpourquoi\\b", "\\bquand\\b",
        "\\bactivé\\b", "\\bdésactivé\\b", "\\bêtes\\b"
    ).map { Pattern.compile(it) }
}

private fun detectLanguage(text: String, appLang: String?): String {
    val lower = text.lowercase(Locale.ROOT)

    // Fast path: check French diacritics
    val diacriticCount = lower.count { it in "éèêëàâùûôîïç" }
    if (diacriticCount >= 2) return "fr"

    // Use pre-compiled patterns
    val contractionHits = FR_CONTRACTION_PATTERNS.count { it.matcher(lower).find() }
    if (contractionHits >= 1) return "fr"

    val wordHits = FR_WORD_PATTERNS.count { it.matcher(lower).find() }
    if (wordHits >= 3) return "fr"
    if (wordHits >= 1 && appLang == "fr") return "fr"

    return "en"
}
```

---

### 11. `isDndActive()` — Edge case when start == end

**Problem:** DND from 22:00 to 22:00 (same hour) is treated as "no DND", which may confuse users expecting 24h DND.

**Solution:**

```kotlin
// NotificationReaderService.kt

private fun isDndActive(): Boolean {
    if (!prefs.getBoolean("dnd_enabled", false)) return false

    val start = prefs.getInt("dnd_start", 22)
    val end = prefs.getInt("dnd_end", 8)
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    // Same hour means 24h DND (always active)
    if (start == end) return true

    return if (start > end) {
        // Wraps midnight: 22:00–08:00
        hour >= start || hour < end
    } else {
        // Same day: 01:00–06:00
        hour >= start && hour < end
    }
}
```

---

### 12. `processItem()` — Sculpted mode copy bitmask zeroes wrong fields

**Problem:** The `ModulatedVoice.copy$default` call uses bitmask `14331` which zeroes out fields that should be preserved. Speed, pitch, volume may reset to defaults for Kokoro sculpted utterances.

**Solution:**

```kotlin
// AudioPipeline.kt — processItem, sculpted branch

if (synthResult?.engine == EngineType.KOKORO_SCULPTED) {
    // Only override breath and jitter; keep everything else from modulated
    modVoice = modulated.copy(
        breathIntensity = (modulated.breathIntensity * 0.3f).toInt(),
        jitterAmount = modulated.jitterAmount * 0.12f
        // DO NOT pass speed=0, pitch=0, volume=0 etc.
        // The copy$default bitmask must mark ONLY breathIntensity and jitterAmount
        // as "explicitly set", leaving all other params at their original values.
    )
}
```

The bitmask `14331` (binary `0011100000001011`) needs to be recalculated so only the bit positions for `breathIntensity` (param index ~3) and `jitterAmount` (param index ~11) are cleared (meaning "use the passed value"), and all other bits are set (meaning "keep the original"). Verify the exact parameter ordering in the `ModulatedVoice` data class constructor.

---

### 13. `CustomCoreEngine` — Groq API key in plain-text SharedPreferences

**Problem:** `groq_api_key` stored unencrypted. Trivially readable on rooted devices or via backup.

**Solution:**

```kotlin
// CustomCoreEngine.kt — Use EncryptedSharedPreferences

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private fun getSecurePrefs(ctx: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(ctx)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        ctx,
        "kyokan_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

// Add dependency: implementation "androidx.security:security-crypto:1.1.0-alpha06"
// Migrate existing key on first launch:
fun migrateApiKey(ctx: Context) {
    val oldPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    val key = oldPrefs.getString("groq_api_key", null) ?: return
    getSecurePrefs(ctx).edit().putString("groq_api_key", key).apply()
    oldPrefs.edit().remove("groq_api_key").apply()
}
```

---

### 14. `CustomCoreEngine.start()` — Cached patch JSON crash on corrupt data

**Problem:** Malformed `custom_core_last_patch` JSON throws `JSONException`. Caught silently — engine starts without last patch, losing all tuning.

**Solution:**

```kotlin
// CustomCoreEngine.kt

fun start(ctx: Context) {
    // ...existing code...
    val patchJson = prefs.getString("custom_core_last_patch", null)
    if (patchJson != null) {
        try {
            lastPatch = BehaviorPatch.fromJson(JSONObject(patchJson))
            Log.i(TAG, "Restored cached behavior patch")
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt cached patch, clearing", e)
            prefs.edit().remove("custom_core_last_patch").apply()
            // Notify user via ObservationDb event
            ObservationDb.getInstance(ctx).logEvent(
                PersonalityEvent.PATCH_CORRUPT,
                "Cached behavior patch was corrupt and has been reset"
            )
        }
    }
    // ...rest of start...
}
```

---

### 15. `applyCrossfade()` — Fade-in ramp after sample rate change

**Problem:** When sample rate changes between utterances, crossfade blend is skipped (correct) but a fade-in ramp is still applied to the new buffer, creating an audible "soft start" artifact.

**Solution:**

```kotlin
// AudioPipeline.kt

private fun applyCrossfade(pcm: FloatArray, sampleRate: Int): FloatArray {
    val result = pcm.copyOf()
    val prev = prevTail
    val crossfadeSamples = (sampleRate * 40 / 1000).coerceAtMost(pcm.size / 4)

    // Save tail for next crossfade
    val tailStart = (pcm.size - crossfadeSamples).coerceAtLeast(0)
    prevTail = pcm.sliceArray(tailStart until pcm.size)
    prevSampleRate = sampleRate

    // ONLY crossfade if previous tail exists AND sample rates match
    if (prev != null && prevSampleRate == sampleRate && prev.isNotEmpty()) {
        val len = minOf(crossfadeSamples, prev.size, result.size)
        for (i in 0 until len) {
            val t = i.toFloat() / len
            val prevIdx = prev.size - len + i
            result[i] = prev[prevIdx] * (1f - t) + result[i] * t
        }
    }
    // NO fade-in ramp when sample rate changed — just play clean

    return result
}
```

---

### 16. `BootReceiver` — `startForegroundService()` without SDK check

**Problem:** On Android 12+ (SDK 31+), background-started foreground services have restrictions. The service may be killed within 10 seconds.

**Solution:**

```kotlin
// BootReceiver.kt

override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

    Log.d(TAG, "Boot completed — starting TTS alive service")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+: Use WorkManager or schedule with an exact alarm
        // startForegroundService from a BroadcastReceiver is allowed
        // ONLY if the app has a notification listener permission (which Kyōkan does)
        // But add a safety net:
        try {
            context.startForegroundService(
                Intent(context, TtsAliveService::class.java)
            )
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "FGS not allowed at boot, scheduling retry", e)
            // Retry via WorkManager
            val work = OneTimeWorkRequestBuilder<TtsStartWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(work)
        }
    } else {
        context.startForegroundService(
            Intent(context, TtsAliveService::class.java)
        )
    }
}
```

---

### 17. `DirectOrtEngine.loadVoices()` — Wrong voice count candidates

**Problem:** Tries `[11, 26, 54, 108]` as voice count candidates. None match Kokoro-82M (82 voices), so it falls to a default of `tokensPerVoice=512` which is likely wrong.

**Solution:**

```kotlin
// DirectOrtEngine.kt

private fun loadVoices(path: String) {
    // ... existing file loading ...

    val totalFloats = voicesData!!.size
    // Standard Kokoro style dims
    val STYLE_DIM = 256

    // Calculate voice count from known style dimension
    if (totalFloats % STYLE_DIM == 0) {
        voiceCount = totalFloats / STYLE_DIM
        tokensPerVoice = STYLE_DIM
        Log.i(TAG, "Loaded $voiceCount voices × $tokensPerVoice style dims")
        return
    }

    // Fallback: try common voice counts
    val candidates = listOf(82, 54, 26, 108, 11)
    for (count in candidates) {
        if (totalFloats % count == 0) {
            voiceCount = count
            tokensPerVoice = totalFloats / count
            Log.i(TAG, "Loaded $voiceCount voices, $tokensPerVoice tokens each (heuristic)")
            return
        }
    }

    // Last resort
    tokensPerVoice = 256
    voiceCount = totalFloats / tokensPerVoice
    Log.w(TAG, "Could not determine voice layout, guessing: $voiceCount voices × $tokensPerVoice")
}
```

---

### 18. `DirectOrtEngine.synthesizeKokoro()` — Style tensor shape hardcoded `[1, 256]`

**Problem:** If model uses a different style dimension, ONNX Runtime throws a shape mismatch. The dimension should come from `tokensPerVoice`.

**Solution:**

```kotlin
// DirectOrtEngine.kt — synthesizeKokoro()

// Replace hardcoded shape:
// OLD: val styleShape = longArrayOf(1, 256)
// NEW:
val styleShape = longArrayOf(1, tokensPerVoice.toLong())

val styleTensor = OnnxTensor.createTensor(
    ortEnv,
    FloatBuffer.wrap(sculptedStyle),
    styleShape
)
```

---

### 19. `synthesizePiperMapped()` — `sid = -1` vs SherpaEngine `sid = 0`

**Problem:** DirectORT path passes `sid = -1` (no speaker), SherpaEngine always passes `sid = 0`. Fallback between engines causes voice identity shift.

**Solution:**

```kotlin
// YatagamiSynthesizer.kt — synthesizePiperMapped()

private fun synthesizePiperMapped(
    tokenIds: LongArray,
    modulated: ModulatedVoice,
    signal: SignalMap
): SynthResult? {
    val scales = ScaleMapper.mapScales(modulated, signal)
    // Use sid=0 to match SherpaEngine behavior
    val result = directOrt.synthesizePiper(tokenIds, scales, sid = 0L)
    // ...
}

// Alternatively, make both configurable via the VoiceProfile:
val sid = profile.piperSpeakerId ?: 0L
```

---

## LOW — Quality & Performance

---

### 20. `ObservationDb` — No WAL mode

**Problem:** Concurrent reads/writes from pipeline and CustomCore threads serialize on database lock, adding latency.

**Solution:**

```kotlin
// ObservationDb.kt

override fun onCreate(db: SQLiteDatabase) {
    db.execSQL("PRAGMA journal_mode=WAL;")
    // ... existing table creation ...
}

// Or set in the constructor:
class ObservationDb private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    init {
        writableDatabase.enableWriteAheadLogging()
    }
}
```

---

### 21. `CrashLogger` — Deprecated `PackageInfo.versionCode`

**Problem:** Uses `packageInfo.versionCode` which is deprecated since API 28.

**Solution:**

```kotlin
// CrashLogger.kt — writeCrashLog()

val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
val versionName = pkgInfo.versionName
val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    pkgInfo.longVersionCode
} else {
    @Suppress("DEPRECATION")
    pkgInfo.versionCode.toLong()
}
writer.println("App: $versionName (code $versionCode)")
```

---

### 22. `VoiceCommandListener` — `SpeechRecognizer` misused as always-on listener

**Problem:** Android's SpeechRecognizer is designed for short dictation, not persistent listening. Causes high battery drain, frequent beep sounds on some devices, and the 10-error bailout.

**Solution:**

```kotlin
// VoiceCommandListener.kt — Option A: Use on-device hotword via ML Kit or Vosk

// Replace SpeechRecognizer with a lightweight VAD + hotword detector:
// 1. Use AudioRecord in low-power mode to capture small frames
// 2. Run a tiny wake-word model (e.g. Porcupine, Snowboy, or custom Kokoro-based)
// 3. Only activate SpeechRecognizer AFTER wake word detected

// Option B: If keeping SpeechRecognizer, add exponential backoff and power awareness

private fun scheduleRestart(isError: Boolean) {
    if (!isListening) return

    if (isError) {
        val errors = consecutiveErrors.incrementAndGet()
        if (errors > MAX_CONSECUTIVE_ERRORS) {
            Log.w(TAG, "Too many errors ($errors), stopping")
            isListening = false
            onStatusChanged?.invoke(false)
            return
        }
        // Exponential backoff: 800ms, 1.6s, 3.2s, ... up to 60s
        val delayMs = (800L * (1L shl minOf(errors - 1, 6)))
            .coerceAtMost(MAX_BACKOFF_MS)
        mainHandler.postDelayed({ startListeningInternal() }, delayMs)
    } else {
        consecutiveErrors.set(0)
        // Short delay for normal restart (end-of-speech)
        mainHandler.postDelayed({ startListeningInternal() }, 300L)
    }
}
```

---

### 23. ProGuard/R8 keep rules for JNI

**Problem:** R8 is active (obfuscated names visible), but ONNX Runtime and SherpaONNX classes need to be kept for JNI callbacks. Currently they appear un-obfuscated, which is correct — but needs explicit keep rules.

**Solution:**

```proguard
# proguard-rules.pro

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }

# SherpaONNX
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

# App JNI bridge (if any custom native methods exist)
-keepclasseswithmembers class com.echolibrium.kyokan.** {
    native <methods>;
}
```

---

### 24. `processItem()` — Hardcoded float constants for trajectory parameters

**Problem:** ~200 instructions of inline float constants (magic numbers) for parameter trajectories. Impossible to tune without recompilation.

**Solution:**

```kotlin
// Create a configuration file: assets/trajectory_config.json

{
  "dramatic": {
    "volume": { "base": 1.2, "peak": 3.0, "curve": 1.4 },
    "breathiness": { "floor": 0.5, "ceiling": 1.5 },
    "pitch": { "base": 1.35, "peak": 2.0, "curve": 1.3 },
    "jitter": { "floor": 0.5, "ceiling": 1.9 },
    "speed": { "base": 1.3, "peak": 2.5, "curve": 1.35 }
  },
  "relaxed": {
    "volume": { "min": 0.7, "mid": 1.1, "max": 1.35 },
    "breathiness": { "floor": 0.5, "ceiling": 1.5 },
    "jitter": { "floor": 0.8, "ceiling": 1.9 },
    "speed": { "min": 0.8, "mid": 0.95, "max": 1.3 }
  }
}

// Load in AudioPipeline.start():
trajectoryConfig = ctx.assets.open("trajectory_config.json")
    .bufferedReader().use { JSONObject(it.readText()) }

// Then in processItem, look up values:
val cfg = trajectoryConfig.getJSONObject(trajectory.name.lowercase())
val volumeCfg = cfg.getJSONObject("volume")
// Use volumeCfg.getDouble("base"), etc. instead of inline float literals
```

---

## Architecture Note — `initDirectOrt()` blocking service start

**Problem:** `AudioPipeline.start()` calls `initDirectOrt()` synchronously, which loads multi-MB ONNX models. This runs inside `NotificationReaderService.onCreate()`, which blocks the main thread. If model loading takes >5 seconds, Android may ANR the service.

**Solution:**

```kotlin
// AudioPipeline.kt

fun start(ctx: Context) {
    if (running) return
    running = true

    observationDb = ObservationDb.getInstance(ctx)

    // Move model loading to background
    Thread({
        try {
            initDirectOrt(ctx)
            Log.i(TAG, "DirectOrt initialized on background thread")
        } catch (e: Exception) {
            Log.e(TAG, "DirectOrt init failed", e)
        }
    }, "DirectOrtInit").start()

    // Start pipeline loop immediately — it will use SherpaEngine fallback
    // until DirectOrt finishes loading
    pipelineThread = Thread({ loop(ctx) }, "AudioPipelineLoop").apply {
        isDaemon = true
        start()
    }
}
```

This way the pipeline starts immediately using the SherpaEngine fallback, and upgrades to DirectORT once it's ready. No ANR risk.

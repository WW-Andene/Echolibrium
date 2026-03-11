package com.kokoro.reader

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton wrapper around sherpa-onnx OfflineTts.
 *
 * Supports two synthesis backends, both running locally:
 *   • Kokoro — 30 voices in a single model (multi-lang-v1_0), selected by speaker ID
 *   • Piper/VITS — one model per voice, loaded on demand
 *
 * Kokoro model is extracted from APK assets to the filesystem on first run,
 * then loaded via file-based constructor to avoid SIGSEGV on some devices.
 *
 * Thread-safe: synthesize methods are called from the AudioPipeline background thread.
 */
object SherpaEngine {

    private const val TAG = "SherpaEngine"

    // Asset paths (relative to assets/)
    private const val KOKORO_DIR = "kokoro-model"
    private const val PIPER_DIR  = "piper-models"

    /** Maximum time (ms) to wait for native OfflineTts constructor before giving up. */
    private const val INIT_TIMEOUT_MS = 60_000L

    // ── Device-adaptive engine configuration ────────────────────────────────────

    /** Detected SoC vendor — cached at first access. */
    private enum class SocVendor { MEDIATEK, QUALCOMM, OTHER }

    private val socVendor: SocVendor by lazy {
        val vendor = detectSocVendor()
        Log.i(TAG, "SoC detected: $vendor (hw=${Build.HARDWARE}, " +
            "mfr=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else "?"}, " +
            "model=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "?"})")
        vendor
    }

    private fun detectSocVendor(): SocVendor {
        // API 31+ gives us direct SoC info
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mfr = Build.SOC_MANUFACTURER.lowercase()
            if (mfr.contains("mediatek") || mfr.contains("mtk")) return SocVendor.MEDIATEK
            if (mfr.contains("qualcomm") || mfr.contains("qcom")) return SocVendor.QUALCOMM
        }
        // Fallback: Build.HARDWARE heuristic (works on all API levels)
        val hw = Build.HARDWARE.lowercase()
        if (hw.contains("mt") || hw.contains("mediatek")) return SocVendor.MEDIATEK
        if (hw.contains("qcom") || hw.contains("snapdragon") || hw.contains("kona")
            || hw.contains("lahaina") || hw.contains("taro")) return SocVendor.QUALCOMM
        return SocVendor.OTHER
    }

    /**
     * Returns the optimal ONNX Runtime provider for this device.
     *
     * All devices use "cpu" — the NNAPI EP does not support LSTM ops (core to
     * Kokoro TTS), causing model partitioning where Conv/MatMul run on APU but
     * LSTM falls back to NNAPI's slow reference CPU. This is worse than pure
     * CPU inference. The original HWUI mutex collision on MediaTek/MIUI is
     * already solved by process isolation (:tts runs in a separate process
     * with no HWUI).
     */
    private fun optimalProvider(): String = "cpu"

    /**
     * Returns the optimal thread count for this device.
     *
     * MediaTek: 1 — ONNX Runtime thread pool init can SIGSEGV on Dimensity
     *   SoCs when creating multiple threads during model loading. Single-threaded
     *   inference is ~30% slower but avoids the native crash entirely.
     * Qualcomm/other: 2 — safe multi-threading.
     */
    private fun optimalThreadCount(): Int = when (socVendor) {
        SocVendor.MEDIATEK -> 1
        else -> 2
    }

    // ── Kokoro engine (single model, 30 speakers) ─────────────────────────────
    private var kokoroTts: OfflineTts? = null

    // ── Piper engine (one model per voice, cached) ────────────────────────────
    private var piperTts: OfflineTts? = null
    private var piperLoadedVoiceId: String? = null

    /** Context for writing cross-process status file. Set by warmUp(). */
    @Volatile private var statusContext: Context? = null

    var lastSampleRate: Int = 22050
        private set

    @Volatile var isReady = false
        private set

    /** Non-null when the engine failed to initialize — contains the error reason */
    @Volatile var errorMessage: String? = null
        private set

    /** Human-readable status of what the engine is currently doing */
    @Volatile var statusMessage: String = "idle"
        private set

    /** Writes current engine status to the cross-process status file. */
    private fun syncStatus() {
        val ctx = statusContext ?: return
        TtsBridge.writeStatus(
            ctx = ctx,
            ready = isReady,
            status = statusMessage,
            error = errorMessage,
            alive = true,
            voiceCmdListening = VoiceCommandListener.isListening,
            voiceCmdWakeWord = VoiceCommandListener.wakeWord,
            initProgress = initProgress
        )
    }

    /** Timestamp (ms) when initialization started, 0 if not initializing */
    private val initStartTime = AtomicLong(0)

    /** Approximate init progress 0–100. Drives progress bar in the UI. */
    @Volatile var initProgress: Int = 0
        private set

    /**
     * Callback fired (on any thread) when the Kokoro engine becomes ready.
     * Useful for updating UI status.
     */
    @Volatile var onReadyCallback: (() -> Unit)? = null

    @Volatile private var isWarmingUp = false
    private val warmUpLock = Object()

    // ── Asset resolution ──────────────────────────────────────────────────────

    /**
     * Prefer pre-optimized .ort model over .onnx if available.
     * ORT format loads 5-10x faster (pre-optimized flatbuffer, no graph optimization at runtime).
     */
    private fun resolveModel(ctx: Context, onnxPath: String): String {
        val ortPath = onnxPath.replace(".onnx", ".ort")
        val ortExists = try { ctx.assets.open(ortPath).use { true } } catch (_: Throwable) { false }
        if (ortExists) {
            Log.i(TAG, "│ Using pre-optimized ORT model: $ortPath")
            return ortPath
        }
        return onnxPath
    }

    // ── Eager warm-up ─────────────────────────────────────────────────────────

    /** SharedPreferences name for tracking init crashes across process restarts. */
    private const val INIT_PREFS = "sherpa_init_tracker"
    private const val KEY_INIT_CRASH_COUNT = "init_crash_count"
    private const val KEY_INIT_IN_PROGRESS = "init_in_progress"
    private const val KEY_INIT_LAST_CRASH_TIME = "init_last_crash_time"
    /** Stop attempting init after this many consecutive crashes. */
    private const val MAX_INIT_CRASHES = 3
    /** Reset crash counter after this much time (ms) — gives the device a chance to recover. */
    private const val CRASH_RESET_WINDOW_MS = 300_000L  // 5 minutes

    /**
     * Initializes the Kokoro engine on a background thread.
     * Extracts model from APK assets to filesystem, then loads via file-based
     * constructor. Guarded against duplicate concurrent calls and crash loops.
     *
     * If previous init attempts crashed the process (SIGSEGV), this method
     * tracks the failures and stops retrying after [MAX_INIT_CRASHES] to break
     * the crash loop. The user can force a retry from the UI.
     */
    fun warmUp(ctx: Context) {
        statusContext = ctx.applicationContext
        if (isReady && kokoroTts != null) { onReadyCallback?.invoke(); return }
        synchronized(warmUpLock) {
            if (isWarmingUp) return
            isWarmingUp = true
        }

        // ── Crash-loop detection ─────────────────────────────────────────
        // Before calling native code that can SIGSEGV, check if previous
        // attempts crashed. We use SharedPreferences because they survive
        // process restarts (the :tts process gets killed and restarted by Android).
        val initPrefs = ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)
        val wasInProgress = initPrefs.getBoolean(KEY_INIT_IN_PROGRESS, false)
        var crashCount = initPrefs.getInt(KEY_INIT_CRASH_COUNT, 0)
        val lastCrashTime = initPrefs.getLong(KEY_INIT_LAST_CRASH_TIME, 0)

        // If the previous init was still "in progress" when the process died, it crashed.
        if (wasInProgress) {
            crashCount++
            initPrefs.edit()
                .putInt(KEY_INIT_CRASH_COUNT, crashCount)
                .putLong(KEY_INIT_LAST_CRASH_TIME, System.currentTimeMillis())
                .putBoolean(KEY_INIT_IN_PROGRESS, false)
                .apply()
            Log.w(TAG, "Previous engine init crashed (crash #$crashCount)")
        }

        // Reset crash counter if enough time has passed (device might have cooled down)
        if (crashCount > 0 && System.currentTimeMillis() - lastCrashTime > CRASH_RESET_WINDOW_MS) {
            crashCount = 0
            initPrefs.edit().putInt(KEY_INIT_CRASH_COUNT, 0).apply()
            Log.d(TAG, "Init crash counter reset (>5min since last crash)")
        }

        // If we've crashed too many times, don't attempt init — report error instead.
        if (crashCount >= MAX_INIT_CRASHES) {
            Log.e(TAG, "Engine init disabled after $crashCount consecutive crashes")
            errorMessage = "Engine crashed $crashCount times during initialization. " +
                "The model may be incompatible with this device. " +
                "Tap \"Retry engine init\" to try again, or restart the app after 5 minutes."
            statusMessage = "error: repeated native crashes"
            isReady = false
            synchronized(warmUpLock) { isWarmingUp = false }
            syncStatus()
            return
        }

        // ── Set init_in_progress BEFORE any sherpa-onnx code runs ────────
        // The native library loads when sherpa-onnx config classes are first
        // constructed (JNI System.loadLibrary). If that crashes (SIGSEGV),
        // this flag must already be persisted so the next startup detects it.
        // Using commit() (synchronous) to ensure it's flushed to disk.
        initPrefs.edit().putBoolean(KEY_INIT_IN_PROGRESS, true).commit()

        Thread {
            try {
                if (initializeKokoro(ctx)) {
                    onReadyCallback?.invoke()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Warm-up failed", e)
                errorMessage = e.message ?: "Unknown error during warm-up"
                statusMessage = "error: ${errorMessage}"
                syncStatus()
            } finally {
                // Clear init_in_progress — if we reached this point, the process survived.
                // Only a SIGSEGV (which kills the process) will leave the flag set.
                try {
                    ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_INIT_IN_PROGRESS, false).apply()
                } catch (_: Throwable) {}
                synchronized(warmUpLock) { isWarmingUp = false }
            }
        }.apply { name = "SherpaEngine-warmup"; isDaemon = true; start() }
    }

    /**
     * Force retry of engine initialization, resetting the crash counter.
     * Called from the UI when the user taps a retry button.
     */
    fun forceRetry(ctx: Context) {
        val initPrefs = ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)
        initPrefs.edit()
            .putInt(KEY_INIT_CRASH_COUNT, 0)
            .putBoolean(KEY_INIT_IN_PROGRESS, false)
            .apply()
        errorMessage = null
        statusMessage = "retrying…"
        isReady = false
        syncStatus()
        warmUp(ctx)
    }

    // ── Kokoro initialization ─────────────────────────────────────────────────

    @Synchronized
    fun initializeKokoro(ctx: Context): Boolean {
        if (isReady && kokoroTts != null) return true

        // Check crash counter — prevents AudioPipeline's ensureKokoroReady() from
        // bypassing the crash-loop breaker by calling initializeKokoro() directly.
        val initPrefs = ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)
        val crashCount = initPrefs.getInt(KEY_INIT_CRASH_COUNT, 0)
        if (crashCount >= MAX_INIT_CRASHES) {
            Log.e(TAG, "initializeKokoro blocked — $crashCount consecutive init failures")
            return false
        }

        return try {
            initProgress = 5
            statusMessage = "verifying model assets…"
            syncStatus()
            Log.i(TAG, "┌── Kokoro init START ──────────────────────────")
            Log.i(TAG, "│ Model dir: assets/$KOKORO_DIR")

            // ── Pre-flight: verify required assets exist BEFORE calling native code ──
            // Missing assets cause OfflineTts() to hang or SIGSEGV with no error message.
            // Accept either .ort (pre-optimized) or .onnx format for the model.
            val requiredFiles = listOf(
                "$KOKORO_DIR/model.onnx",  // or model.ort — checked via resolveModel()
                "$KOKORO_DIR/voices.bin",
                "$KOKORO_DIR/tokens.txt"
            )
            val missingFiles = mutableListOf<String>()
            for (path in requiredFiles) {
                // For model files, also accept .ort pre-optimized variant
                val ortPath = path.replace(".onnx", ".ort")
                val exists = try {
                    ctx.assets.open(path).use { true }
                } catch (_: Throwable) {
                    if (ortPath != path) try { ctx.assets.open(ortPath).use { true } } catch (_: Throwable) { false }
                    else false
                }
                Log.i(TAG, "│ asset %-30s %s".format(path, if (exists) "✓" else "✗ MISSING"))
                if (!exists) missingFiles.add(path)
            }
            // Also check espeak-ng-data directory exists
            val espeakExists = try {
                ctx.assets.list("$KOKORO_DIR/espeak-ng-data")?.isNotEmpty() == true
            } catch (_: Throwable) { false }
            Log.i(TAG, "│ asset %-30s %s".format("$KOKORO_DIR/espeak-ng-data/", if (espeakExists) "✓" else "✗ MISSING"))
            if (!espeakExists) missingFiles.add("$KOKORO_DIR/espeak-ng-data/")

            if (missingFiles.isNotEmpty()) {
                val msg = "Missing model files: ${missingFiles.joinToString(", ")}. " +
                    "The APK was built without bundling the Kokoro model. " +
                    "Run the CI build (build.yml) or download the model manually."
                Log.e(TAG, "│ $msg")
                Log.e(TAG, "└── Kokoro init FAILED (missing assets) ───────")
                statusMessage = "error: model files missing from APK"
                errorMessage = msg
                isReady = false
                syncStatus()
                return false
            }

            initProgress = 15
            statusMessage = "extracting model to filesystem…"
            syncStatus()

            // ── Extract model from APK assets to filesystem ──────────────
            // The AssetManager-based OfflineTts() constructor crashes with
            // SIGSEGV on Xiaomi/MediaTek devices. Using filesystem paths
            // with the file-based constructor avoids this entirely.
            val extractedDir = extractKokoroModel(ctx)
            if (extractedDir == null) {
                Log.e(TAG, "│ Failed to extract Kokoro model to filesystem")
                Log.e(TAG, "└── Kokoro init FAILED (extraction) ──────────")
                statusMessage = "error: model extraction failed"
                errorMessage = "Failed to extract model files to device storage. " +
                    "Check available storage space."
                isReady = false
                syncStatus()
                return false
            }

            initProgress = 30
            statusMessage = "preparing Kokoro config…"
            syncStatus()

            // Resolve which model file was extracted (.ort or .onnx)
            val modelFile = File(extractedDir, "model.ort").let {
                if (it.exists()) it else File(extractedDir, "model.onnx")
            }
            val isOrt = modelFile.name.endsWith(".ort")

            val provider = optimalProvider()
            val threads = optimalThreadCount()

            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model   = modelFile.absolutePath,
                voices  = File(extractedDir, "voices.bin").absolutePath,
                tokens  = File(extractedDir, "tokens.txt").absolutePath,
                dataDir = File(extractedDir, "espeak-ng-data").absolutePath
            )

            val modelConfig = OfflineTtsModelConfig(
                kokoro     = kokoroConfig,
                numThreads = threads,
                debug      = false,
                provider   = provider
            )

            val config = OfflineTtsConfig(model = modelConfig)

            initProgress = 35
            val providerLabel = if (isOrt) "loading optimized model…"
                else "loading native model (this may take 10-30s)…"
            statusMessage = providerLabel
            syncStatus()
            Log.i(TAG, "│ Provider: $provider, threads: $threads, SoC: $socVendor")
            Log.i(TAG, "│ Using file-based constructor (no AssetManager)")
            Log.i(TAG, "│ Model: ${modelFile.absolutePath}")
            Log.i(TAG, "│ Calling OfflineTts() — native JNI constructor…")
            initStartTime.set(System.currentTimeMillis())

            // init_in_progress flag is already set by warmUp() before this thread started.
            // This ensures the flag is persisted even if the native library load (triggered
            // by config class constructors above) crashes the process.

            // Run the native constructor on a separate thread with a timeout.
            // OfflineTts() can hang indefinitely on some devices.
            val resultHolder = arrayOfNulls<OfflineTts>(1)
            val errorHolder = arrayOfNulls<Throwable>(1)
            val latch = CountDownLatch(1)

            val initThread = Thread {
                try {
                    // File-based constructor — no AssetManager, no HWUI mutex corruption
                    resultHolder[0] = OfflineTts(config = config)
                } catch (e: Throwable) {
                    errorHolder[0] = e
                } finally {
                    latch.countDown()
                }
            }.apply { name = "SherpaEngine-native-init"; isDaemon = true; start() }

            // Sync progress periodically while native init runs
            val progressThread = Thread {
                try {
                    val start = System.currentTimeMillis()
                    while (!latch.await(500, TimeUnit.MILLISECONDS)) {
                        val elapsed = System.currentTimeMillis() - start
                        // Asymptotic progress: approaches 90% over ~30s
                        initProgress = (35 + (55 * (1.0 - Math.exp(-elapsed / 15000.0)))).toInt().coerceAtMost(90)
                        syncStatus()
                    }
                } catch (_: InterruptedException) {}
            }.apply { name = "SherpaEngine-progress"; isDaemon = true; start() }

            val completed = latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            progressThread.interrupt()
            val elapsed = System.currentTimeMillis() - initStartTime.get()
            initStartTime.set(0)

            if (!completed) {
                // Native init hung — count as a failure to prevent infinite retry on restart
                val prevCrashCount = initPrefs.getInt(KEY_INIT_CRASH_COUNT, 0)
                initPrefs.edit()
                    .putBoolean(KEY_INIT_IN_PROGRESS, false)
                    .putInt(KEY_INIT_CRASH_COUNT, prevCrashCount + 1)
                    .putLong(KEY_INIT_LAST_CRASH_TIME, System.currentTimeMillis())
                    .apply()
                Log.e(TAG, "│ OfflineTts() TIMED OUT after ${elapsed}ms (failure #${prevCrashCount + 1})")
                Log.e(TAG, "└── Kokoro init FAILED (timeout) ─────────────")
                statusMessage = "error: init timed out after ${elapsed / 1000}s"
                errorMessage = "Engine initialization timed out after ${elapsed / 1000}s. " +
                    "The model may be too large for this device's memory."
                isReady = false
                syncStatus()
                // Interrupt the hung thread (best effort — native code may ignore this)
                try { initThread.interrupt() } catch (_: Throwable) {}
                return false
            }

            // Check if native init threw — if NNAPI failed, fall back to CPU
            val initError = errorHolder[0]
            if (initError != null && provider != "cpu") {
                Log.w(TAG, "│ $provider provider failed: ${initError.message}")
                Log.w(TAG, "│ Falling back to CPU provider…")
                statusMessage = "NNAPI unavailable, falling back to CPU…"
                syncStatus()

                val cpuModelConfig = OfflineTtsModelConfig(
                    kokoro = kokoroConfig, numThreads = 2, debug = false, provider = "cpu"
                )
                val cpuConfig = OfflineTtsConfig(model = cpuModelConfig)
                // Direct call — we already survived native library loading
                resultHolder[0] = OfflineTts(config = cpuConfig)
            } else if (initError != null) {
                throw initError
            }

            kokoroTts = resultHolder[0]
            if (kokoroTts == null) {
                Log.e(TAG, "│ OfflineTts() returned null")
                Log.e(TAG, "└── Kokoro init FAILED ────────────────────────")
                statusMessage = "error: engine returned null"
                errorMessage = "Engine constructor returned null"
                isReady = false
                return false
            }

            // Native init succeeded — clear crash tracking flags
            initPrefs.edit()
                .putBoolean(KEY_INIT_IN_PROGRESS, false)
                .putInt(KEY_INIT_CRASH_COUNT, 0)
                .apply()

            initProgress = 100
            isReady = true
            errorMessage = null
            statusMessage = "ready"
            syncStatus()
            Log.i(TAG, "│ Kokoro engine ready in ${elapsed}ms")
            Log.i(TAG, "└── Kokoro init SUCCESS ───────────────────────")
            true

        } catch (e: Throwable) {
            val elapsed = System.currentTimeMillis() - initStartTime.getAndSet(0)
            // Clear in-progress flag — this was a Java exception, not a native crash
            try {
                ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_INIT_IN_PROGRESS, false).apply()
            } catch (_: Throwable) {}
            Log.e(TAG, "│ Exception after ${elapsed}ms: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "└── Kokoro init FAILED ────────────────────────", e)
            kokoroTts = null
            isReady = false
            errorMessage = e.message ?: "Failed to initialize Kokoro engine"
            statusMessage = "error: ${errorMessage}"
            syncStatus()
            false
        }
    }

    /** Kept for backward compat — delegates to initializeKokoro */
    @Synchronized
    fun initialize(ctx: Context): Boolean = initializeKokoro(ctx)

    // ── Kokoro synthesis ──────────────────────────────────────────────────────

    /**
     * Synthesize with Kokoro engine (30 bundled voices).
     * @param sid Speaker ID (from KokoroVoice.sid)
     */
    @Synchronized
    fun synthesize(text: String, sid: Int = 0, speed: Float = 1.0f): Pair<FloatArray, Int>? {
        val engine = kokoroTts ?: return null
        return try {
            val audio = engine.generate(text = text, sid = sid, speed = speed)
            lastSampleRate = audio.sampleRate
            Pair(audio.samples, audio.sampleRate)
        } catch (e: Throwable) {
            Log.e(TAG, "Kokoro synthesis failed", e)
            null
        }
    }

    // ── Piper/VITS synthesis ──────────────────────────────────────────────────

    /**
     * Synthesize with a Piper/VITS voice. Loads the voice model on demand
     * and caches it for reuse (avoids reload if same voice is used again).
     */
    @Synchronized
    fun synthesizePiper(ctx: Context, text: String, voiceId: String, speed: Float = 1.0f): Pair<FloatArray, Int>? {
        if (text.isBlank()) return null

        // Reuse cached engine if same voice
        if (piperTts == null || piperLoadedVoiceId != voiceId) {
            if (!loadPiperVoice(ctx, voiceId)) return null
        }

        val engine = piperTts ?: return null
        return try {
            val audio = engine.generate(text = text, sid = 0, speed = speed)
            lastSampleRate = audio.sampleRate
            Pair(audio.samples, audio.sampleRate)
        } catch (e: Throwable) {
            Log.e(TAG, "Piper synthesis failed for $voiceId", e)
            null
        }
    }

    private fun loadPiperVoice(ctx: Context, voiceId: String): Boolean {
        try {
            val voice = PiperVoiceCatalog.byId(voiceId)

            // Determine where the model lives: assets (bundled) or filesDir (downloaded)
            val isBundled = voice?.bundled == true
            val downloadedFile = VoiceDownloadManager.getVoiceFile(ctx, voiceId)
            val isDownloaded = downloadedFile.exists()

            if (!isBundled && !isDownloaded) {
                Log.e(TAG, "Piper voice not available: $voiceId (not bundled, not downloaded)")
                return false
            }

            // Release previous Piper engine
            piperTts?.let { try { it.release() } catch (e: Exception) { Log.w(TAG, "Error releasing previous Piper engine", e) } }
            piperTts = null
            piperLoadedVoiceId = null

            // Both bundled and downloaded voices use file-based constructor.
            // AssetManager-based constructor crashes on some devices (Xiaomi/MediaTek).
            val tokensFile = ensureTokensFile(ctx)
            val espeakDir = ensureEspeakData(ctx)

            val modelFilePath: String
            if (isBundled) {
                // Extract bundled Piper model from assets to filesystem
                val extractedModel = extractBundledPiperModel(ctx, voiceId)
                if (extractedModel == null) {
                    Log.e(TAG, "Failed to extract bundled Piper voice: $voiceId")
                    return false
                }
                modelFilePath = extractedModel.absolutePath
                Log.d(TAG, "Loading Piper voice from extracted file: $voiceId")
            } else {
                modelFilePath = downloadedFile.absolutePath
                Log.d(TAG, "Loading Piper voice from download: $voiceId (${downloadedFile.length() / 1024 / 1024}MB)")
            }

            val vitsConfig = OfflineTtsVitsModelConfig(
                model   = modelFilePath,
                tokens  = tokensFile.absolutePath,
                dataDir = espeakDir.absolutePath
            )
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig, numThreads = optimalThreadCount(),
                debug = false, provider = optimalProvider()
            )
            piperTts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))

            piperLoadedVoiceId = voiceId
            Log.d(TAG, "Piper voice loaded: $voiceId (${if (isBundled) "bundled" else "downloaded"})")
            return true

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load Piper voice $voiceId", e)
            return false
        }
    }

    /**
     * Extract a bundled Piper model from APK assets to the filesystem.
     * Returns the extracted .onnx/.ort file, or null on failure.
     * Uses atomic writes and version tracking to prevent corrupt/stale files.
     */
    private fun extractBundledPiperModel(ctx: Context, voiceId: String): File? {
        synchronized(extractionLock) {
            val destDir = File(ctx.filesDir, "piper-models")
            destDir.mkdirs()

            val assetPath = resolveModel(ctx, "$PIPER_DIR/$voiceId.onnx")
            val fileName = assetPath.substringAfterLast("/")
            val destFile = File(destDir, fileName)

            // Re-extract if file missing, empty, or APK was updated
            if (destFile.exists() && destFile.length() > 0 && isExtractionCurrent(destDir, ctx)) {
                return destFile
            }

            return try {
                Log.i(TAG, "Extracting bundled Piper model: $fileName")
                extractAssetAtomic(ctx, assetPath, destFile)
                writeVersionMarker(destDir, ctx)
                Log.i(TAG, "Extracted $fileName: ${destFile.length() / 1024 / 1024}MB")
                destFile
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to extract bundled Piper model: $voiceId", e)
                null
            }
        }
    }

    /**
     * Ensure tokens.txt is available on the filesystem for Piper voices.
     * Re-extracts if missing or if the APK was updated (tokens may change).
     */
    private fun ensureTokensFile(ctx: Context): File {
        val dir = VoiceDownloadManager.getDownloadDir(ctx)
        val tokensFile = File(dir, "tokens.txt")
        if (tokensFile.exists() && tokensFile.length() > 0 && isExtractionCurrent(dir, ctx)) {
            return tokensFile
        }
        synchronized(extractionLock) {
            if (tokensFile.exists() && tokensFile.length() > 0 && isExtractionCurrent(dir, ctx)) {
                return tokensFile
            }
            extractAssetAtomic(ctx, "$PIPER_DIR/tokens.txt", tokensFile)
            writeVersionMarker(dir, ctx)
        }
        return tokensFile
    }

    /**
     * Ensure espeak-ng-data is available on the filesystem.
     * Re-extracts if missing or if the APK was updated.
     */
    private fun ensureEspeakData(ctx: Context): File {
        val baseDir = File(ctx.filesDir, "kokoro-model")
        val espeakDir = File(baseDir, "espeak-ng-data")
        if (espeakDir.exists() && (espeakDir.listFiles()?.size ?: 0) > 0
            && isExtractionCurrent(baseDir, ctx)) return espeakDir
        synchronized(extractionLock) {
            // Double-check after acquiring lock
            if (espeakDir.exists() && (espeakDir.listFiles()?.size ?: 0) > 0
                && isExtractionCurrent(baseDir, ctx)) return espeakDir
            if (espeakDir.exists()) espeakDir.deleteRecursively()
            espeakDir.mkdirs()
            copyAssetDir(ctx, "$KOKORO_DIR/espeak-ng-data", espeakDir)
            writeVersionMarker(baseDir, ctx)
        }
        return espeakDir
    }

    // ── Model extraction (hardened) ───────────────────────────────────────────

    /** Lock to prevent concurrent extraction from multiple threads/processes. */
    private val extractionLock = Object()

    /** Name of the marker file that tracks which APK version was extracted. */
    private const val VERSION_MARKER = ".extracted_version"

    /**
     * Returns the app's versionCode, used to detect APK updates.
     * When the APK is updated, extracted model files must be re-extracted
     * because the bundled model may have changed.
     */
    private fun getAppVersionCode(ctx: Context): Long {
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) { 0L }
    }

    /**
     * Checks if extracted files match the current APK version.
     * Returns true if the version marker exists and matches.
     */
    private fun isExtractionCurrent(dir: File, ctx: Context): Boolean {
        val marker = File(dir, VERSION_MARKER)
        if (!marker.exists()) return false
        return try {
            marker.readText().trim() == getAppVersionCode(ctx).toString()
        } catch (_: Throwable) { false }
    }

    /** Writes a version marker after successful extraction. */
    private fun writeVersionMarker(dir: File, ctx: Context) {
        try {
            File(dir, VERSION_MARKER).writeText(getAppVersionCode(ctx).toString())
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to write version marker", e)
        }
    }

    /**
     * Atomically copies an asset to a destination file.
     * Writes to a .tmp file first, then renames. If the process is killed
     * mid-copy, only the .tmp file is left (and will be overwritten next time).
     */
    private fun extractAssetAtomic(ctx: Context, assetPath: String, destFile: File) {
        val tmpFile = File(destFile.parent, "${destFile.name}.tmp")
        try {
            ctx.assets.open(assetPath).use { input ->
                tmpFile.outputStream().use { output -> input.copyTo(output) }
            }
            // Atomic rename — either the full file appears or nothing
            if (!tmpFile.renameTo(destFile)) {
                // renameTo can fail on some filesystems — fall back to copy+delete
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Throwable) {
            tmpFile.delete()  // Clean up partial write
            throw e
        }
    }

    /**
     * Extracts the Kokoro model from APK assets to the filesystem.
     *
     * The AssetManager-based OfflineTts constructor crashes with SIGSEGV on
     * some devices (Xiaomi/MediaTek) due to HWUI mutex corruption when native
     * code accesses the APK via AssetManager. By extracting model files to
     * the filesystem first, we can use the file-based constructor which
     * avoids this entirely.
     *
     * Hardened against:
     *   - Partial writes: uses atomic write (tmp + rename)
     *   - Stale files after APK update: version marker invalidates old extraction
     *   - Concurrent access: synchronized on extractionLock
     *
     * Returns the extraction directory, or null on failure.
     */
    private fun extractKokoroModel(ctx: Context): File? {
        synchronized(extractionLock) {
            val destDir = File(ctx.filesDir, "kokoro-model")
            destDir.mkdirs()

            // Determine which model file to extract (.ort preferred over .onnx)
            val modelAsset = resolveModel(ctx, "$KOKORO_DIR/model.onnx")
            val modelFileName = modelAsset.substringAfterLast("/")
            val modelFile = File(destDir, modelFileName)

            val voicesFile = File(destDir, "voices.bin")
            val tokensFile = File(destDir, "tokens.txt")
            val espeakDir = File(destDir, "espeak-ng-data")

            // Check if already fully extracted AND matches current APK version
            if (isExtractionCurrent(destDir, ctx) &&
                modelFile.exists() && modelFile.length() > 0 &&
                voicesFile.exists() && voicesFile.length() > 0 &&
                tokensFile.exists() && tokensFile.length() > 0 &&
                espeakDir.exists() && (espeakDir.listFiles()?.size ?: 0) > 0
            ) {
                Log.d(TAG, "Kokoro model already extracted (version current)")
                return destDir
            }

            Log.i(TAG, "│ Extracting Kokoro model from assets to filesystem…")

            // Clean up any leftover .tmp files from previous failed extraction
            destDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }

            // Extract model file
            if (!modelFile.exists() || modelFile.length() == 0L || !isExtractionCurrent(destDir, ctx)) {
                Log.i(TAG, "│   Extracting $modelFileName…")
                extractAssetAtomic(ctx, modelAsset, modelFile)
                Log.i(TAG, "│   $modelFileName: ${modelFile.length() / 1024 / 1024}MB")
            }

            // Extract voices.bin
            if (!voicesFile.exists() || voicesFile.length() == 0L || !isExtractionCurrent(destDir, ctx)) {
                Log.i(TAG, "│   Extracting voices.bin…")
                extractAssetAtomic(ctx, "$KOKORO_DIR/voices.bin", voicesFile)
            }

            // Extract tokens.txt
            if (!tokensFile.exists() || tokensFile.length() == 0L || !isExtractionCurrent(destDir, ctx)) {
                Log.i(TAG, "│   Extracting tokens.txt…")
                extractAssetAtomic(ctx, "$KOKORO_DIR/tokens.txt", tokensFile)
            }

            // Extract espeak-ng-data directory tree
            if (!espeakDir.exists() || (espeakDir.listFiles()?.size ?: 0) == 0 || !isExtractionCurrent(destDir, ctx)) {
                Log.i(TAG, "│   Extracting espeak-ng-data/…")
                // Delete old espeak data on version change to avoid stale files
                if (espeakDir.exists()) espeakDir.deleteRecursively()
                espeakDir.mkdirs()
                copyAssetDir(ctx, "$KOKORO_DIR/espeak-ng-data", espeakDir)
            }

            // Mark extraction as complete for this APK version
            writeVersionMarker(destDir, ctx)

            Log.i(TAG, "│ Kokoro model extraction complete → ${destDir.absolutePath}")
            return destDir
        }
    }

    private fun copyAssetDir(ctx: Context, assetPath: String, destDir: File) {
        val children = ctx.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            // It's a file — copy it atomically
            val destFile = File(destDir.parentFile, destDir.name)
            extractAssetAtomic(ctx, assetPath, destFile)
        } else {
            destDir.mkdirs()
            for (child in children) {
                val childDest = File(destDir, child)
                val childAsset = "$assetPath/$child"
                val subChildren = ctx.assets.list(childAsset)
                if (subChildren != null && subChildren.isNotEmpty()) {
                    copyAssetDir(ctx, childAsset, childDest)
                } else {
                    extractAssetAtomic(ctx, childAsset, childDest)
                }
            }
        }
    }

    /**
     * Pre-load a Piper voice model without synthesizing.
     * Call from a background thread to eliminate first-synthesis lag.
     */
    @Synchronized
    fun preloadPiperVoice(ctx: Context, voiceId: String): Boolean {
        if (piperTts != null && piperLoadedVoiceId == voiceId) return true
        return loadPiperVoice(ctx, voiceId)
    }

    // ── Release ───────────────────────────────────────────────────────────────

    @Synchronized
    fun release() {
        try { kokoroTts?.release() } catch (e: Throwable) { /* ignore */ }
        try { piperTts?.release() } catch (e: Throwable) { /* ignore */ }
        kokoroTts = null
        piperTts = null
        piperLoadedVoiceId = null
        isReady = false
        statusMessage = "released"
        syncStatus()
        Log.d(TAG, "SherpaEngine released (Kokoro + Piper)")
    }
}

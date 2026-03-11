package com.kokoro.reader

import android.content.Context
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
 * Models are bundled in the APK's assets and loaded directly via AssetManager —
 * no extraction to internal storage is needed.
 *
 * Thread-safe: synthesize methods are called from the AudioPipeline background thread.
 */
object SherpaEngine {

    private const val TAG = "SherpaEngine"

    // Asset paths (relative to assets/)
    private const val KOKORO_DIR = "kokoro-model"
    private const val PIPER_DIR  = "piper-models"

    /** Maximum time (ms) to wait for native OfflineTts constructor before giving up. */
    private const val INIT_TIMEOUT_MS = 45_000L

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
            voiceCmdWakeWord = VoiceCommandListener.wakeWord
        )
    }

    /** Timestamp (ms) when initialization started, 0 if not initializing */
    private val initStartTime = AtomicLong(0)

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

    /**
     * Initializes the Kokoro engine on a background thread.
     * Models are read directly from APK assets — no extraction step.
     * Guarded against duplicate concurrent calls.
     */
    fun warmUp(ctx: Context) {
        statusContext = ctx.applicationContext
        if (isReady && kokoroTts != null) { onReadyCallback?.invoke(); return }
        synchronized(warmUpLock) {
            if (isWarmingUp) return
            isWarmingUp = true
        }
        Thread {
            try {
                if (initializeKokoro(ctx)) {
                    onReadyCallback?.invoke()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Warm-up failed", e)
                errorMessage = e.message ?: "Unknown error during warm-up"
                statusMessage = "error: ${errorMessage}"
            } finally {
                synchronized(warmUpLock) { isWarmingUp = false }
            }
        }.apply { name = "SherpaEngine-warmup"; isDaemon = true; start() }
    }

    // ── Kokoro initialization ─────────────────────────────────────────────────

    @Synchronized
    fun initializeKokoro(ctx: Context): Boolean {
        if (isReady && kokoroTts != null) return true

        return try {
            statusMessage = "verifying model assets…"
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

            statusMessage = "preparing Kokoro config…"
            val modelPath = resolveModel(ctx, "$KOKORO_DIR/model.onnx")
            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model   = modelPath,
                voices  = "$KOKORO_DIR/voices.bin",
                tokens  = "$KOKORO_DIR/tokens.txt",
                dataDir = "$KOKORO_DIR/espeak-ng-data"
            )

            val modelConfig = OfflineTtsModelConfig(
                kokoro     = kokoroConfig,
                numThreads = 2,
                debug      = false,
                provider   = "cpu"
            )

            val config = OfflineTtsConfig(model = modelConfig)

            val isOrt = modelPath.endsWith(".ort")
            statusMessage = if (isOrt) "loading optimized model…" else "loading native model (this may take 10-30s)…"
            Log.i(TAG, "│ Calling OfflineTts() — native JNI constructor…")
            initStartTime.set(System.currentTimeMillis())

            // Run the native constructor on a separate thread with a timeout.
            // OfflineTts() can SIGSEGV or hang indefinitely on some devices.
            val resultHolder = arrayOfNulls<OfflineTts>(1)
            val errorHolder = arrayOfNulls<Throwable>(1)
            val latch = CountDownLatch(1)

            val initThread = Thread {
                try {
                    resultHolder[0] = OfflineTts(assetManager = ctx.assets, config = config)
                } catch (e: Throwable) {
                    errorHolder[0] = e
                } finally {
                    latch.countDown()
                }
            }.apply { name = "SherpaEngine-native-init"; isDaemon = true; start() }

            val completed = latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val elapsed = System.currentTimeMillis() - initStartTime.get()
            initStartTime.set(0)

            if (!completed) {
                // Native init hung — don't crash, but report clearly
                Log.e(TAG, "│ OfflineTts() TIMED OUT after ${elapsed}ms")
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

            // Check if native init threw
            errorHolder[0]?.let { throw it }

            kokoroTts = resultHolder[0]
            if (kokoroTts == null) {
                Log.e(TAG, "│ OfflineTts() returned null")
                Log.e(TAG, "└── Kokoro init FAILED ────────────────────────")
                statusMessage = "error: engine returned null"
                errorMessage = "Engine constructor returned null"
                isReady = false
                return false
            }

            isReady = true
            errorMessage = null
            statusMessage = "ready"
            syncStatus()
            Log.i(TAG, "│ Kokoro engine ready in ${elapsed}ms")
            Log.i(TAG, "└── Kokoro init SUCCESS ───────────────────────")
            true

        } catch (e: Throwable) {
            val elapsed = System.currentTimeMillis() - initStartTime.getAndSet(0)
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

            if (isBundled) {
                // Load from APK assets
                val resolvedPath = resolveModel(ctx, "$PIPER_DIR/$voiceId.onnx")
                Log.d(TAG, "Loading Piper voice from assets: $voiceId")

                val vitsConfig = OfflineTtsVitsModelConfig(
                    model   = resolvedPath,
                    tokens  = "$PIPER_DIR/tokens.txt",
                    dataDir = "$KOKORO_DIR/espeak-ng-data"
                )
                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig, numThreads = 2, debug = false, provider = "cpu"
                )
                piperTts = OfflineTts(assetManager = ctx.assets, config = OfflineTtsConfig(model = modelConfig))
            } else {
                // Load from downloaded file path
                // tokens.txt is still in assets — copy to filesDir if not already there
                val tokensFile = ensureTokensFile(ctx)
                val espeakDir = ensureEspeakData(ctx)
                Log.d(TAG, "Loading Piper voice from file: $voiceId (${downloadedFile.length() / 1024 / 1024}MB)")

                val vitsConfig = OfflineTtsVitsModelConfig(
                    model   = downloadedFile.absolutePath,
                    tokens  = tokensFile.absolutePath,
                    dataDir = espeakDir.absolutePath
                )
                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig, numThreads = 2, debug = false, provider = "cpu"
                )
                piperTts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
            }

            piperLoadedVoiceId = voiceId
            Log.d(TAG, "Piper voice loaded: $voiceId (${if (isBundled) "bundled" else "downloaded"})")
            return true

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load Piper voice $voiceId", e)
            return false
        }
    }

    /**
     * Ensure tokens.txt is available on the filesystem for downloaded voices.
     * Copies from assets/piper-models/tokens.txt on first call.
     */
    private fun ensureTokensFile(ctx: Context): File {
        val dir = VoiceDownloadManager.getDownloadDir(ctx)
        val tokensFile = File(dir, "tokens.txt")
        if (!tokensFile.exists()) {
            ctx.assets.open("$PIPER_DIR/tokens.txt").use { input ->
                tokensFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return tokensFile
    }

    /**
     * Ensure espeak-ng-data is available on the filesystem for downloaded voices.
     * Copies the entire directory tree from assets on first call.
     */
    private fun ensureEspeakData(ctx: Context): File {
        val baseDir = File(ctx.filesDir, "kokoro-model")
        val espeakDir = File(baseDir, "espeak-ng-data")
        if (espeakDir.exists() && (espeakDir.listFiles()?.size ?: 0) > 0) return espeakDir
        espeakDir.mkdirs()
        copyAssetDir(ctx, "$KOKORO_DIR/espeak-ng-data", espeakDir)
        return espeakDir
    }

    private fun copyAssetDir(ctx: Context, assetPath: String, destDir: File) {
        val children = ctx.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            // It's a file — copy it
            ctx.assets.open(assetPath).use { input ->
                File(destDir.parentFile, destDir.name).also { dest ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } else {
            destDir.mkdirs()
            for (child in children) {
                val childDest = File(destDir, child)
                val childAsset = "$assetPath/$child"
                val subChildren = ctx.assets.list(childAsset)
                if (subChildren != null && subChildren.isNotEmpty()) {
                    copyAssetDir(ctx, childAsset, childDest)
                } else {
                    ctx.assets.open(childAsset).use { input ->
                        childDest.outputStream().use { output -> input.copyTo(output) }
                    }
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

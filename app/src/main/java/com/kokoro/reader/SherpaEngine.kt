package com.kokoro.reader

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.util.Log
import java.io.File
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.SharedPreferences
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /** Base timeout (ms) — actual timeout is adaptive based on device + model format. */
    private const val BASE_TIMEOUT_ORT_MS = 30_000L
    private const val BASE_TIMEOUT_ONNX_MS = 90_000L

    /** Minimum free disk space (bytes) required before extracting the Kokoro model. */
    private const val MIN_DISK_SPACE_BYTES = 400L * 1024 * 1024  // 400 MB

    /** Minimum free RAM (bytes) required before attempting native model loading. */
    private const val MIN_RAM_FOR_INIT_BYTES = 350L * 1024 * 1024  // 350 MB

    /** Minimum expected model.ort/.onnx file size — below this, extraction is corrupt. */
    private const val MIN_MODEL_FILE_SIZE = 100L * 1024 * 1024  // 100 MB

    /** Pre-warming: read model file in 256KB chunks to populate OS page cache. */
    private const val PREWARM_BUFFER_SIZE = 256 * 1024

    // ── Configuration memory keys ────────────────────────────────────────────────
    // Persists the last config that successfully loaded Kokoro. On next launch,
    // skips the entire escalation ladder and goes straight to the known-good config.
    // Invalidated on app update (model or native lib may have changed).
    private const val KEY_LAST_GOOD_THREADS  = "last_good_threads"
    private const val KEY_LAST_GOOD_PROVIDER = "last_good_provider"
    private const val KEY_LAST_GOOD_DEBUG    = "last_good_debug"
    private const val KEY_LAST_GOOD_VERSION  = "last_good_version"

    // ── Init config (used by escalation ladder + config memory) ──────────────────

    private data class InitConfig(
        val threads: Int,
        val provider: String,
        val debug: Boolean,
        val label: String
    )

    // ── Device-adaptive engine configuration ────────────────────────────────────

    /** Detected SoC vendor — cached at first access. */
    enum class SocVendor { MEDIATEK, QUALCOMM, OTHER }

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

    // ── Device profiling ─────────────────────────────────────────────────────────
    // Comprehensive device capability assessment. Drives timeout, memory thresholds,
    // escalation strategy, and thread priority decisions. This is what separates a
    // world-class engine from a "hope it works" engine.

    enum class DeviceTier { HIGH, MEDIUM, LOW }

    data class DeviceProfile(
        val totalRamMB: Long,
        val availRamMB: Long,
        val cpuCores: Int,
        val socVendor: SocVendor,
        val apiLevel: Int,
        val isXiaomi: Boolean,
        /** True if running MIUI or HyperOS (Xiaomi's custom Android ROMs). */
        val isXiaomiRom: Boolean,
        val tier: DeviceTier,
        val manufacturer: String,
        val model: String
    ) {
        /** Timeout multiplier — low-tier devices get more time, not less. */
        val timeoutMultiplier: Float get() = when (tier) {
            DeviceTier.HIGH -> 0.75f
            DeviceTier.MEDIUM -> 1.0f
            DeviceTier.LOW -> 1.5f
        }

        override fun toString(): String = buildString {
            append("DeviceProfile(")
            append("$manufacturer $model, ")
            append("${totalRamMB}MB RAM, ${availRamMB}MB free, ")
            append("${cpuCores} cores, $socVendor, API $apiLevel, ")
            append("tier=$tier")
            if (isXiaomi) append(", Xiaomi")
            if (isXiaomiRom) append(", MIUI/HyperOS")
            append(")")
        }
    }

    private fun profileDevice(ctx: Context): DeviceProfile {
        val actMgr = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)

        val totalRamMB = memInfo.totalMem / 1024 / 1024
        val availRamMB = memInfo.availMem / 1024 / 1024
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val mfr = Build.MANUFACTURER.lowercase()
        val isXiaomi = mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco")
        // Xiaomi ROM detection: MIUI uses "ro.miui.ui.version.name",
        // HyperOS (MIUI 15+) uses "ro.mi.os.version.incremental".
        // Both ROMs share the same aggressive background killing and CPU throttling.
        val isXiaomiRom = isXiaomi && try {
            @Suppress("PrivateApi")
            val sysPropClass = Class.forName("android.os.SystemProperties")
            val getMethod = sysPropClass.getMethod("get", String::class.java)
            val miui = getMethod.invoke(null, "ro.miui.ui.version.name")?.toString().orEmpty()
            val hyperOs = getMethod.invoke(null, "ro.mi.os.version.incremental")?.toString().orEmpty()
            miui.isNotEmpty() || hyperOs.isNotEmpty()
        } catch (_: Throwable) { false }

        val tier = when {
            totalRamMB >= 6000 && cpuCores >= 6 && !memInfo.lowMemory -> DeviceTier.HIGH
            totalRamMB >= 3000 && cpuCores >= 4 -> DeviceTier.MEDIUM
            else -> DeviceTier.LOW
        }

        return DeviceProfile(
            totalRamMB, availRamMB, cpuCores, socVendor,
            Build.VERSION.SDK_INT, isXiaomi, isXiaomiRom, tier,
            Build.MANUFACTURER, Build.MODEL
        )
    }

    // ── Config memory ────────────────────────────────────────────────────────────
    // Remembers the exact config that worked. Skips escalation on next launch.
    // Google TTS does this — learn from success, don't re-probe every time.

    private fun loadGoodConfig(prefs: SharedPreferences, appVersion: Long): InitConfig? {
        val savedVersion = prefs.getLong(KEY_LAST_GOOD_VERSION, -1)
        if (savedVersion != appVersion) return null  // App updated — re-probe
        val threads = prefs.getInt(KEY_LAST_GOOD_THREADS, -1)
        val provider = prefs.getString(KEY_LAST_GOOD_PROVIDER, null)
        val debug = prefs.getBoolean(KEY_LAST_GOOD_DEBUG, false)
        if (threads < 1 || provider == null) return null
        return InitConfig(threads, provider, debug, "remembered ($threads threads, $provider)")
    }

    private fun saveGoodConfig(prefs: SharedPreferences, config: InitConfig, appVersion: Long) {
        prefs.edit()
            .putInt(KEY_LAST_GOOD_THREADS, config.threads)
            .putString(KEY_LAST_GOOD_PROVIDER, config.provider)
            .putBoolean(KEY_LAST_GOOD_DEBUG, config.debug)
            .putLong(KEY_LAST_GOOD_VERSION, appVersion)
            .apply()
    }

    private fun clearGoodConfig(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_LAST_GOOD_THREADS)
            .remove(KEY_LAST_GOOD_PROVIDER)
            .remove(KEY_LAST_GOOD_DEBUG)
            .remove(KEY_LAST_GOOD_VERSION)
            .apply()
    }

    /**
     * Checks Android 11+ ApplicationExitInfo to determine if the last process
     * death was a native crash (SIGSEGV) vs OS-initiated kill (HyperOS battery
     * management, OOM killer, etc).
     *
     * Returns true if the last exit was definitely a native crash, or if we
     * can't determine the reason (pre-Android 11 — assume crash for safety).
     */
    private fun wasLastExitNativeCrash(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Pre-Android 11: no exit info API, assume crash for safety
            return true
        }
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // Get recent exit reasons for our package, limit to 5
            val exitInfos = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 5)
            // Find the most recent exit for the :tts process
            val ttsExit = exitInfos.firstOrNull { info ->
                info.processName?.endsWith(":tts") == true
            }
            if (ttsExit == null) {
                plog("wasLastExitNativeCrash: no exit info for :tts — assuming crash")
                return true
            }
            val reason = ttsExit.reason
            val desc = ttsExit.description ?: "none"
            plog("wasLastExitNativeCrash: reason=$reason (${exitReasonName(reason)}), desc=$desc, " +
                "importance=${ttsExit.importance}, status=${ttsExit.status}")
            // REASON_CRASH_NATIVE (6) = SIGSEGV/SIGABRT/etc — definitely a native crash
            // REASON_CRASH (4) = uncaught Java exception — treat as crash
            // REASON_ANR (3) = ANR — treat as crash (init took too long)
            // Everything else = OS killed it (not the native code's fault)
            when (reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_ANR -> {
                    Log.w(TAG, "Last :tts exit was ${exitReasonName(reason)}: $desc")
                    true
                }
                else -> {
                    Log.i(TAG, "Last :tts exit was ${exitReasonName(reason)} (not native crash): $desc")
                    false
                }
            }
        } catch (e: Throwable) {
            plog("wasLastExitNativeCrash: exception ${e.message} — assuming crash")
            true  // Can't determine — assume crash for safety
        }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "ANR"
        4 -> "CRASH"
        5 -> "DEPENDENCY_DIED"
        6 -> "CRASH_NATIVE"
        7 -> "OTHER"
        8 -> "LOW_MEMORY"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "PACKAGE_STATE_CHANGE"
        14 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN($reason)"
    }

    // ── Diagnostics (shown in error UI when native code is broken) ──────────────

    private fun buildDiagnostics(ctx: Context): String {
        return buildString {
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            append(" | SoC: $socVendor (${Build.HARDWARE})")
            append(" | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val exitInfos = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 3)
                    val ttsExit = exitInfos.firstOrNull { it.processName?.endsWith(":tts") == true }
                    if (ttsExit != null) {
                        append(" | Last exit: ${exitReasonName(ttsExit.reason)}")
                        ttsExit.description?.let { append(" ($it)") }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    // ── Android system TTS fallback ──────────────────────────────────────────────
    // When sherpa-onnx native code is broken on the device, fall back to the
    // Android system TTS (e.g. Google TTS). Returns PCM audio through the normal
    // pipeline so DSP effects still work.

    private fun initSystemTtsFallback(ctx: Context): Boolean {
        plog("initSystemTtsFallback: initializing Android system TTS")
        val latch = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        try {
            val tts = TextToSpeech(ctx.applicationContext) { status ->
                initStatus = status
                latch.countDown()
            }
            // Wait up to 5 seconds for system TTS to init
            if (!latch.await(5, TimeUnit.SECONDS)) {
                plog("initSystemTtsFallback: timeout waiting for system TTS init")
                tts.shutdown()
                return false
            }
            if (initStatus != TextToSpeech.SUCCESS) {
                plog("initSystemTtsFallback: system TTS init failed (status=$initStatus)")
                tts.shutdown()
                return false
            }
            // Set language to English (fallback)
            val langResult = tts.setLanguage(java.util.Locale.US)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                plog("initSystemTtsFallback: English not available, trying default locale")
                tts.setLanguage(java.util.Locale.getDefault())
            }
            systemTts = tts
            plog("initSystemTtsFallback: system TTS ready (engine=${tts.defaultEngine})")
            Log.i(TAG, "Android system TTS ready: ${tts.defaultEngine}")
            return true
        } catch (e: Throwable) {
            plog("initSystemTtsFallback: exception: ${e.message}")
            Log.e(TAG, "System TTS init failed", e)
            return false
        }
    }

    /**
     * Synthesize text using Android system TTS → WAV file → read PCM.
     * Returns the same Pair<FloatArray, Int> as the sherpa-onnx methods.
     */
    fun synthesizeSystemTts(text: String, speed: Float = 1.0f): Pair<FloatArray, Int>? {
        val tts = systemTts ?: return null
        if (text.isBlank()) return null
        try {
            tts.setSpeechRate(speed)
            val tmpFile = File.createTempFile("sys_tts_", ".wav", statusContext?.cacheDir)
            val utteranceId = "synth_${System.nanoTime()}"
            val latch = CountDownLatch(1)
            var synthOk = false

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { synthOk = true; latch.countDown() }
                @Deprecated("Deprecated") override fun onError(id: String?) { latch.countDown() }
                override fun onError(id: String?, errorCode: Int) { latch.countDown() }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = tts.synthesizeToFile(text, params, tmpFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                tmpFile.delete()
                return null
            }

            // Wait up to 30 seconds for synthesis
            if (!latch.await(30, TimeUnit.SECONDS) || !synthOk) {
                tmpFile.delete()
                return null
            }

            // Read WAV file → extract PCM float samples
            val pcmResult = readWavAsPcmFloat(tmpFile)
            tmpFile.delete()
            if (pcmResult != null) lastSampleRate = pcmResult.second
            return pcmResult
        } catch (e: Throwable) {
            Log.e(TAG, "System TTS synthesis failed", e)
            return null
        }
    }

    /** Read a WAV file and return PCM data as FloatArray + sample rate. */
    private fun readWavAsPcmFloat(wavFile: File): Pair<FloatArray, Int>? {
        try {
            val bytes = wavFile.readBytes()
            if (bytes.size < 44) return null  // WAV header is 44 bytes minimum
            // Parse WAV header
            // Bytes 24-27: sample rate (little-endian)
            val sampleRate = (bytes[24].toInt() and 0xFF) or
                ((bytes[25].toInt() and 0xFF) shl 8) or
                ((bytes[26].toInt() and 0xFF) shl 16) or
                ((bytes[27].toInt() and 0xFF) shl 24)
            // Bytes 34-35: bits per sample
            val bitsPerSample = (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8)
            // Find "data" chunk
            var dataOffset = 12
            var dataSize = 0
            while (dataOffset < bytes.size - 8) {
                val chunkId = String(bytes, dataOffset, 4)
                val chunkSize = (bytes[dataOffset + 4].toInt() and 0xFF) or
                    ((bytes[dataOffset + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[dataOffset + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[dataOffset + 7].toInt() and 0xFF) shl 24)
                if (chunkId == "data") {
                    dataOffset += 8
                    dataSize = chunkSize
                    break
                }
                dataOffset += 8 + chunkSize
            }
            if (dataSize == 0) return null
            // Convert to float samples
            val samples = when (bitsPerSample) {
                16 -> {
                    val numSamples = dataSize / 2
                    FloatArray(numSamples) { i ->
                        val lo = bytes[dataOffset + i * 2].toInt() and 0xFF
                        val hi = bytes[dataOffset + i * 2 + 1].toInt()
                        ((hi shl 8) or lo).toShort().toFloat() / 32768f
                    }
                }
                8 -> {
                    FloatArray(dataSize) { i ->
                        (bytes[dataOffset + i].toInt() and 0xFF).toFloat() / 128f - 1f
                    }
                }
                else -> return null
            }
            return Pair(samples, sampleRate)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read WAV file", e)
            return null
        }
    }

    // ── Model pre-warming ────────────────────────────────────────────────────────
    // Sequentially reads the entire model file to populate the OS page cache.
    // When ORT opens the file via mmap, ALL pages are already in RAM — no random
    // page faults during model parsing. This is critical on low-memory devices
    // where random page faults during mmap can trigger the OOM killer (SIGSEGV).

    private fun prewarmModelFile(modelFile: File): Long {
        val start = System.currentTimeMillis()
        try {
            val fileSize = modelFile.length()
            // Use mmap + MappedByteBuffer.load() for aggressive page residency.
            // This forces the OS to page in the ENTIRE file and pin it in RAM.
            // When ORT opens the same file via its own mmap, all pages are already
            // resident — zero page faults, no OOM killer triggers.
            RandomAccessFile(modelFile, "r").use { raf ->
                raf.channel.use { channel ->
                    val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
                    mapped.load()  // Force all pages into physical RAM
                    val elapsed = System.currentTimeMillis() - start
                    val speedKBs = if (elapsed > 0) fileSize / elapsed else 0
                    Log.i(TAG, "│   ${fileSize / 1024 / 1024}MB mmap+load in ${elapsed}ms (${speedKBs}KB/s)")
                    return elapsed
                }
            }
        } catch (e: Throwable) {
            // Fallback to sequential read if mmap fails (e.g., on very old devices)
            Log.w(TAG, "│   mmap pre-warm failed (${e.message}), falling back to sequential read")
            try {
                val buffer = ByteArray(PREWARM_BUFFER_SIZE)
                var totalRead = 0L
                modelFile.inputStream().buffered(PREWARM_BUFFER_SIZE).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        totalRead += n
                    }
                }
                val elapsed = System.currentTimeMillis() - start
                Log.i(TAG, "│   ${totalRead / 1024 / 1024}MB sequential read in ${elapsed}ms")
                return elapsed
            } catch (e2: Throwable) {
                Log.w(TAG, "│   Sequential pre-warm also failed: ${e2.message}")
                return System.currentTimeMillis() - start
            }
        }
    }

    // ── Heap reservation ─────────────────────────────────────────────────────────
    // Forces the JVM to expand its heap region BEFORE native code starts allocating.
    // Without this, the JVM may grow its heap DURING native model loading, causing
    // virtual memory region collisions that manifest as SIGSEGV on some Android ROMs
    // (especially MIUI/HyperOS's modified ART runtime).

    private fun reserveAndReleaseHeap(): Long {
        val start = System.currentTimeMillis()
        try {
            val heapMax = Runtime.getRuntime().maxMemory()
            val reserveSize = (heapMax / 4).toInt().coerceIn(8 * 1024 * 1024, 64 * 1024 * 1024)
            val buffer = ByteArray(reserveSize)
            // Touch every page to force physical backing
            for (i in buffer.indices step 4096) { buffer[i] = 1 }
            Log.i(TAG, "│   Heap expanded by ${reserveSize / 1024 / 1024}MB")
            // Buffer goes out of scope → eligible for GC
            // The heap region stays expanded even after GC reclaims the buffer
        } catch (_: OutOfMemoryError) {
            Log.w(TAG, "│   Heap reservation skipped (device very low on memory)")
        }
        System.gc()
        return System.currentTimeMillis() - start
    }

    // ── Adaptive timeout ─────────────────────────────────────────────────────────
    // .ort loads 5-10x faster than .onnx. Low-tier devices need MORE time, not less.
    // A fixed timeout is wrong for both fast and slow devices.

    private fun adaptiveTimeoutMs(isOrt: Boolean, profile: DeviceProfile): Long {
        val baseMs = if (isOrt) BASE_TIMEOUT_ORT_MS else BASE_TIMEOUT_ONNX_MS
        val adjusted = (baseMs * profile.timeoutMultiplier).toLong()
        return adjusted.coerceIn(20_000L, 120_000L)
    }

    // ── Phase telemetry ──────────────────────────────────────────────────────────
    // Granular timing of every init stage. Written to init log for diagnostics.

    data class InitTelemetry(
        var probeMs: Long = 0,
        var extractionMs: Long = 0,
        var gcMs: Long = 0,
        var heapReserveMs: Long = 0,
        var prewarmMs: Long = 0,
        var nativeLoadMs: Long = 0,
        var totalMs: Long = 0,
        var escalationLevel: Int = 0,
        var modelFormat: String = "",
        var configUsed: String = "",
        var deviceProfile: DeviceProfile? = null,
        var timeoutMs: Long = 0
    ) {
        override fun toString(): String = buildString {
            appendLine("Phase Timing:")
            appendLine("  probe       : ${probeMs}ms")
            appendLine("  extraction  : ${extractionMs}ms")
            appendLine("  GC+heap     : ${gcMs + heapReserveMs}ms (gc=${gcMs}ms, heap=${heapReserveMs}ms)")
            appendLine("  prewarm     : ${prewarmMs}ms")
            appendLine("  native load : ${nativeLoadMs}ms")
            appendLine("  TOTAL       : ${totalMs}ms")
            appendLine("Escalation    : level $escalationLevel ($configUsed)")
            appendLine("Model format  : $modelFormat")
            appendLine("Timeout       : ${timeoutMs}ms")
            appendLine("Device        : $deviceProfile")
        }
    }

    // ── Kokoro engine (single model, 30 speakers) ─────────────────────────────
    private var kokoroTts: OfflineTts? = null

    // ── Piper engine (one model per voice, cached) ────────────────────────────
    private var piperTts: OfflineTts? = null
    private var piperLoadedVoiceId: String? = null

    // ── Piper fallback (used when Kokoro init fails) ────────────────────────
    /** Default bundled Piper voice to use when Kokoro is unavailable. */
    private const val PIPER_FALLBACK_VOICE = "en_US-lessac-medium"

    /** True when the Piper fallback engine is initialized and ready to synthesize. */
    @Volatile var isPiperFallbackReady = false
        private set

    /** True when Android system TTS is active as ultimate fallback (native code broken). */
    @Volatile var isSystemTtsFallbackActive = false
        private set

    /** Android system TTS engine — used when native sherpa-onnx crashes on the device. */
    private var systemTts: android.speech.tts.TextToSpeech? = null

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

    // ── Process log: captures every step of init for debug dump ──────────
    private val processLog = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private const val PROCESS_LOG_MAX = 500

    /** Append a timestamped line to the in-memory process log. */
    private fun plog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$ts] $msg"
        processLog.add(line)
        while (processLog.size > PROCESS_LOG_MAX) processLog.poll()
    }

    /** Dump the process log for diagnostic reporting. */
    fun dumpProcessLog(): String = processLog.joinToString("\n")

    /** Debug log directory on user-visible storage. */
    private const val DEBUG_LOG_DIR = "WW_Andene/Kyōkan/Logs"

    /**
     * Dump comprehensive debug log to /storage/emulated/0/WW_Andene/Kyōkan/Logs/.
     * Includes: device info, engine state, crash tracking, config memory,
     * SharedPreferences, full init process log, and current telemetry.
     * Called from the UI via TtsBridge command.
     */
    fun dumpDebugLog(ctx: Context): String? {
        return try {
            val dir = File(android.os.Environment.getExternalStorageDirectory(), DEBUG_LOG_DIR)
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "debug_$timestamp.log")

            val initPrefs = ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)

            file.writeText(buildString {
                appendLine("╔══════════════════════════════════════════════════╗")
                appendLine("║        KYŌKAN FULL DEBUG LOG                    ║")
                appendLine("╚══════════════════════════════════════════════════╝")
                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                appendLine()

                // ── Device Info ──
                appendLine("═══ DEVICE ═══")
                appendLine("Manufacturer : ${Build.MANUFACTURER}")
                appendLine("Model        : ${Build.MODEL}")
                appendLine("Hardware     : ${Build.HARDWARE}")
                appendLine("Android      : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                if (Build.VERSION.SDK_INT >= 31) {
                    appendLine("SoC Mfg      : ${Build.SOC_MANUFACTURER}")
                    appendLine("SoC Model    : ${Build.SOC_MODEL}")
                }
                appendLine("SoC Vendor   : $socVendor")
                appendLine("App version  : ${try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Throwable) { "?" }}")
                try {
                    val vi = com.k2fsa.sherpa.onnx.VersionInfo.Companion
                    appendLine("sherpa-onnx  : ${vi.version} (${vi.gitSha1})")
                } catch (_: Throwable) {
                    appendLine("sherpa-onnx  : version unavailable")
                }
                appendLine()

                // ── Memory ──
                appendLine("═══ MEMORY ═══")
                val runtime = Runtime.getRuntime()
                appendLine("JVM heap used: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024}MB")
                appendLine("JVM heap max : ${runtime.maxMemory() / 1024 / 1024}MB")
                val memInfo = android.app.ActivityManager.MemoryInfo()
                (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
                    ?.getMemoryInfo(memInfo)
                appendLine("RAM available: ${memInfo.availMem / 1024 / 1024}MB")
                appendLine("RAM total    : ${memInfo.totalMem / 1024 / 1024}MB")
                appendLine("Low memory   : ${memInfo.lowMemory}")
                try {
                    val stat = StatFs(ctx.filesDir.absolutePath)
                    appendLine("Disk free    : ${stat.availableBytes / 1024 / 1024}MB")
                } catch (_: Throwable) {}
                appendLine()

                // ── Engine State ──
                appendLine("═══ ENGINE STATE ═══")
                appendLine("isReady          : $isReady")
                appendLine("statusMessage    : $statusMessage")
                appendLine("errorMessage     : $errorMessage")
                appendLine("initProgress     : $initProgress")
                appendLine("isWarmingUp      : $isWarmingUp")
                appendLine("nativeStackProbed: $nativeStackProbed")
                appendLine("kokoroTts        : ${if (kokoroTts != null) "loaded" else "null"}")
                appendLine("piperTts         : ${if (piperTts != null) "loaded (${piperLoadedVoiceId})" else "null"}")
                appendLine("isPiperFallback  : $isPiperFallbackReady")
                appendLine("lastSampleRate   : $lastSampleRate")
                appendLine()

                // ── Crash Tracking ──
                appendLine("═══ CRASH TRACKING (SharedPrefs: $INIT_PREFS) ═══")
                appendLine("init_in_progress : ${initPrefs.getBoolean(KEY_INIT_IN_PROGRESS, false)}")
                appendLine("init_crash_count : ${initPrefs.getInt(KEY_INIT_CRASH_COUNT, 0)}")
                val lastCrash = initPrefs.getLong(KEY_INIT_LAST_CRASH_TIME, 0)
                appendLine("last_crash_time  : ${if (lastCrash > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastCrash)) else "never"}")
                appendLine("crash_age_ms     : ${if (lastCrash > 0) System.currentTimeMillis() - lastCrash else "N/A"}")
                appendLine()

                // ── Config Memory ──
                appendLine("═══ CONFIG MEMORY ═══")
                appendLine("last_good_threads  : ${initPrefs.getInt(KEY_LAST_GOOD_THREADS, -1)}")
                appendLine("last_good_provider : ${initPrefs.getString(KEY_LAST_GOOD_PROVIDER, "none")}")
                appendLine("last_good_debug    : ${initPrefs.getBoolean(KEY_LAST_GOOD_DEBUG, false)}")
                appendLine("last_good_version  : ${initPrefs.getInt(KEY_LAST_GOOD_VERSION, -1)}")
                appendLine()

                // ── Model Files ──
                appendLine("═══ MODEL FILES ═══")
                val kokoroDir = File(ctx.filesDir, "kokoro-model")
                appendLine("Kokoro dir: ${kokoroDir.absolutePath}")
                if (kokoroDir.exists()) {
                    kokoroDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                        if (f.isFile) appendLine("  ${f.name} (${f.length() / 1024}KB)")
                        else appendLine("  ${f.name}/ (dir, ${f.listFiles()?.size ?: 0} files)")
                    }
                } else {
                    appendLine("  NOT EXTRACTED")
                }
                val piperDir = File(ctx.filesDir, "piper-models")
                appendLine("Piper dir: ${piperDir.absolutePath}")
                if (piperDir.exists()) {
                    piperDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                        appendLine("  ${f.name} (${f.length() / 1024}KB)")
                    }
                } else {
                    appendLine("  NOT EXTRACTED")
                }
                appendLine()

                // ── Telemetry ──
                val telem = lastTelemetry
                if (telem != null) {
                    appendLine("═══ LAST TELEMETRY ═══")
                    appendLine(telem.toString())
                    appendLine()
                }

                // ── TTS Status File ──
                appendLine("═══ TTS STATUS FILE ═══")
                try {
                    val statusFile = File(ctx.filesDir, "tts_status.json")
                    if (statusFile.exists()) {
                        appendLine(statusFile.readText())
                    } else {
                        appendLine("(file does not exist)")
                    }
                } catch (e: Throwable) {
                    appendLine("(error reading: ${e.message})")
                }
                appendLine()

                // ── Process Log (every step of init) ──
                appendLine("═══ INIT PROCESS LOG (${processLog.size} entries) ═══")
                processLog.forEach { appendLine(it) }
                if (processLog.isEmpty()) {
                    appendLine("(no init steps recorded yet — engine may not have started)")
                }
            })

            Log.i(TAG, "Debug log written to ${file.absolutePath}")
            file.absolutePath
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write debug log", e)
            null
        }
    }

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

    /**
     * Writes an engine init failure log file so the user can copy it from the UI.
     * Mirrors [ReaderApplication.writeCrashLog] but for non-fatal init failures.
     */
    /** Last telemetry from init — available for the log even after init returns. */
    @Volatile private var lastTelemetry: InitTelemetry? = null

    private fun writeInitLog(
        ctx: Context, reason: String, throwable: Throwable? = null,
        telemetry: InitTelemetry? = lastTelemetry
    ) {
        try {
            // Use resolved log dir, fall back to app filesDir if null (e.g. in :tts process)
            val dir = ReaderApplication.resolvedLogDir
                ?: File(ctx.applicationContext.filesDir, "logs").also { it.mkdirs() }
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
            val file = File(dir, "engine_init_$timestamp.log")

            val stackTrace = if (throwable != null) {
                StringWriter().also { sw -> PrintWriter(sw).use { throwable.printStackTrace(it) } }.toString()
            } else null

            file.writeText(buildString {
                appendLine("=== Kyōkan Engine Init Report ===")
                appendLine("Time       : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                appendLine("Reason     : $reason")
                appendLine()
                appendLine("--- Device Profile ---")
                val dp = telemetry?.deviceProfile
                if (dp != null) {
                    appendLine("Manufacturer: ${dp.manufacturer}")
                    appendLine("Model       : ${dp.model}")
                    appendLine("RAM         : ${dp.availRamMB}MB free / ${dp.totalRamMB}MB total")
                    appendLine("CPU cores   : ${dp.cpuCores}")
                    appendLine("SoC         : ${dp.socVendor} (hw=${Build.HARDWARE})")
                    appendLine("Tier        : ${dp.tier}")
                    appendLine("Xiaomi      : ${dp.isXiaomi}")
                    appendLine("MIUI/HyperOS: ${dp.isXiaomiRom}")
                } else {
                    appendLine("Device     : ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("SoC        : $socVendor (hw=${Build.HARDWARE})")
                }
                appendLine("Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("App version: ${try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "?" }}")
                try {
                    val vi = com.k2fsa.sherpa.onnx.VersionInfo.Companion
                    appendLine("sherpa-onnx : ${vi.version} (${vi.gitSha1})")
                } catch (_: Throwable) {}
                appendLine()
                if (telemetry != null) {
                    appendLine("--- Telemetry ---")
                    append(telemetry.toString())
                    appendLine()
                }
                if (stackTrace != null) {
                    appendLine("--- Stack Trace ---")
                    appendLine(stackTrace)
                }
                appendLine("--- Status ---")
                appendLine("statusMessage: $statusMessage")
                appendLine("errorMessage : $errorMessage")
                appendLine("isReady      : $isReady")
                appendLine("initProgress : $initProgress")
            })
            Log.i(TAG, "Engine init log written to ${file.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write engine init log", e)
        }
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

    /** True after we've verified the JNI/ORT native stack works with a lightweight probe. */
    @Volatile private var nativeStackProbed = false

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
    /** Which model format was being loaded when a crash happened ("ort" or "onnx"). */
    private const val KEY_INIT_FORMAT = "init_format"
    /** If true, skip .ort and go straight to .onnx (set after a crash during .ort loading). */
    private const val KEY_SKIP_ORT = "skip_ort"
    /** Base delay (ms) for exponential backoff after crashes. Delay = base * 2^(n-1). */
    private const val CRASH_BACKOFF_BASE_MS = 5_000L   // 5s, 10s, 20s, 40s, 80s…
    /** Max backoff delay (ms) — caps at ~2.5 minutes, then retries forever at this interval. */
    private const val CRASH_BACKOFF_MAX_MS = 150_000L  // 2.5 minutes
    /** After this many crashes, assume the native stack is fundamentally broken. */
    private const val NATIVE_BROKEN_THRESHOLD = 5
    /** Reset crash counter after this much time (ms) — gives the device a chance to recover. */
    private const val CRASH_RESET_WINDOW_MS = 300_000L  // 5 minutes

    /**
     * Initializes the Kokoro engine on a background thread.
     * Extracts model from APK assets to filesystem, then loads via file-based
     * constructor. Guarded against duplicate concurrent calls and crash loops.
     *
     * If previous init attempts crashed the process (SIGSEGV), this method
     * tracks the failures and backs off exponentially with increasing delays.
     * On the very first detected crash, Piper fallback is attempted immediately
     * (guarded by init_in_progress so Piper crashes are also tracked).
     * After [NATIVE_BROKEN_THRESHOLD] total crashes, all native code is skipped
     * and an error is shown — the native stack is broken on this device.
     */
    fun warmUp(ctx: Context) {
        statusContext = ctx.applicationContext
        plog("warmUp() called")
        if (isReady && kokoroTts != null) { plog("warmUp: already ready, skipping"); onReadyCallback?.invoke(); return }
        synchronized(warmUpLock) {
            if (isWarmingUp) { plog("warmUp: already warming up, skipping"); return }
            isWarmingUp = true
        }
        plog("warmUp: starting init sequence")

        // ── Crash-loop detection ─────────────────────────────────────────
        // Before calling native code that can SIGSEGV, check if previous
        // attempts crashed. We use SharedPreferences because they survive
        // process restarts (the :tts process gets killed and restarted by Android).
        val initPrefs = ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)
        val wasInProgress = initPrefs.getBoolean(KEY_INIT_IN_PROGRESS, false)
        var crashCount = initPrefs.getInt(KEY_INIT_CRASH_COUNT, 0)
        val lastCrashTime = initPrefs.getLong(KEY_INIT_LAST_CRASH_TIME, 0)

        // If the previous init was still "in progress" when the process died, check WHY.
        // On Android 11+, ApplicationExitInfo tells us if it was a native crash (SIGSEGV)
        // or the OS killing the process (HyperOS aggressive battery management).
        // Only count native crashes — OS kills are not the native code's fault.
        plog("warmUp: wasInProgress=$wasInProgress, crashCount=$crashCount, lastCrashTime=$lastCrashTime")
        if (wasInProgress) {
            val wasNativeCrash = wasLastExitNativeCrash(ctx)
            val crashedFormat = initPrefs.getString(KEY_INIT_FORMAT, "unknown")
            plog("warmUp: process died during init — wasNativeCrash=$wasNativeCrash, format=$crashedFormat")
            if (wasNativeCrash) {
                crashCount++
                clearGoodConfig(initPrefs)
                // If .ort caused the crash, skip it next time and go straight to .onnx
                if (crashedFormat == "ort") {
                    initPrefs.edit().putBoolean(KEY_SKIP_ORT, true).apply()
                    plog("warmUp: .ort crashed — will skip .ort and use .onnx on next attempt")
                    Log.w(TAG, "Previous init crashed during .ort loading — will use .onnx instead")
                }
                plog("warmUp: native crash confirmed — crash #$crashCount, cleared config memory")
                Log.w(TAG, "Previous engine init: native crash #$crashCount — cleared config memory")
            } else {
                plog("warmUp: OS killed process (not native crash) — not incrementing crash counter")
                Log.i(TAG, "Previous engine init: OS killed process (not native crash) — crashCount stays at $crashCount")
            }
            initPrefs.edit()
                .putInt(KEY_INIT_CRASH_COUNT, crashCount)
                .putLong(KEY_INIT_LAST_CRASH_TIME, System.currentTimeMillis())
                .putBoolean(KEY_INIT_IN_PROGRESS, false)
                .apply()
        }

        // Reset crash counter if enough time has passed (device might have cooled down)
        if (crashCount > 0 && System.currentTimeMillis() - lastCrashTime > CRASH_RESET_WINDOW_MS) {
            plog("warmUp: crash counter reset (>5min since last crash)")
            crashCount = 0
            initPrefs.edit().putInt(KEY_INIT_CRASH_COUNT, 0).apply()
            Log.d(TAG, "Init crash counter reset (>5min since last crash)")
        }

        // ── Crash recovery ─────────────────────────────────────────────
        // Even a SINGLE native crash means Kokoro is unsafe to load right now.
        // We try Piper first (it's smaller and more likely to work), then
        // back off exponentially before retrying Kokoro.
        //
        // CRITICAL: Piper also uses sherpa-onnx native code (OfflineTts).
        // If the native stack is fundamentally broken (bad JNI/ORT on this
        // device), Piper will ALSO SIGSEGV. We must set init_in_progress
        // before calling Piper so that crash increments the counter.
        // After NATIVE_BROKEN_THRESHOLD crashes, we stop calling ANY native
        // code and show an error instead of crash-looping forever.
        if (crashCount > 0) {
            plog("warmUp: entering crash recovery (crashCount=$crashCount)")
            Log.w(TAG, "Native code crashed $crashCount time(s)")

            if (crashCount >= NATIVE_BROKEN_THRESHOLD) {
                plog("warmUp: NATIVE_BROKEN_THRESHOLD reached ($crashCount >= $NATIVE_BROKEN_THRESHOLD) — trying Android TTS fallback")
                Log.e(TAG, "Native stack appears broken ($crashCount crashes) — trying Android TTS")

                // Build diagnostic info visible in the UI
                val diag = buildDiagnostics(ctx)
                writeInitLog(ctx, "Native stack broken ($crashCount crashes), falling back to Android TTS\n$diag")

                // Try Android system TTS as ultimate fallback
                statusMessage = "native engine broken — trying Android TTS…"
                syncStatus()
                val systemTtsOk = initSystemTtsFallback(ctx)
                if (systemTtsOk) {
                    plog("warmUp: Android system TTS fallback ready")
                    Log.i(TAG, "Android system TTS fallback activated")
                    errorMessage = null
                    statusMessage = "ready (Android TTS fallback)"
                    isReady = true
                    isSystemTtsFallbackActive = true
                    syncStatus()
                    synchronized(warmUpLock) { isWarmingUp = false }
                    onReadyCallback?.invoke()
                } else {
                    plog("warmUp: Android system TTS also failed")
                    errorMessage = "TTS engine crashed $crashCount times.\n$diag"
                    statusMessage = "error: all TTS engines failed"
                    isReady = false
                    syncStatus()
                    synchronized(warmUpLock) { isWarmingUp = false }
                }

                // Schedule native retry after long backoff (conditions might change)
                val backoffMs = CRASH_BACKOFF_MAX_MS
                Log.i(TAG, "Will retry native in ${backoffMs / 1000}s")
                Thread {
                    try {
                        Thread.sleep(backoffMs)
                        synchronized(warmUpLock) { isWarmingUp = false }
                        warmUp(ctx)
                    } catch (_: InterruptedException) {
                        synchronized(warmUpLock) { isWarmingUp = false }
                    }
                }.apply { name = "SherpaEngine-backoff"; isDaemon = true; start() }
                return
            }

            // ── Try Piper fallback (guarded by init_in_progress) ────────
            // Set the flag BEFORE any native code so that if Piper also
            // SIGSEGVs, the crash counter increments on next restart.
            initPrefs.edit().putBoolean(KEY_INIT_IN_PROGRESS, true).commit()
            plog("warmUp: set init_in_progress=true, attempting Piper fallback")
            Log.i(TAG, "Attempting Piper fallback (crash #$crashCount)…")
            statusMessage = "trying fallback engine…"
            syncStatus()

            var piperOk = false
            try {
                piperOk = initPiperFallback(ctx)
                plog("warmUp: Piper fallback result=$piperOk")
            } catch (e: Throwable) {
                plog("warmUp: Piper fallback threw ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "Piper fallback threw exception", e)
            }
            initPrefs.edit().putBoolean(KEY_INIT_IN_PROGRESS, false).apply()
            plog("warmUp: cleared init_in_progress")

            if (piperOk) {
                Log.i(TAG, "Piper fallback activated after $crashCount Kokoro crash(es)")
                onReadyCallback?.invoke()
            } else {
                Log.w(TAG, "Piper fallback also failed — no TTS available yet")
                errorMessage = "TTS engines unavailable (crash #$crashCount)"
                statusMessage = "error: engines unavailable"
                syncStatus()
            }

            // ── Exponential backoff before Kokoro retry ─────────────────
            val backoffMs = (CRASH_BACKOFF_BASE_MS * (1L shl (crashCount - 1).coerceAtMost(10)))
                .coerceAtMost(CRASH_BACKOFF_MAX_MS)
            Log.i(TAG, "Backing off ${backoffMs}ms before Kokoro retry (crash #$crashCount)")
            if (piperOk) {
                statusMessage = "Piper active — retrying Kokoro in ${backoffMs / 1000}s…"
                syncStatus()
            }

            Thread {
                try {
                    Thread.sleep(backoffMs)
                    Log.i(TAG, "Backoff complete — retrying Kokoro init (crash #$crashCount)")
                    synchronized(warmUpLock) { isWarmingUp = false }
                    warmUp(ctx)  // Recursive retry after backoff
                } catch (_: InterruptedException) {
                    synchronized(warmUpLock) { isWarmingUp = false }
                }
            }.apply { name = "SherpaEngine-backoff"; isDaemon = true; start() }
            return
        }

        // ── Set init_in_progress BEFORE any sherpa-onnx code runs ────────
        initPrefs.edit().putBoolean(KEY_INIT_IN_PROGRESS, true).commit()
        plog("warmUp: crashCount=0, set init_in_progress=true, launching init thread")

        Thread {
            try {
                plog("warmUp thread: calling initializeKokoro()")
                val kokoroOk = initializeKokoro(ctx)
                plog("warmUp thread: initializeKokoro returned $kokoroOk")
                if (kokoroOk) {
                    onReadyCallback?.invoke()
                } else {
                    plog("warmUp thread: Kokoro failed, trying Piper fallback")
                    Log.w(TAG, "Kokoro init failed, attempting Piper fallback…")
                    val piperOk = initPiperFallback(ctx)
                    plog("warmUp thread: Piper fallback returned $piperOk")
                    if (piperOk) {
                        Log.i(TAG, "Piper fallback activated — speech will continue with Piper voice")
                        onReadyCallback?.invoke()
                    } else {
                        Log.e(TAG, "Both Kokoro and Piper fallback failed — no TTS available")
                        plog("warmUp thread: BOTH engines failed — errorMessage=$errorMessage")
                        if (errorMessage == null) {
                            errorMessage = "Both Kokoro and Piper engines failed to initialize"
                        }
                        statusMessage = "error: ${errorMessage}"
                        syncStatus()
                        writeInitLog(ctx, "Both engines failed: ${errorMessage}")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Warm-up failed", e)
                errorMessage = e.message ?: "Unknown error during warm-up"
                statusMessage = "error: ${errorMessage}"
                writeInitLog(ctx, "Warm-up exception: ${e.message}", e)
                syncStatus()
                // Even on exception, try Piper fallback
                try {
                    if (initPiperFallback(ctx)) {
                        Log.i(TAG, "Piper fallback activated after warm-up exception")
                        onReadyCallback?.invoke()
                    }
                } catch (e2: Throwable) {
                    Log.e(TAG, "Piper fallback also failed after warm-up exception", e2)
                }
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
            .putBoolean(KEY_SKIP_ORT, false)
            .remove(KEY_INIT_FORMAT)
            .apply()
        errorMessage = null
        statusMessage = "retrying…"
        isReady = false
        isPiperFallbackReady = false  // Reset fallback so Kokoro gets a fresh try
        isSystemTtsFallbackActive = false
        systemTts?.shutdown()
        systemTts = null
        syncStatus()
        warmUp(ctx)
    }

    // ── Kokoro initialization ─────────────────────────────────────────────────

    @Synchronized
    fun initializeKokoro(ctx: Context): Boolean {
        plog("initializeKokoro: called (isReady=$isReady, kokoroTts=${kokoroTts != null})")
        if (isReady && kokoroTts != null) return true

        val initPrefs = ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)
        val crashCount = initPrefs.getInt(KEY_INIT_CRASH_COUNT, 0)
        if (crashCount > 0) {
            plog("initializeKokoro: blocked — $crashCount crash(es), warmUp() handles backoff")
            Log.w(TAG, "initializeKokoro: $crashCount crash(es) — warmUp() handles backoff, skipping direct call")
            return false
        }

        var initWakeLock: PowerManager.WakeLock? = null
        return try {
            val initGlobalStart = System.currentTimeMillis()
            var probeElapsedMs = 0L

            initProgress = 5
            statusMessage = "probing native stack…"
            syncStatus()
            plog("initKokoro: Stage 0 — probing native stack")
            Log.i(TAG, "┌── Kokoro init START ──────────────────────────")

            // ── Stage 0: Probe native stack with a tiny model ────────────
            // The JNI library loads when sherpa-onnx config classes are first
            // constructed. Loading a small Piper model (~60MB) first proves
            // System.loadLibrary, ORT runtime, and JNI bridge all work.
            // If even THIS crashes, native code is fundamentally broken on
            // this device — skip straight to Piper fallback without wasting
            // 3 crash cycles on the huge Kokoro model.
            if (!nativeStackProbed) {
                val probeStart = System.currentTimeMillis()
                Log.i(TAG, "│ Probing native stack with lightweight model…")
                try {
                    val probeVoiceId = PIPER_FALLBACK_VOICE
                    val probeTokens = ensureTokensFile(ctx)
                    val probeEspeak = ensureEspeakData(ctx)
                    val probeModel = extractBundledPiperModel(ctx, probeVoiceId)
                    if (probeModel != null) {
                        val probeVitsConfig = OfflineTtsVitsModelConfig(
                            model   = probeModel.absolutePath,
                            tokens  = probeTokens.absolutePath,
                            dataDir = probeEspeak.absolutePath
                        )
                        val probeModelConfig = OfflineTtsModelConfig(
                            vits = probeVitsConfig, numThreads = 1,
                            debug = false, provider = "cpu"
                        )
                        val probeEngine = OfflineTts(config = OfflineTtsConfig(model = probeModelConfig))
                        // Probe succeeded — native stack is proven functional
                        probeEngine.release()
                        nativeStackProbed = true
                        probeElapsedMs = System.currentTimeMillis() - probeStart
                        plog("initKokoro: native probe ✓ in ${probeElapsedMs}ms")
                        Log.i(TAG, "│ Native stack probe: ✓ in ${probeElapsedMs}ms (ORT + JNI working)")
                    } else {
                        Log.w(TAG, "│ Native stack probe: skipped (couldn't extract probe model)")
                        nativeStackProbed = true
                        probeElapsedMs = System.currentTimeMillis() - probeStart
                    }
                } catch (e: Throwable) {
                    // Probe CRASHED — native code is broken. Don't attempt Kokoro at all.
                    plog("initKokoro: native probe ✗ FAILED: ${e.javaClass.simpleName}: ${e.message}")
                    Log.e(TAG, "│ Native stack probe: ✗ FAILED (${e.javaClass.simpleName}: ${e.message})")
                    Log.e(TAG, "│ Native stack is broken on this device — skipping Kokoro entirely")
                    Log.e(TAG, "└── Kokoro init ABORTED (native probe failed) ─")
                    writeInitLog(ctx, "Native stack probe failed: ${e.javaClass.simpleName}: ${e.message}. " +
                        "JNI/ORT is broken on this device — Kokoro cannot work.", e)
                    statusMessage = "error: native engine broken"
                    errorMessage = "TTS native library failed basic probe test. " +
                        "This device may have an incompatible ONNX Runtime configuration."
                    isReady = false
                    syncStatus()
                    return false
                }
            } else {
                Log.i(TAG, "│ Native stack probe: ✓ (cached)")
            }

            // Log sherpa-onnx library version for diagnostics
            try {
                val vi = com.k2fsa.sherpa.onnx.VersionInfo.Companion
                Log.i(TAG, "│ sherpa-onnx: ${vi.version} (${vi.gitSha1}, ${vi.gitDate})")
            } catch (_: Throwable) {
                Log.i(TAG, "│ sherpa-onnx: version unavailable")
            }

            // Check ORT version compatibility — the build-time ORT that created .ort files
            // must match the on-device ORT bundled in the AAR. If mismatched, .ort files
            // may cause SIGSEGV. Log a warning so it shows up in diagnostics.
            try {
                val buildOrtVersion = ctx.assets.open("ort_version.txt").bufferedReader().readText().trim()
                Log.i(TAG, "│ Build-time ORT version: $buildOrtVersion")
            } catch (_: Throwable) {
                Log.i(TAG, "│ Build-time ORT version: unknown (ort_version.txt not found)")
            }

            initProgress = 10
            statusMessage = "verifying model assets…"
            syncStatus()
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

            plog("initKokoro: pre-flight done — missing=${missingFiles.size}")
            if (missingFiles.isNotEmpty()) {
                val msg = "Missing model files: ${missingFiles.joinToString(", ")}. " +
                    "The APK was built without bundling the Kokoro model. " +
                    "Run the CI build (build.yml) or download the model manually."
                Log.e(TAG, "│ $msg")
                Log.e(TAG, "└── Kokoro init FAILED (missing assets) ───────")
                statusMessage = "error: model files missing from APK"
                errorMessage = msg
                isReady = false
                writeInitLog(ctx, msg)
                syncStatus()
                return false
            }

            initProgress = 15
            statusMessage = "checking device resources…"
            syncStatus()

            // ── Pre-check: disk space ────────────────────────────────────
            // The 311MB model needs ~400MB free (model + temp file during atomic write).
            // Fail fast with a clear message instead of crashing mid-extraction.
            try {
                val stat = StatFs(ctx.filesDir.absolutePath)
                val freeBytes = stat.availableBytes
                Log.i(TAG, "│ Disk free: ${freeBytes / 1024 / 1024}MB (need ${MIN_DISK_SPACE_BYTES / 1024 / 1024}MB)")
                if (freeBytes < MIN_DISK_SPACE_BYTES) {
                    val msg = "Not enough storage: ${freeBytes / 1024 / 1024}MB free, need ${MIN_DISK_SPACE_BYTES / 1024 / 1024}MB. " +
                        "Free up space and restart the app."
                    Log.e(TAG, "│ $msg")
                    Log.e(TAG, "└── Kokoro init FAILED (disk space) ──────────")
                    statusMessage = "error: not enough storage"
                    errorMessage = msg
                    isReady = false
                    writeInitLog(ctx, msg)
                    syncStatus()
                    return false
                }
            } catch (e: Throwable) {
                Log.w(TAG, "│ Could not check disk space: ${e.message}")
            }

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
                writeInitLog(ctx, "Model extraction to filesystem failed")
                syncStatus()
                return false
            }

            // ── Post-extraction integrity check ──────────────────────────
            // Verify extracted files aren't truncated/corrupt (e.g. from power loss mid-write).
            val modelCandidate = File(extractedDir, "model.ort").let {
                if (it.exists()) it else File(extractedDir, "model.onnx")
            }
            val voicesCheck = File(extractedDir, "voices.bin")
            val tokensCheck = File(extractedDir, "tokens.txt")
            if (!modelCandidate.exists() || modelCandidate.length() < MIN_MODEL_FILE_SIZE) {
                val msg = "Extracted model file is missing or corrupt (${modelCandidate.name}: ${modelCandidate.length() / 1024 / 1024}MB, expected >100MB). " +
                    "Deleting and re-extracting on next launch."
                Log.e(TAG, "│ $msg")
                // Delete version marker to force re-extraction
                File(extractedDir, ".extracted_version").delete()
                modelCandidate.delete()
                writeInitLog(ctx, msg)
                statusMessage = "error: corrupt model, will re-extract"
                errorMessage = msg
                syncStatus()
                return false
            }
            if (!voicesCheck.exists() || voicesCheck.length() < 1024) {
                Log.e(TAG, "│ voices.bin missing or empty — forcing re-extraction")
                File(extractedDir, ".extracted_version").delete()
                voicesCheck.delete()
                writeInitLog(ctx, "voices.bin missing or empty after extraction")
                statusMessage = "error: corrupt voices file, will re-extract"
                errorMessage = "Voice data is corrupt. Restart to re-extract."
                syncStatus()
                return false
            }
            plog("initKokoro: extraction verified — model=${modelCandidate.length() / 1024 / 1024}MB, voices=${voicesCheck.length() / 1024 / 1024}MB")
            Log.i(TAG, "│ Extraction verified: model=${modelCandidate.length() / 1024 / 1024}MB, voices=${voicesCheck.length() / 1024 / 1024}MB")

            // ══════════════════════════════════════════════════════════════
            // Stage 1: Device Profiling + Memory Preparation
            // ══════════════════════════════════════════════════════════════
            // Profile the device FIRST — all subsequent decisions (timeout,
            // escalation, memory thresholds) are driven by the profile.
            // Then prepare memory: GC, heap reservation, model pre-warming.
            val telemetry = InitTelemetry(probeMs = probeElapsedMs)
            val stageStart = System.currentTimeMillis()

            initProgress = 25
            statusMessage = "profiling device…"
            syncStatus()
            Log.i(TAG, "│")
            Log.i(TAG, "│ ═══ Stage 1: Device Profile + Memory Prep ═══")

            val profile = profileDevice(ctx)
            telemetry.deviceProfile = profile
            plog("initKokoro: Stage 1 — device=${profile.manufacturer} ${profile.model}, tier=${profile.tier}, ram=${profile.availRamMB}MB/${profile.totalRamMB}MB, soc=${profile.socVendor}")
            Log.i(TAG, "│ $profile")

            // ── 1a. Garbage collection ──────────────────────────────────
            val gcStart = System.currentTimeMillis()
            val runtime = Runtime.getRuntime()
            val heapBefore = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            System.gc()
            System.runFinalization()
            System.gc()
            Thread.sleep(50)
            val heapAfter = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            telemetry.gcMs = System.currentTimeMillis() - gcStart
            Log.i(TAG, "│ GC: heap ${heapBefore}MB → ${heapAfter}MB (freed ${heapBefore - heapAfter}MB, ${telemetry.gcMs}ms)")

            // ── 1b. RAM check (AFTER GC for accurate reading) ───────────
            val memInfo = android.app.ActivityManager.MemoryInfo()
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
                ?.getMemoryInfo(memInfo)
            Log.i(TAG, "│ RAM: ${memInfo.availMem / 1024 / 1024}MB free / ${memInfo.totalMem / 1024 / 1024}MB total (low=${memInfo.lowMemory})")
            Log.i(TAG, "│ JVM: ${runtime.freeMemory() / 1024 / 1024}MB free / ${runtime.maxMemory() / 1024 / 1024}MB max")

            if (memInfo.availMem < MIN_RAM_FOR_INIT_BYTES) {
                val msg = "Not enough RAM: ${memInfo.availMem / 1024 / 1024}MB available, " +
                    "need ${MIN_RAM_FOR_INIT_BYTES / 1024 / 1024}MB for Kokoro model. " +
                    "Close other apps and retry."
                Log.e(TAG, "│ $msg")
                Log.e(TAG, "└── Kokoro init FAILED (low memory) ──────────")
                statusMessage = "error: not enough RAM"
                errorMessage = msg
                isReady = false
                lastTelemetry = telemetry
                writeInitLog(ctx, msg, telemetry = telemetry)
                syncStatus()
                return false
            }

            // ── 1c. Heap reservation ────────────────────────────────────
            // Force JVM to expand its heap region BEFORE native code runs.
            // Prevents virtual memory collisions with ORT's allocator.
            initProgress = 27
            statusMessage = "preparing memory…"
            syncStatus()
            telemetry.heapReserveMs = reserveAndReleaseHeap()
            Log.i(TAG, "│ Heap reservation: ${telemetry.heapReserveMs}ms")

            // ── 1d. Model file resolution ───────────────────────────────
            // Build list of model files to try: .ort first (fast), .onnx fallback (compatible).
            // If .ort fails at all escalation levels, we retry everything with .onnx.
            // CRITICAL: If .ort previously caused a SIGSEGV, skip it entirely.
            initProgress = 28
            val ortFile = File(extractedDir, "model.ort")
            val onnxFile = File(extractedDir, "model.onnx")
            val skipOrt = initPrefs.getBoolean(KEY_SKIP_ORT, false)
            val modelFilesToTry = mutableListOf<File>()
            if (!skipOrt && ortFile.exists() && ortFile.length() > MIN_MODEL_FILE_SIZE) {
                modelFilesToTry.add(ortFile)
            } else if (skipOrt) {
                plog("initKokoro: skipping .ort (previously caused crash)")
                Log.i(TAG, "│ Skipping model.ort (previously caused SIGSEGV — using .onnx instead)")
            }
            if (onnxFile.exists() && onnxFile.length() > MIN_MODEL_FILE_SIZE) modelFilesToTry.add(onnxFile)
            if (modelFilesToTry.isEmpty()) {
                // Neither format available — use whichever exists (will fail at integrity check)
                modelFilesToTry.add(if (ortFile.exists()) ortFile else onnxFile)
            }
            val modelFile = modelFilesToTry.first()
            val isOrt = modelFile.name.endsWith(".ort")
            val hasOnnxFallback = modelFilesToTry.size > 1
            telemetry.modelFormat = if (isOrt) "ORT (pre-optimized)" else "ONNX (runtime optimization)"
            Log.i(TAG, "│ Model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB, ${telemetry.modelFormat})")
            if (hasOnnxFallback) {
                Log.i(TAG, "│ Fallback: model.onnx available if .ort fails")
            }

            // ── 1e. Model file pre-warming ──────────────────────────────
            // Read the entire model file sequentially into OS page cache.
            // When ORT opens it via mmap, ALL pages are already resident —
            // no random page faults that can trigger OOM killer on low-RAM devices.
            initProgress = 29
            statusMessage = "pre-warming model…"
            syncStatus()
            Log.i(TAG, "│ Pre-warming model file into page cache…")
            telemetry.prewarmMs = prewarmModelFile(modelFile)
            // Also pre-warm voices.bin (smaller but accessed during init)
            prewarmModelFile(File(extractedDir, "voices.bin"))

            telemetry.extractionMs = System.currentTimeMillis() - stageStart - telemetry.gcMs -
                telemetry.heapReserveMs - telemetry.prewarmMs

            // ══════════════════════════════════════════════════════════════
            // Stage 2: Config Memory + Auto-Escalation
            // ══════════════════════════════════════════════════════════════
            // 1. Try remembered good config (if any) — skips entire ladder
            // 2. Try escalation ladder: optimal → safe → diagnostic
            // 3. Save successful config for next launch
            // 4. Thread priority elevated during native load (like Google TTS)
            // 5. Adaptive timeout based on device profile + model format
            initProgress = 30
            statusMessage = "preparing Kokoro config…"
            syncStatus()
            Log.i(TAG, "│")
            Log.i(TAG, "│ ═══ Stage 2: Config Memory + Auto-Escalation ═══")

            // ── WakeLock: prevent MIUI/HyperOS from throttling/killing during native load ──
            // Xiaomi's MIUI/HyperOS aggressively throttles CPU and kills background processes.
            // A PARTIAL_WAKE_LOCK keeps the CPU at full speed during model loading.
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            initWakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Echolibrium:KokoroInit"
            )?.apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 1000L)  // 3 min safety timeout
            }
            Log.i(TAG, "│ WakeLock acquired: ${initWakeLock != null}")

            val appVersion = getAppVersionCode(ctx)

            // Build escalation ladder
            val optThreads = optimalThreadCount()
            val optProvider = optimalProvider()
            val freshLadder = listOf(
                InitConfig(optThreads, optProvider, false, "optimal ($optThreads threads, $optProvider)"),
                InitConfig(1, "cpu", false, "safe (1 thread, cpu)"),
                InitConfig(1, "cpu", true, "diagnostic (1 thread, cpu, debug)")
            ).distinctBy { Triple(it.threads, it.provider, it.debug) }

            // Check config memory — if we remember what worked, try it first
            val rememberedConfig = loadGoodConfig(initPrefs, appVersion)
            val escalationLadder = if (rememberedConfig != null) {
                Log.i(TAG, "│ Config memory: ${rememberedConfig.label}")
                val rest = freshLadder.filter {
                    Triple(it.threads, it.provider, it.debug) !=
                        Triple(rememberedConfig.threads, rememberedConfig.provider, rememberedConfig.debug)
                }
                listOf(rememberedConfig) + rest
            } else {
                Log.i(TAG, "│ Config memory: empty (first run or app updated)")
                freshLadder
            }

            // init_in_progress flag is already set by warmUp() before this thread started.
            initStartTime.set(System.currentTimeMillis())
            var successConfig: InitConfig? = null

            // ── Outer loop: model format fallback (.ort → .onnx) ─────────
            // If all escalation configs fail with .ort, the format itself may be
            // incompatible (ORT version mismatch). Retry with .onnx if available.
            for ((formatIdx, currentModelFile) in modelFilesToTry.withIndex()) {
                val currentIsOrt = currentModelFile.name.endsWith(".ort")
                val formatLabel = if (currentIsOrt) "ORT" else "ONNX"
                val timeoutMs = adaptiveTimeoutMs(currentIsOrt, profile)

                if (formatIdx > 0) {
                    // This is a format fallback — log clearly
                    Log.w(TAG, "│")
                    Log.w(TAG, "│ ═══ Format fallback: retrying with $formatLabel ═══")
                    plog("initKokoro: Format fallback → $formatLabel (${currentModelFile.name})")
                    telemetry.modelFormat = "ONNX (fallback from ORT)"
                    statusMessage = "retrying with $formatLabel format…"
                    initProgress = 35
                    syncStatus()

                    // Pre-warm the fallback model file
                    Log.i(TAG, "│ Pre-warming ${currentModelFile.name}…")
                    prewarmModelFile(currentModelFile)
                } else {
                    telemetry.timeoutMs = timeoutMs
                }

                Log.i(TAG, "│ Format: $formatLabel (${currentModelFile.name}, ${currentModelFile.length() / 1024 / 1024}MB)")
                Log.i(TAG, "│ Adaptive timeout: ${timeoutMs}ms (ort=$currentIsOrt, tier=${profile.tier})")

                // Track which format we're about to load so crash recovery knows what failed
                initPrefs.edit().putString(KEY_INIT_FORMAT, if (currentIsOrt) "ort" else "onnx").commit()

                val kokoroConfig = OfflineTtsKokoroModelConfig(
                    model   = currentModelFile.absolutePath,
                    voices  = File(extractedDir, "voices.bin").absolutePath,
                    tokens  = File(extractedDir, "tokens.txt").absolutePath,
                    dataDir = File(extractedDir, "espeak-ng-data").absolutePath
                )

                plog("initKokoro: Stage 2 ($formatLabel) — ladder=${escalationLadder.size} configs, timeout=${timeoutMs}ms")
                Log.i(TAG, "│ Escalation ladder: ${escalationLadder.size} configs")
                escalationLadder.forEachIndexed { i, c -> Log.i(TAG, "│   [$i] ${c.label}") }

                initProgress = 35
                statusMessage = if (currentIsOrt) "loading optimized model…"
                    else "loading native model (this may take 10-30s)…"
                syncStatus()

                Log.i(TAG, "│ Using file-based constructor (no AssetManager)")
                Log.i(TAG, "│ Model: ${currentModelFile.absolutePath}")

                var allTimedOut = true
                var lastError: Throwable? = null

                for ((level, initCfg) in escalationLadder.withIndex()) {
                    Log.i(TAG, "│ ── Escalation level $level: ${initCfg.label} ($formatLabel) ──")

                    val modelConfig = OfflineTtsModelConfig(
                        kokoro     = kokoroConfig,
                        numThreads = initCfg.threads,
                        debug      = initCfg.debug,
                        provider   = initCfg.provider
                    )
                    val config = OfflineTtsConfig(model = modelConfig)

                    val resultHolder = arrayOfNulls<OfflineTts>(1)
                    val errorHolder = arrayOfNulls<Throwable>(1)
                    val latch = CountDownLatch(1)
                    val levelStart = System.currentTimeMillis()

                    val initThread = Thread {
                        try {
                            android.os.Process.setThreadPriority(
                                android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
                            )
                            resultHolder[0] = OfflineTts(config = config)
                        } catch (e: Throwable) {
                            errorHolder[0] = e
                        } finally {
                            latch.countDown()
                        }
                    }.apply { name = "SherpaEngine-init-L$level"; isDaemon = true; start() }

                    val progressThread = Thread {
                        try {
                            val pStart = System.currentTimeMillis()
                            while (!latch.await(500, TimeUnit.MILLISECONDS)) {
                                val elapsed = System.currentTimeMillis() - pStart
                                initProgress = (35 + (55 * (1.0 - Math.exp(-elapsed / 15000.0)))).toInt().coerceAtMost(90)
                                syncStatus()
                            }
                        } catch (_: InterruptedException) {}
                    }.apply { name = "SherpaEngine-progress-L$level"; isDaemon = true; start() }

                    val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                    progressThread.interrupt()
                    val levelElapsed = System.currentTimeMillis() - levelStart

                    if (!completed) {
                        plog("initKokoro: Level $level TIMED OUT after ${levelElapsed}ms — ${initCfg.label} ($formatLabel)")
                        Log.w(TAG, "│ Level $level TIMED OUT after ${levelElapsed}ms — ${initCfg.label}")
                        try { initThread.interrupt() } catch (_: Throwable) {}

                        if (level < escalationLadder.lastIndex) {
                            Log.i(TAG, "│ Escalating to level ${level + 1}…")
                            statusMessage = "retrying with safer config…"
                            syncStatus()
                            Thread.sleep(100)
                            continue
                        }
                        // All levels exhausted for this format
                        continue
                    }

                    allTimedOut = false

                    val initError = errorHolder[0]
                    if (initError != null) {
                        plog("initKokoro: Level $level FAILED in ${levelElapsed}ms: ${initError.javaClass.simpleName}: ${initError.message}")
                        Log.w(TAG, "│ Level $level FAILED in ${levelElapsed}ms: " +
                            "${initError.javaClass.simpleName}: ${initError.message}")
                        lastError = initError

                        if (level < escalationLadder.lastIndex) {
                            Log.i(TAG, "│ Escalating to level ${level + 1}…")
                            statusMessage = "retrying with safer config…"
                            syncStatus()
                            continue
                        }
                        // All levels exhausted for this format
                        continue
                    }

                    // SUCCESS at this level
                    plog("initKokoro: Level $level SUCCESS in ${levelElapsed}ms — ${initCfg.label} ($formatLabel)")
                    Log.i(TAG, "│ OfflineTts() constructor: ${levelElapsed}ms at level $level")
                    if (level > 0 || formatIdx > 0) {
                        Log.i(TAG, "│ Succeeded after ${if (formatIdx > 0) "format fallback + " else ""}$level escalations (${initCfg.label})")
                    }

                    kokoroTts = resultHolder[0]
                    successConfig = initCfg
                    telemetry.nativeLoadMs = levelElapsed
                    telemetry.escalationLevel = level
                    telemetry.configUsed = "${initCfg.label} ($formatLabel)"
                    if (formatIdx > 0) telemetry.modelFormat = "ONNX (fallback from ORT)"
                    break
                }

                // If we got a successful result, break out of format loop
                if (kokoroTts != null) break

                // Log format failure
                if (formatIdx < modelFilesToTry.lastIndex) {
                    val nextFormat = if (modelFilesToTry[formatIdx + 1].name.endsWith(".ort")) "ORT" else "ONNX"
                    Log.w(TAG, "│ All escalation levels failed with $formatLabel — falling back to $nextFormat")
                    // Clear config memory since it was for the failing format
                    initPrefs.edit().remove(KEY_LAST_GOOD_THREADS).remove(KEY_LAST_GOOD_PROVIDER)
                        .remove(KEY_LAST_GOOD_DEBUG).remove(KEY_LAST_GOOD_VERSION).apply()
                } else if (allTimedOut) {
                    // Final format, all timed out
                    val elapsed = System.currentTimeMillis() - initStartTime.get()
                    initStartTime.set(0)
                    telemetry.nativeLoadMs = elapsed
                    telemetry.totalMs = System.currentTimeMillis() - stageStart
                    telemetry.escalationLevel = escalationLadder.lastIndex
                    telemetry.configUsed = escalationLadder.last().label
                    lastTelemetry = telemetry
                    initPrefs.edit()
                        .putBoolean(KEY_INIT_IN_PROGRESS, false)
                        .apply()
                    try { initWakeLock?.release() } catch (_: Throwable) {}
                    Log.e(TAG, "│ All escalation levels TIMED OUT across all model formats")
                    Log.e(TAG, "└── Kokoro init FAILED (timeout) ─────────────")
                    statusMessage = "error: init timed out after ${elapsed / 1000}s"
                    errorMessage = "Engine timed out after ${elapsed / 1000}s. " +
                        "Tried ${escalationLadder.size} configs × ${modelFilesToTry.size} formats. Device: ${profile.tier}"
                    isReady = false
                    writeInitLog(ctx, "All escalation configs timed out (all formats)", telemetry = telemetry)
                    syncStatus()
                    return false
                } else if (lastError != null) {
                    // Final format, all errored
                    telemetry.escalationLevel = escalationLadder.lastIndex
                    telemetry.configUsed = escalationLadder.last().label
                    lastTelemetry = telemetry
                    throw lastError!!
                }
            }

            val totalElapsed = System.currentTimeMillis() - initStartTime.getAndSet(0)
            telemetry.totalMs = System.currentTimeMillis() - stageStart
            lastTelemetry = telemetry

            if (kokoroTts == null) {
                Log.e(TAG, "│ OfflineTts() returned null after all escalation levels and formats")
                Log.e(TAG, "└── Kokoro init FAILED ────────────────────────")
                statusMessage = "error: engine returned null"
                errorMessage = "Engine returned null after ${escalationLadder.size} configs × ${modelFilesToTry.size} formats"
                isReady = false
                writeInitLog(ctx, "OfflineTts() returned null", telemetry = telemetry)
                return false
            }

            // ── SUCCESS: release WakeLock + persist everything ──────────
            try { initWakeLock?.release() } catch (_: Throwable) {}
            // Clear crash tracking flags
            initPrefs.edit()
                .putBoolean(KEY_INIT_IN_PROGRESS, false)
                .putInt(KEY_INIT_CRASH_COUNT, 0)
                .apply()

            // Save the successful config — next launch skips the entire ladder
            if (successConfig != null) {
                saveGoodConfig(initPrefs, successConfig, appVersion)
                Log.i(TAG, "│ Config saved to memory: ${successConfig.label}")
            }

            initProgress = 100
            isReady = true
            errorMessage = null
            statusMessage = "ready"
            syncStatus()
            plog("initKokoro: ✓ SUCCESS in ${totalElapsed}ms — config=${successConfig?.label}")
            Log.i(TAG, "│ Kokoro engine ready in ${totalElapsed}ms")
            Log.i(TAG, "│ Telemetry: probe=${telemetry.probeMs}ms extract=${telemetry.extractionMs}ms " +
                "gc=${telemetry.gcMs}ms heap=${telemetry.heapReserveMs}ms " +
                "prewarm=${telemetry.prewarmMs}ms native=${telemetry.nativeLoadMs}ms")
            Log.i(TAG, "└── Kokoro init SUCCESS ───────────────────────")
            true

        } catch (e: Throwable) {
            plog("initKokoro: ✗ EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            try { initWakeLock?.release() } catch (_: Throwable) {}
            val elapsed = System.currentTimeMillis() - initStartTime.getAndSet(0)
            try {
                ctx.applicationContext.getSharedPreferences(INIT_PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_INIT_IN_PROGRESS, false).apply()
            } catch (_: Throwable) {}
            val telem = lastTelemetry
            telem?.totalMs = elapsed
            Log.e(TAG, "│ Exception after ${elapsed}ms: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "└── Kokoro init FAILED (all escalation levels exhausted) ──", e)
            kokoroTts = null
            isReady = false
            errorMessage = e.message ?: "Failed to initialize Kokoro engine"
            statusMessage = "error: ${errorMessage}"
            writeInitLog(ctx, "All escalation levels failed: ${e.javaClass.simpleName}: ${e.message}", e, telem)
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

    // ── Piper fallback (when Kokoro init fails) ─────────────────────────────

    /**
     * Initializes a bundled Piper voice as fallback when Kokoro fails to init.
     * Uses a small, battle-tested VITS model (~60MB) that rarely crashes.
     * Called automatically when Kokoro init fails.
     */
    @Synchronized
    fun initPiperFallback(ctx: Context): Boolean {
        if (isPiperFallbackReady && piperTts != null && piperLoadedVoiceId == PIPER_FALLBACK_VOICE) {
            return true
        }

        Log.i(TAG, "┌── Piper fallback init START ─────────────────")
        Log.i(TAG, "│ Kokoro unavailable, initializing Piper fallback: $PIPER_FALLBACK_VOICE")

        return try {
            if (loadPiperVoice(ctx, PIPER_FALLBACK_VOICE)) {
                isPiperFallbackReady = true
                // Update status to reflect we're running with fallback
                statusMessage = "ready (Piper fallback)"
                errorMessage = null
                isReady = true
                syncStatus()
                Log.i(TAG, "│ Piper fallback engine ready")
                Log.i(TAG, "└── Piper fallback init SUCCESS ──────────────")
                true
            } else {
                Log.e(TAG, "│ loadPiperVoice returned false")
                Log.e(TAG, "└── Piper fallback init FAILED ───────────────")
                writeInitLog(ctx, "Piper fallback init failed: loadPiperVoice returned false")
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "│ Piper fallback init exception", e)
            Log.e(TAG, "└── Piper fallback init FAILED ───────────────")
            writeInitLog(ctx, "Piper fallback init exception: ${e.message}", e)
            false
        }
    }

    /**
     * Synthesize using whatever engine is available.
     * Tries Kokoro first (with speaker ID), falls back to Piper if Kokoro is down.
     * Returns null only if no engine is available at all.
     */
    @Synchronized
    fun synthesizeWithFallback(ctx: Context, text: String, sid: Int = 0, speed: Float = 1.0f): Pair<FloatArray, Int>? {
        // Try Kokoro first
        if (kokoroTts != null) {
            val result = synthesize(text, sid, speed)
            if (result != null) return result
        }

        // Kokoro unavailable or failed — use Piper fallback
        if (!isPiperFallbackReady) {
            initPiperFallback(ctx)
        }
        if (isPiperFallbackReady) {
            try {
                val engine = piperTts
                if (engine != null) {
                    val audio = engine.generate(text = text, sid = 0, speed = speed)
                    lastSampleRate = audio.sampleRate
                    return Pair(audio.samples, audio.sampleRate)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Piper fallback synthesis failed", e)
            }
        }

        // Both native engines failed — try Android system TTS
        if (isSystemTtsFallbackActive) {
            return synthesizeSystemTts(text, speed)
        }

        // Last resort: try to init system TTS now
        if (initSystemTtsFallback(ctx)) {
            isSystemTtsFallbackActive = true
            return synthesizeSystemTts(text, speed)
        }

        return null
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

            // Extract BOTH model formats if available (.ort for speed, .onnx as fallback)
            val ortAsset = "$KOKORO_DIR/model.ort"
            val onnxAsset = "$KOKORO_DIR/model.onnx"
            val hasOrt = try { ctx.assets.open(ortAsset).use { true } } catch (_: Throwable) { false }
            val hasOnnx = try { ctx.assets.open(onnxAsset).use { true } } catch (_: Throwable) { false }
            val modelAsset = if (hasOrt) ortAsset else onnxAsset
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

            // Extract primary model file (.ort preferred)
            if (!modelFile.exists() || modelFile.length() == 0L || !isExtractionCurrent(destDir, ctx)) {
                Log.i(TAG, "│   Extracting $modelFileName…")
                extractAssetAtomic(ctx, modelAsset, modelFile)
                Log.i(TAG, "│   $modelFileName: ${modelFile.length() / 1024 / 1024}MB")
            }

            // Extract .onnx fallback if available and not already extracted
            if (hasOrt && hasOnnx) {
                val onnxFallback = File(destDir, "model.onnx")
                if (!onnxFallback.exists() || onnxFallback.length() == 0L || !isExtractionCurrent(destDir, ctx)) {
                    Log.i(TAG, "│   Extracting model.onnx (runtime fallback)…")
                    extractAssetAtomic(ctx, onnxAsset, onnxFallback)
                    Log.i(TAG, "│   model.onnx: ${onnxFallback.length() / 1024 / 1024}MB (fallback)")
                }
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

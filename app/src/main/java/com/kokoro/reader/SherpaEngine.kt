package com.kokoro.reader

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.util.Log
import java.io.File
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Singleton wrapper around sherpa-onnx OfflineTts.
 *
 * Synthesis backend: Piper/VITS — one model per voice, loaded on demand.
 *
 * Thread-safe: synthesize methods are called from the AudioPipeline background thread.
 */
object SherpaEngine {

    private const val TAG = "SherpaEngine"

    // Asset paths (relative to assets/)
    private const val PIPER_DIR  = "piper-models"

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
     * All devices use "cpu" for maximum compatibility.
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
            // Find the most recent exit for our process (main process, no longer :tts)
            val ttsExit = exitInfos.firstOrNull { info ->
                info.processName == ctx.packageName
            }
            if (ttsExit == null) {
                plog("wasLastExitNativeCrash: no exit info for main process — assuming crash")
                return true
            }
            val reason = ttsExit.reason
            val desc = ttsExit.description ?: "none"
            plog("wasLastExitNativeCrash: reason=$reason (${exitReasonName(reason)}), desc=$desc, " +
                "importance=${ttsExit.importance}, status=${ttsExit.status}")
            // REASON_CRASH_NATIVE (6) = SIGSEGV/SIGABRT/etc — definitely a native crash
            // REASON_CRASH (4) = uncaught Java exception — treat as crash
            // REASON_ANR (3) = ANR — treat as crash (init took too long)
            // REASON_EXIT_SELF (1) with non-zero status = abnormal self-exit during init
            //   (e.g. OOM, uncaught error causing exit(255)) — treat as crash to avoid
            //   infinite retry loops where init keeps dying at the same point
            // Everything else = OS killed it (not the native code's fault)
            when (reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_ANR -> {
                    Log.w(TAG, "Last :tts exit was ${exitReasonName(reason)}: $desc")
                    true
                }
                ApplicationExitInfo.REASON_EXIT_SELF -> {
                    // EXIT_SELF is ambiguous: could be our code calling exit(), OR the OS
                    // (especially Xiaomi/HyperOS) killing background processes for battery
                    // management. Status=255 is commonly used by the system for forced kills.
                    // Only REASON_CRASH_NATIVE reliably indicates a SIGSEGV/SIGABRT.
                    // Treating EXIT_SELF as crash causes false positives on aggressive OEMs,
                    // permanently disabling the engine after NATIVE_BROKEN_THRESHOLD.
                    val status = ttsExit.status
                    Log.i(TAG, "Last :tts exit was EXIT_SELF with status=$status — not counting as native crash")
                    plog("wasLastExitNativeCrash: EXIT_SELF status=$status — not counting as native crash (may be OS kill)")
                    false
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
                    val ttsExit = exitInfos.firstOrNull { it.processName == ctx.packageName }
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
        // Try up to 2 times — the first attempt can fail on Xiaomi/HyperOS when
        // the system TTS service is slow to bind after repeated :tts process crashes.
        for (attempt in 1..2) {
            plog("initSystemTtsFallback: attempt $attempt/2")
            val latch = CountDownLatch(1)
            var initStatus = TextToSpeech.ERROR
            try {
                val tts = TextToSpeech(ctx.applicationContext) { status ->
                    initStatus = status
                    latch.countDown()
                }
                // Wait up to 15 seconds — Xiaomi/HyperOS can be very slow to bind
                // the system TTS service from a background process that has been
                // crash-looping.
                if (!latch.await(15, TimeUnit.SECONDS)) {
                    plog("initSystemTtsFallback: timeout waiting for system TTS init (attempt $attempt)")
                    tts.shutdown()
                    if (attempt < 2) {
                        Thread.sleep(2000)  // Brief pause before retry
                        continue
                    }
                    return false
                }
                if (initStatus != TextToSpeech.SUCCESS) {
                    plog("initSystemTtsFallback: system TTS init failed (status=$initStatus, attempt $attempt)")
                    tts.shutdown()
                    if (attempt < 2) {
                        Thread.sleep(2000)
                        continue
                    }
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
                plog("initSystemTtsFallback: exception on attempt $attempt: ${e.message}")
                Log.e(TAG, "System TTS init failed (attempt $attempt)", e)
                if (attempt < 2) {
                    Thread.sleep(2000)
                    continue
                }
                return false
            }
        }
        return false
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

    // ── Piper engine (one model per voice, cached) ────────────────────────────
    private var piperTts: OfflineTts? = null
    private var piperLoadedVoiceId: String? = null

    /** Default bundled Piper voice used for initial engine warm-up. */
    private const val PIPER_DEFAULT_VOICE = "en_US-lessac-medium"

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
                appendLine("piperTts         : ${if (piperTts != null) "loaded (${piperLoadedVoiceId})" else "null"}")
                appendLine("lastSampleRate   : $lastSampleRate")
                appendLine()

                // ── Crash Tracking ──
                appendLine("═══ CRASH TRACKING (SharedPrefs: $INIT_PREFS) ═══")
                appendLine("init_in_progress : ${initPrefs.getBoolean(KEY_INIT_IN_PROGRESS, false)}")
                appendLine("init_crash_count : ${initPrefs.getInt(KEY_INIT_CRASH_COUNT, 0)}")
                appendLine("init_os_kill_count: ${initPrefs.getInt(KEY_INIT_OS_KILL_COUNT, 0)}")
                val lastCrash = initPrefs.getLong(KEY_INIT_LAST_CRASH_TIME, 0)
                appendLine("last_crash_time  : ${if (lastCrash > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastCrash)) else "never"}")
                appendLine("crash_age_ms     : ${if (lastCrash > 0) System.currentTimeMillis() - lastCrash else "N/A"}")
                appendLine()

                // ── Model Files ──
                appendLine("═══ MODEL FILES ═══")
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

    /** Approximate init progress 0–100. Drives progress bar in the UI. */
    @Volatile var initProgress: Int = 0
        private set

    /**
     * Callback fired (on any thread) when the TTS engine becomes ready.
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
    /** Tracks how many times the OS killed the process during init (memory pressure). */
    private const val KEY_INIT_OS_KILL_COUNT = "init_os_kill_count"
    /** If true, force INT8 model on next init (set after OS kills during FP32 loading). */
    private const val KEY_FORCE_INT8 = "force_int8"
    /** Base delay (ms) for exponential backoff after crashes. Delay = base * 2^(n-1). */
    private const val CRASH_BACKOFF_BASE_MS = 3_000L   // 3s, 6s, 12s, 24s, 48s…
    /** Max backoff delay (ms) — caps at ~1 minute, then retries forever at this interval. */
    private const val CRASH_BACKOFF_MAX_MS = 60_000L   // 1 minute
    /** After this many CONFIRMED NATIVE crashes, assume the native stack is broken. */
    private const val NATIVE_BROKEN_THRESHOLD = 8
    /** Reset crash counter after this much time (ms) — gives the device a chance to recover. */
    private const val CRASH_RESET_WINDOW_MS = 120_000L  // 2 minutes
    /** After this many OS kills during init, skip native TTS and use system TTS. */
    private const val OS_KILL_THRESHOLD = 3

    /**
     * Initializes the Piper TTS engine on a background thread.
     * Guarded against duplicate concurrent calls and crash loops.
     *
     * If previous init attempts crashed the process (SIGSEGV), this method
     * tracks the failures and backs off exponentially with increasing delays.
     * After [NATIVE_BROKEN_THRESHOLD] total crashes, all native code is skipped
     * and Android system TTS is used instead.
     */
    fun warmUp(ctx: Context) {
        statusContext = ctx.applicationContext
        plog("warmUp() called")
        if (isReady) { plog("warmUp: already ready, skipping"); onReadyCallback?.invoke(); return }
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

        // Reset crash counter on app update — new code deserves a fresh chance
        val currentVersion = getAppVersionCode(ctx)
        val lastVersion = initPrefs.getLong("last_init_version", 0L)
        if (currentVersion != lastVersion) {
            plog("warmUp: app updated ($lastVersion → $currentVersion) — resetting crash state")
            Log.i(TAG, "App version changed ($lastVersion → $currentVersion) — resetting init crash state")
            crashCount = 0
            initPrefs.edit()
                .putInt(KEY_INIT_CRASH_COUNT, 0)
                // Keep os_kill_count and force_int8 across updates — RAM limits are
                // a hardware constraint, not a code bug. Resetting them just causes
                // the device to repeat the FP32→OS kill→force INT8 cycle on every update.
                .putBoolean(KEY_INIT_IN_PROGRESS, false)
                .putBoolean(KEY_SKIP_ORT, false)
                .putLong("last_init_version", currentVersion)
                .apply()
        }

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
                // If .ort caused the crash, skip it next time and go straight to .onnx
                if (crashedFormat == "ort") {
                    initPrefs.edit().putBoolean(KEY_SKIP_ORT, true).apply()
                    plog("warmUp: .ort crashed — will skip .ort and use .onnx on next attempt")
                    Log.w(TAG, "Previous init crashed during .ort loading — will use .onnx instead")
                }
                plog("warmUp: native crash confirmed — crash #$crashCount, cleared config memory")
                Log.w(TAG, "Previous engine init: native crash #$crashCount — cleared config memory")
            } else {
                // OS killed process during init — likely memory pressure.
                // Track separately: after OS_KILL_THRESHOLD kills, skip native TTS.
                // Also force INT8 model on next attempt (smaller memory footprint).
                val osKillCount = initPrefs.getInt(KEY_INIT_OS_KILL_COUNT, 0) + 1
                initPrefs.edit()
                    .putInt(KEY_INIT_OS_KILL_COUNT, osKillCount)
                    .putBoolean(KEY_FORCE_INT8, true)
                    .apply()
                plog("warmUp: OS killed process (not native crash) — osKillCount=$osKillCount, will force INT8")
                Log.i(TAG, "Previous engine init: OS killed process (memory pressure?) — osKillCount=$osKillCount, forcing INT8 on next attempt")
            }
            initPrefs.edit()
                .putInt(KEY_INIT_CRASH_COUNT, crashCount)
                .putLong(KEY_INIT_LAST_CRASH_TIME, System.currentTimeMillis())
                .putBoolean(KEY_INIT_IN_PROGRESS, false)
                .apply()
        }

        // Reset crash counter if enough time has passed (device might have cooled down)
        if (crashCount > 0 && System.currentTimeMillis() - lastCrashTime > CRASH_RESET_WINDOW_MS) {
            plog("warmUp: crash counter reset (>${CRASH_RESET_WINDOW_MS / 1000}s since last crash)")
            crashCount = 0
            initPrefs.edit()
                .putInt(KEY_INIT_CRASH_COUNT, 0)
                .putInt(KEY_INIT_OS_KILL_COUNT, 0)
                .apply()
            Log.d(TAG, "Init crash/OS-kill counters reset (>2min since last crash)")
        }

        // ── Crash recovery ─────────────────────────────────────────────
        // Even a SINGLE native crash means the engine is unsafe to load right now.
        // We try again after an exponential backoff.
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

                // Schedule native retry after long backoff (conditions might change).
                // Must wait at least CRASH_RESET_WINDOW_MS so the counter resets on re-entry.
                val backoffMs = CRASH_RESET_WINDOW_MS + 5_000L  // 2min + 5s margin
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

            // ── Try Piper (guarded by init_in_progress) ─────────────────
            // Set the flag BEFORE any native code so that if Piper
            // SIGSEGVs, the crash counter increments on next restart.
            initPrefs.edit().putBoolean(KEY_INIT_IN_PROGRESS, true).commit()
            plog("warmUp: set init_in_progress=true, attempting Piper init")
            Log.i(TAG, "Attempting Piper init (crash #$crashCount)…")
            statusMessage = "trying engine…"
            syncStatus()

            var piperOk = false
            try {
                piperOk = initPiper(ctx)
                plog("warmUp: Piper init result=$piperOk")
            } catch (e: Throwable) {
                plog("warmUp: Piper init threw ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "Piper init threw exception", e)
            }
            initPrefs.edit().putBoolean(KEY_INIT_IN_PROGRESS, false).apply()
            plog("warmUp: cleared init_in_progress")

            if (piperOk) {
                Log.i(TAG, "Piper activated after $crashCount crash(es)")
                onReadyCallback?.invoke()
            } else {
                Log.w(TAG, "Piper also failed — no TTS available yet")
                errorMessage = "TTS engines unavailable (crash #$crashCount)"
                statusMessage = "error: engines unavailable"
                syncStatus()
            }

            // ── Exponential backoff before retry ────────────────────────
            val backoffMs = (CRASH_BACKOFF_BASE_MS * (1L shl (crashCount - 1).coerceAtMost(10)))
                .coerceAtMost(CRASH_BACKOFF_MAX_MS)
            Log.i(TAG, "Backing off ${backoffMs}ms before retry (crash #$crashCount)")
            if (piperOk) {
                statusMessage = "ready — retrying in ${backoffMs / 1000}s…"
                syncStatus()
            }

            Thread {
                try {
                    Thread.sleep(backoffMs)
                    Log.i(TAG, "Backoff complete — retrying init (crash #$crashCount)")
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
                plog("warmUp thread: calling initPiper()")
                val piperOk = initPiper(ctx)
                plog("warmUp thread: initPiper returned $piperOk")
                if (piperOk) {
                    Log.i(TAG, "Piper engine ready")
                    onReadyCallback?.invoke()
                } else {
                    Log.e(TAG, "Piper engine failed — no TTS available")
                    plog("warmUp thread: Piper failed — errorMessage=$errorMessage")
                    if (errorMessage == null) {
                        errorMessage = "Piper engine failed to initialize"
                    }
                    statusMessage = "error: ${errorMessage}"
                    syncStatus()
                    writeInitLog(ctx, "Piper init failed: ${errorMessage}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Warm-up failed", e)
                errorMessage = e.message ?: "Unknown error during warm-up"
                statusMessage = "error: ${errorMessage}"
                writeInitLog(ctx, "Warm-up exception: ${e.message}", e)
                syncStatus()
                // Try system TTS as fallback
                try {
                    if (initSystemTtsFallback(ctx)) {
                        Log.i(TAG, "System TTS fallback activated after warm-up exception")
                        isSystemTtsFallbackActive = true
                        isReady = true
                        errorMessage = null
                        statusMessage = "ready (Android TTS fallback)"
                        syncStatus()
                        onReadyCallback?.invoke()
                    }
                } catch (e2: Throwable) {
                    Log.e(TAG, "System TTS fallback also failed", e2)
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
            .putInt(KEY_INIT_OS_KILL_COUNT, 0)
            .putBoolean(KEY_INIT_IN_PROGRESS, false)
            .putBoolean(KEY_SKIP_ORT, false)
            .putBoolean(KEY_FORCE_INT8, false)
            .remove(KEY_INIT_FORMAT)
            .apply()
        errorMessage = null
        statusMessage = "retrying…"
        isReady = false
        isSystemTtsFallbackActive = false
        systemTts?.shutdown()
        systemTts = null
        syncStatus()
        warmUp(ctx)
    }

    // ── Piper initialization ──────────────────────────────────────────────────

    // initializeKokoro and Kokoro synthesis removed — Piper is the primary engine.
    // The initPiper() method below handles all Piper engine initialization.


    /**
     * Initializes a bundled Piper voice as the primary TTS engine.
     */
    @Synchronized
    fun initPiper(ctx: Context): Boolean {
        if (isReady && piperTts != null && piperLoadedVoiceId == PIPER_DEFAULT_VOICE) {
            return true
        }

        Log.i(TAG, "┌── Piper init START ──────────────────────────")
        Log.i(TAG, "│ Initializing Piper engine: $PIPER_DEFAULT_VOICE")

        return try {
            if (loadPiperVoice(ctx, PIPER_DEFAULT_VOICE)) {
                statusMessage = "ready"
                errorMessage = null
                isReady = true
                syncStatus()
                Log.i(TAG, "│ Piper engine ready")
                Log.i(TAG, "└── Piper init SUCCESS ───────────────────────")
                true
            } else {
                Log.e(TAG, "│ loadPiperVoice returned false")
                Log.e(TAG, "└── Piper init FAILED ────────────────────────")
                writeInitLog(ctx, "Piper init failed: loadPiperVoice returned false")
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "│ Piper init exception", e)
            Log.e(TAG, "└── Piper init FAILED ────────────────────────")
            writeInitLog(ctx, "Piper init exception: ${e.message}", e)
            false
        }
    }

    /**
     * Synthesize using whatever engine is available.
     * Tries Piper first, falls back to Android system TTS.
     * Returns null only if no engine is available at all.
     */
    @Synchronized
    fun synthesizeWithFallback(ctx: Context, text: String, sid: Int = 0, speed: Float = 1.0f): Pair<FloatArray, Int>? {
        // Try Piper engine
        try {
            val engine = piperTts
            if (engine != null) {
                val audio = engine.generate(text = text, sid = 0, speed = speed)
                lastSampleRate = audio.sampleRate
                return Pair(audio.samples, audio.sampleRate)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Piper synthesis failed", e)
        }

        // Piper failed — try Android system TTS
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
        val baseDir = File(ctx.filesDir, "piper-models")
        val espeakDir = File(baseDir, "espeak-ng-data")
        if (espeakDir.exists() && (espeakDir.listFiles()?.size ?: 0) > 0
            && isExtractionCurrent(baseDir, ctx)) return espeakDir
        synchronized(extractionLock) {
            // Double-check after acquiring lock
            if (espeakDir.exists() && (espeakDir.listFiles()?.size ?: 0) > 0
                && isExtractionCurrent(baseDir, ctx)) return espeakDir
            if (espeakDir.exists()) espeakDir.deleteRecursively()
            espeakDir.mkdirs()
            copyAssetDir(ctx, "$PIPER_DIR/espeak-ng-data", espeakDir)
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
        try { piperTts?.release() } catch (e: Throwable) { /* ignore */ }
        piperTts = null
        piperLoadedVoiceId = null
        isReady = false
        statusMessage = "released"
        syncStatus()
        Log.d(TAG, "SherpaEngine released")
    }
}

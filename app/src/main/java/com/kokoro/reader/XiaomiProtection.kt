package com.kokoro.reader

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Programmatically bypasses Xiaomi MIUI/HyperOS aggressive background killing
 * using Shizuku (shell-level access without root).
 *
 * When Shizuku is running, this class executes the same ADB commands a developer
 * would run manually — device idle whitelist, AutoStart appops, background run
 * permissions, and standby bucket override.
 *
 * Without Shizuku, [getManualAdbCommands] returns the exact commands the user
 * can paste into a terminal.
 */
object XiaomiProtection {

    private const val TAG = "XiaomiProtection"
    private const val SHIZUKU_PERMISSION_CODE = 2001

    // ── Shizuku availability ────────────────────────────────────────────────────

    /** True if the Shizuku app is installed on the device. */
    fun isShizukuInstalled(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** True if Shizuku is running and we have permission to use it. */
    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /** True if Shizuku service is alive (installed + running, permission not yet checked). */
    fun isShizukuAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    /** Request Shizuku permission. Call from an Activity that handles the result. */
    fun requestShizukuPermission() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to request Shizuku permission: ${e.message}")
        }
    }

    // ── Shell command execution via Shizuku ──────────────────────────────────────

    private fun exec(command: String): Pair<Boolean, String> {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val exitCode = process.waitFor()
            val fullOutput = if (error.isNotEmpty()) "$output\n$error" else output
            Log.d(TAG, "exec: $command → exit=$exitCode, out=$fullOutput")
            Pair(exitCode == 0, fullOutput)
        } catch (e: Throwable) {
            Log.w(TAG, "exec failed: $command — ${e.message}")
            Pair(false, e.message ?: "unknown error")
        }
    }

    // ── Protection commands ──────────────────────────────────────────────────────

    data class ProtectionResult(
        val deviceIdleWhitelist: Boolean = false,
        val autoStart: Boolean = false,
        val runInBackground: Boolean = false,
        val standbyBucket: Boolean = false,
        val summary: String = ""
    ) {
        val allGranted get() = deviceIdleWhitelist && autoStart && runInBackground && standbyBucket
    }

    /**
     * Executes all Xiaomi protection commands via Shizuku.
     * Each command is independent — failures don't stop the others.
     *
     * @return [ProtectionResult] with per-command success status
     */
    fun applyAllProtections(ctx: Context): ProtectionResult {
        val pkg = ctx.packageName
        val results = mutableListOf<String>()

        // 1. Device idle whitelist (Doze exemption)
        val (idle, _) = exec("cmd deviceidle whitelist +$pkg")
        results.add(if (idle) "✓ Doze whitelist" else "✗ Doze whitelist")

        // 2. AutoStart — try known Xiaomi appops codes
        val (auto1, _) = exec("appops set $pkg 10021 allow")
        val (auto2, _) = exec("appops set $pkg 10008 allow")
        val autoOk = auto1 || auto2
        results.add(if (autoOk) "✓ AutoStart" else "✗ AutoStart")

        // 3. Background run permissions
        val (bg1, _) = exec("cmd appops set $pkg RUN_IN_BACKGROUND allow")
        val (bg2, _) = exec("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
        val bgOk = bg1 && bg2
        results.add(if (bgOk) "✓ Background run" else "✗ Background run")

        // 4. Standby bucket → ACTIVE
        val (bucket, _) = exec("am set-standby-bucket $pkg active")
        results.add(if (bucket) "✓ Standby bucket" else "✗ Standby bucket")

        // 5. Xiaomi battery saver per-app (op 10023)
        exec("appops set $pkg 10023 allow")

        val summary = results.joinToString(", ")
        Log.i(TAG, "Protection results: $summary")

        return ProtectionResult(
            deviceIdleWhitelist = idle,
            autoStart = autoOk,
            runInBackground = bgOk,
            standbyBucket = bucket,
            summary = summary
        )
    }

    // ── Manual ADB fallback ─────────────────────────────────────────────────────

    /**
     * Returns the ADB commands the user can run manually if Shizuku is not available.
     * These are the same commands [applyAllProtections] executes via Shizuku.
     */
    fun getManualAdbCommands(ctx: Context): String {
        val pkg = ctx.packageName
        return buildString {
            appendLine("# Run these commands via ADB to whitelist Kyōkan on Xiaomi/HyperOS:")
            appendLine("# (Connect phone via USB, enable USB debugging)")
            appendLine()
            appendLine("# 1. Doze exemption (prevents aggressive sleep)")
            appendLine("adb shell cmd deviceidle whitelist +$pkg")
            appendLine()
            appendLine("# 2. AutoStart permission")
            appendLine("adb shell appops set $pkg 10021 allow")
            appendLine("adb shell appops set $pkg 10008 allow")
            appendLine()
            appendLine("# 3. Allow background execution")
            appendLine("adb shell cmd appops set $pkg RUN_IN_BACKGROUND allow")
            appendLine("adb shell cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
            appendLine()
            appendLine("# 4. Set app as always-active")
            appendLine("adb shell am set-standby-bucket $pkg active")
            appendLine()
            appendLine("# 5. Xiaomi battery saver bypass")
            appendLine("adb shell appops set $pkg 10023 allow")
        }
    }
}

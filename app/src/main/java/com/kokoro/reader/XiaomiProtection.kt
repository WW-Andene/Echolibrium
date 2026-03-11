package com.kokoro.reader

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Programmatically bypasses Xiaomi MIUI/HyperOS aggressive background killing
 * using Shizuku (shell-level access without root).
 *
 * Uses Shizuku's binder API (not deprecated shell process) to call system
 * services directly: IDeviceIdleController for Doze whitelist, IAppOpsService
 * for AutoStart/background run permissions.
 *
 * Without Shizuku, [getManualAdbCommands] returns the exact commands the user
 * can paste into a terminal.
 */
object XiaomiProtection {

    private const val TAG = "XiaomiProtection"
    private const val SHIZUKU_PERMISSION_CODE = 2001

    // Standard appops modes
    private const val MODE_ALLOWED = 0

    // Standard appops codes
    private const val OP_RUN_IN_BACKGROUND = 63
    private const val OP_RUN_ANY_IN_BACKGROUND = 79

    // Xiaomi-specific appops codes (reverse-engineered, may vary by build)
    private const val OP_XIAOMI_AUTOSTART_1 = 10021
    private const val OP_XIAOMI_AUTOSTART_2 = 10008
    private const val OP_XIAOMI_BATTERY_SAVER = 10023

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

    // ── Binder-based system service calls via Shizuku ────────────────────────────
    // Uses ShizukuBinderWrapper + reflection to call hidden system APIs directly.
    // This is the recommended Shizuku approach (faster + more reliable than shell).

    /**
     * Calls IDeviceIdleController.addPowerSaveWhitelistApp(packageName) via binder.
     * Equivalent to: `adb shell cmd deviceidle whitelist +<package>`
     */
    private fun addToDeviceIdleWhitelist(packageName: String): Boolean {
        return try {
            val binder = ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("deviceidle")
            )
            val stubClass = Class.forName("android.os.IDeviceIdleController\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val controller = asInterface.invoke(null, binder)
            val addMethod = controller!!.javaClass.getMethod(
                "addPowerSaveWhitelistApp", String::class.java
            )
            addMethod.invoke(controller, packageName)
            Log.d(TAG, "Added $packageName to device idle whitelist via binder")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "deviceidle whitelist failed: ${e.message}")
            false
        }
    }

    /**
     * Calls IAppOpsService.setMode(op, uid, packageName, MODE_ALLOWED) via binder.
     * Equivalent to: `adb shell appops set <package> <op> allow`
     */
    private fun setAppOpsMode(op: Int, uid: Int, packageName: String): Boolean {
        return try {
            val binder = ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("appops")
            )
            val stubClass = Class.forName("com.android.internal.app.IAppOpsService\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val service = asInterface.invoke(null, binder)
            val setMode = service!!.javaClass.getMethod(
                "setMode",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            setMode.invoke(service, op, uid, packageName, MODE_ALLOWED)
            Log.d(TAG, "Set appops $op → allow for $packageName (uid=$uid)")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "appops set $op failed: ${e.message}")
            false
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
     * Executes all Xiaomi protection commands via Shizuku binder calls.
     * Each command is independent — failures don't stop the others.
     *
     * @return [ProtectionResult] with per-command success status
     */
    fun applyAllProtections(ctx: Context): ProtectionResult {
        val pkg = ctx.packageName
        val uid = getPackageUid(ctx, pkg)
        val results = mutableListOf<String>()

        // 1. Device idle whitelist (Doze exemption)
        val idle = addToDeviceIdleWhitelist(pkg)
        results.add(if (idle) "✓ Doze whitelist" else "✗ Doze whitelist")

        // 2. AutoStart — try known Xiaomi appops codes
        val auto1 = setAppOpsMode(OP_XIAOMI_AUTOSTART_1, uid, pkg)
        val auto2 = setAppOpsMode(OP_XIAOMI_AUTOSTART_2, uid, pkg)
        val autoOk = auto1 || auto2
        results.add(if (autoOk) "✓ AutoStart" else "✗ AutoStart")

        // 3. Background run permissions (standard Android appops)
        val bg1 = setAppOpsMode(OP_RUN_IN_BACKGROUND, uid, pkg)
        val bg2 = setAppOpsMode(OP_RUN_ANY_IN_BACKGROUND, uid, pkg)
        val bgOk = bg1 && bg2
        results.add(if (bgOk) "✓ Background run" else "✗ Background run")

        // 4. Xiaomi battery saver per-app
        setAppOpsMode(OP_XIAOMI_BATTERY_SAVER, uid, pkg)

        // 5. Standby bucket → ACTIVE (no binder API, use setUsageStandbyBucket)
        val bucket = setStandbyBucketActive(pkg)
        results.add(if (bucket) "✓ Standby bucket" else "✗ Standby bucket")

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

    /** Get the UID for a package (needed by IAppOpsService.setMode). */
    private fun getPackageUid(ctx: Context, packageName: String): Int {
        return try {
            ctx.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (_: Throwable) {
            -1
        }
    }

    /**
     * Sets app standby bucket to ACTIVE via IUsageStatsManager binder.
     * Equivalent to: `adb shell am set-standby-bucket <package> active`
     */
    private fun setStandbyBucketActive(packageName: String): Boolean {
        return try {
            val binder = ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("usagestats")
            )
            val stubClass = Class.forName("android.app.usage.IUsageStatsManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val manager = asInterface.invoke(null, binder)
            // setAppStandbyBucket(packageName, bucket, callingPackage)
            // STANDBY_BUCKET_ACTIVE = 10
            val setMethod = manager!!.javaClass.getMethod(
                "setAppStandbyBucket",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            setMethod.invoke(manager, packageName, 10, "com.android.shell")
            Log.d(TAG, "Set standby bucket to ACTIVE for $packageName")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "setStandbyBucket failed: ${e.message}")
            false
        }
    }

    // ── Manual ADB fallback ─────────────────────────────────────────────────────

    /**
     * Returns the ADB commands the user can run manually if Shizuku is not available.
     * These are the same operations [applyAllProtections] executes via Shizuku binder.
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

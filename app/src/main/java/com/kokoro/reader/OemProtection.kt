package com.kokoro.reader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Comprehensive OEM battery whitelist handler.
 *
 * Handles aggressive background killing on all major Android manufacturers:
 * Xiaomi/Redmi/POCO, Samsung, Huawei/Honor, OnePlus, Oppo/Realme, Vivo,
 * Meizu, Asus, Lenovo/Motorola, Nokia, Sony, Letv/LeEco.
 *
 * Uses two layers:
 * 1. Standard Android battery optimization exemption (all devices)
 * 2. OEM-specific AutoStart/battery manager intents (per manufacturer)
 *
 * Sources: dontkillmyapp.com, AutoStarter library, community research.
 */
object OemProtection {

    private const val TAG = "OemProtection"
    private const val PREFS_NAME = "oem_protection"
    private const val KEY_AUTOSTART_PROMPTED = "autostart_prompted"
    private const val KEY_BATTERY_PROMPTED = "battery_prompted"

    /** Known OEM AutoStart/battery manager intent targets. Ordered by likelihood of working. */
    private val OEM_INTENTS: Map<String, List<ComponentName>> = mapOf(
        "xiaomi" to listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        "redmi" to listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        "poco" to listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        "samsung" to listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity"),
            ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity"),
        ),
        "huawei" to listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ),
        "honor" to listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        ),
        "oneplus" to listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            ComponentName("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
        ),
        "oppo" to listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ),
        "realme" to listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
        ),
        "vivo" to listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ),
        "meizu" to listOf(
            ComponentName("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"),
        ),
        "asus" to listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"),
        ),
        "lenovo" to listOf(
            ComponentName("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity"),
        ),
        "nokia" to listOf(
            ComponentName("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"),
        ),
        "sony" to listOf(
            ComponentName("com.sonymobile.cta", "com.sonymobile.cta.SomcCTAMainActivity"),
        ),
        "letv" to listOf(
            ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        ),
        "tecno" to listOf(
            ComponentName("com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity"),
        ),
        "infinix" to listOf(
            ComponentName("com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity"),
        ),
        "htc" to listOf(
            ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity"),
        ),
    )

    /** Detect which OEM this device is from. Returns lowercase manufacturer key. */
    fun detectOem(): String {
        return Build.MANUFACTURER.lowercase().let { mfr ->
            OEM_INTENTS.keys.firstOrNull { mfr.contains(it) } ?: mfr
        }
    }

    /** True if this device needs OEM-specific protection beyond standard Android. */
    fun needsOemProtection(): Boolean {
        return detectOem() in OEM_INTENTS
    }

    // ── Standard Android battery exemption ────────────────────────────────────

    /** True if the app is NOT exempt from battery optimization (bad). */
    fun isBatteryOptimized(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** Request battery optimization exemption via system dialog. */
    fun requestBatteryExemption(ctx: Context): Boolean {
        if (!isBatteryOptimized(ctx)) return true  // Already exempt
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            Log.i(TAG, "Requested battery optimization exemption")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Battery exemption request failed: ${e.message}")
            false
        }
    }

    // ── OEM-specific AutoStart intent ─────────────────────────────────────────

    /**
     * Opens the OEM's AutoStart/battery manager settings for this app.
     * Tries all known intents for the detected manufacturer.
     * Returns true if any intent resolved and launched.
     */
    fun openAutoStartSettings(ctx: Context): Boolean {
        val oem = detectOem()
        val intents = OEM_INTENTS[oem] ?: return false

        for (component in intents) {
            try {
                val intent = Intent().apply {
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                // Check if the intent resolves before launching
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(intent)
                    Log.i(TAG, "Opened AutoStart settings: ${component.className}")
                    return true
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Intent failed for ${component.flattenToShortString()}: ${e.message}")
            }
        }

        // Fallback: try to open app-specific battery settings
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            Log.i(TAG, "Opened app settings as fallback")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "All AutoStart intents failed for $oem: ${e.message}")
            false
        }
    }

    // ── Combined protection flow ──────────────────────────────────────────────

    data class ProtectionStatus(
        val oem: String,
        val batteryExempt: Boolean,
        val autoStartOpened: Boolean
    )

    /**
     * Apply all available protections for this device.
     * Called from HomeFragment on first launch or when protection is incomplete.
     *
     * Flow:
     * 1. Any OEM → request battery exemption (system dialog)
     * 2. Any OEM → open AutoStart settings (one-time prompt)
     */
    fun applyProtections(ctx: Context): ProtectionStatus {
        val oem = detectOem()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var autoStartOpened = false

        Log.i(TAG, "Applying protections for OEM: $oem (${Build.MANUFACTURER} ${Build.MODEL})")

        // 1. Battery optimization exemption (all devices)
        val batteryExempt = !isBatteryOptimized(ctx)
        if (!batteryExempt && !prefs.getBoolean(KEY_BATTERY_PROMPTED, false)) {
            requestBatteryExemption(ctx)
            prefs.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply()
        }

        // 2. AutoStart settings (one-time prompt for OEM devices)
        if (needsOemProtection() && !prefs.getBoolean(KEY_AUTOSTART_PROMPTED, false)) {
            autoStartOpened = openAutoStartSettings(ctx)
            if (autoStartOpened) {
                prefs.edit().putBoolean(KEY_AUTOSTART_PROMPTED, true).apply()
            }
        }

        return ProtectionStatus(
            oem = oem,
            batteryExempt = !isBatteryOptimized(ctx),
            autoStartOpened = autoStartOpened
        )
    }

    /** Reset "already prompted" flags (e.g., after app update or user request). */
    fun resetPromptFlags(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_AUTOSTART_PROMPTED)
            .remove(KEY_BATTERY_PROMPTED)
            .apply()
    }
}

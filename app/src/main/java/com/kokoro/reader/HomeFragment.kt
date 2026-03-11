package com.kokoro.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class HomeFragment : Fragment() {

    private lateinit var prefs: android.content.SharedPreferences
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var engineRefreshRunnable: Runnable? = null
    private var permissionRefreshRunnable: Runnable? = null

    companion object {
        private const val AUDIO_PERMISSION_CODE = 1001
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? =
        try { i.inflate(R.layout.fragment_home, c, false) }
        catch (e: Exception) { android.util.Log.e("HomeFragment", "Layout inflation failed", e); null }

    override fun onViewCreated(v: View, s: Bundle?) {
        try {
            initializeViews(v)
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error initializing home view", e)
            showErrorFallback(v, "Home failed to load: ${e.message}")
        }
    }

    private fun showErrorFallback(v: View, message: String) {
        try {
            val ctx = context ?: return
            val root = v as? ViewGroup ?: return
            root.removeAllViews()
            root.addView(TextView(ctx).apply {
                text = "⚠ $message\n\nTry restarting the app."
                setTextColor(0xFFff4444.toInt())
                textSize = 14f
                setPadding(20, 40, 20, 40)
            })
        } catch (e2: Exception) {
            android.util.Log.e("HomeFragment", "Error showing fallback UI", e2)
        }
    }

    private fun initializeViews(v: View) {
        val ctx = context ?: return
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val statusText       = v.findViewById<TextView>(R.id.status_text)
        val serviceStatusText = v.findViewById<TextView>(R.id.service_status_text)
        val engineStatusText = v.findViewById<TextView>(R.id.engine_status_text)
        val logPathText      = v.findViewById<TextView>(R.id.log_path_text)
        val btnPermission    = v.findViewById<Button>(R.id.btn_permission)
        val switchEnabled    = v.findViewById<SwitchCompat>(R.id.switch_enabled)
        val switchVoiceCmd   = v.findViewById<SwitchCompat>(R.id.switch_voice_commands)
        val voiceCmdStatus   = v.findViewById<TextView>(R.id.voice_command_status)
        val spinnerMode      = v.findViewById<Spinner>(R.id.spinner_read_mode)
        val switchAppName    = v.findViewById<SwitchCompat>(R.id.switch_read_app_name)
        val switchDnd        = v.findViewById<SwitchCompat>(R.id.switch_dnd)
        val layoutDnd        = v.findViewById<View>(R.id.layout_dnd)
        val seekDndStart     = v.findViewById<SeekBar>(R.id.seek_dnd_start)
        val seekDndEnd       = v.findViewById<SeekBar>(R.id.seek_dnd_end)
        val txtDndStart      = v.findViewById<TextView>(R.id.txt_dnd_start)
        val txtDndEnd        = v.findViewById<TextView>(R.id.txt_dnd_end)
        val btnStop          = v.findViewById<Button>(R.id.btn_stop)
        val btnDumpDebug     = v.findViewById<Button>(R.id.btn_dump_debug)
        val btnShowInitLogs  = v.findViewById<Button>(R.id.btn_show_init_logs)
        val initLogPanel     = v.findViewById<View>(R.id.init_log_panel)
        val initLogContent   = v.findViewById<TextView>(R.id.init_log_content)
        val btnForceReport   = v.findViewById<Button>(R.id.btn_force_report)

        updateStatus(statusText, serviceStatusText, btnPermission)
        updateEngineStatus(engineStatusText)
        updateLogPath(logPathText)

        btnDumpDebug.setOnClickListener {
            val c = context ?: return@setOnClickListener
            TtsBridge.dumpDebugLog(c)
            Toast.makeText(c, "Debug log saved to /storage/emulated/0/WW_Andene/Kyōkan/Logs/", Toast.LENGTH_LONG).show()
        }

        // ── Show init logs panel (inline) ──────────────────────────────────────
        var logPanelVisible = false
        var logRefreshRunnable: Runnable? = null

        btnShowInitLogs.setOnClickListener {
            val c = context ?: return@setOnClickListener
            logPanelVisible = !logPanelVisible
            if (logPanelVisible) {
                initLogPanel.visibility = View.VISIBLE
                btnShowInitLogs.text = "▼ Hide init logs"
                // Request process log from :tts process
                TtsBridge.requestProcessLog(c)
                // Start auto-refresh: poll every 1s
                logRefreshRunnable = object : Runnable {
                    override fun run() {
                        if (!isAdded || !logPanelVisible) return
                        refreshLogPanel(c, initLogContent)
                        refreshHandler.postDelayed(this, 1000)
                    }
                }
                // First read after a short delay to let :tts write the file
                refreshHandler.postDelayed(logRefreshRunnable!!, 300)
            } else {
                initLogPanel.visibility = View.GONE
                btnShowInitLogs.text = "▶ Show init logs"
                logRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
                logRefreshRunnable = null
            }
        }

        // ── Force report to GitHub ─────────────────────────────────────────────
        btnForceReport.setOnClickListener {
            val c = context ?: return@setOnClickListener
            btnForceReport.isEnabled = false
            btnForceReport.text = "Sending…"
            // Also request a fresh debug dump so the report has current data
            TtsBridge.dumpDebugLog(c)
            Thread {
                val result = GitHubReporter.forceReport(c)
                activity?.runOnUiThread {
                    btnForceReport.isEnabled = true
                    btnForceReport.text = "Force report to GitHub"
                    Toast.makeText(c, result, Toast.LENGTH_LONG).show()
                }
            }.apply { name = "ForceReport"; isDaemon = true; start() }
        }
        btnPermission.setOnClickListener {
            val granted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !prefs.getBoolean("restricted_settings_prompted", false)) {
                // Android 13+, first attempt: sideloaded apps need "Allow restricted settings".
                // Open app details where the user can enable it via the ⋮ menu.
                // On the next tap we'll go straight to notification listener settings.
                prefs.edit().putBoolean("restricted_settings_prompted", true).apply()
                try {
                    val appDetails = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${ctx.packageName}")
                    )
                    startActivity(appDetails)
                    Toast.makeText(ctx,
                        "Tap ⋮ menu → \"Allow restricted settings\", then come back",
                        Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            } else {
                // Either not Tiramisu, already prompted for restricted settings, or granted.
                // Go straight to notification listener settings so user can enable the service.
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        switchEnabled.isChecked = prefs.getBoolean("service_enabled", true)
        switchEnabled.setOnCheckedChangeListener { _, v2 -> prefs.edit().putBoolean("service_enabled", v2).apply() }

        // ── Voice Commands toggle ─────────────────────────────────────────────
        switchVoiceCmd.isChecked = prefs.getBoolean("voice_commands_enabled", false)
        updateVoiceCommandStatus(voiceCmdStatus)

        switchVoiceCmd.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("voice_commands_enabled", enabled).apply()
            val ctx = context ?: return@setOnCheckedChangeListener
            if (enabled) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
                } else {
                    TtsBridge.startVoiceCommands(ctx)
                }
            } else {
                TtsBridge.stopVoiceCommands(ctx)
            }
            updateVoiceCommandStatus(voiceCmdStatus)
        }

        // Voice commands are started by the :tts process (NotificationReaderService)
        // based on the preference. No direct VoiceCommandListener access from UI.

        val modes = arrayOf("Full (App + Title + Text)", "App + Title", "App name only", "Text only")
        val modeVals = arrayOf("full", "title_only", "app_only", "text_only")
        spinnerMode.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerMode.setSelection(modeVals.indexOf(prefs.getString("read_mode", "full")).coerceAtLeast(0))
        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v2: View?, pos: Int, id: Long) {
                val mode = modeVals.getOrNull(pos) ?: return
                prefs.edit().putString("read_mode", mode).apply()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        switchAppName.isChecked = prefs.getBoolean("read_app_name", true)
        switchAppName.setOnCheckedChangeListener { _, v2 -> prefs.edit().putBoolean("read_app_name", v2).apply() }

        switchDnd.isChecked = prefs.getBoolean("dnd_enabled", false)
        layoutDnd.visibility = if (switchDnd.isChecked) View.VISIBLE else View.GONE
        switchDnd.setOnCheckedChangeListener { _, v2 ->
            prefs.edit().putBoolean("dnd_enabled", v2).apply()
            layoutDnd.visibility = if (v2) View.VISIBLE else View.GONE
        }

        val dndStart = prefs.getInt("dnd_start", 22)
        seekDndStart.max = 23; seekDndStart.progress = dndStart
        txtDndStart.text = "Silence from: %02d:00".format(dndStart)
        seekDndStart.setOnSeekBarChangeListener(seek { h ->
            prefs.edit().putInt("dnd_start", h).apply()
            txtDndStart.text = "Silence from: %02d:00".format(h)
        })

        val dndEnd = prefs.getInt("dnd_end", 8)
        seekDndEnd.max = 23; seekDndEnd.progress = dndEnd
        txtDndEnd.text = "Until: %02d:00".format(dndEnd)
        seekDndEnd.setOnSeekBarChangeListener(seek { h ->
            prefs.edit().putInt("dnd_end", h).apply()
            txtDndEnd.text = "Until: %02d:00".format(h)
        })

        btnStop.setOnClickListener { context?.let { TtsBridge.stop(it) } }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        startEngineStatusRefresh()
        startPermissionRefresh()
        promptOemProtections()
    }

    // ── Xiaomi MIUI/HyperOS: brute-force past aggressive process killing ──────

    /**
     * On all OEM devices, bypasses aggressive background killing:
     *
     * Handles: Xiaomi/Redmi/POCO, Samsung, Huawei/Honor, OnePlus, Oppo/Realme,
     * Vivo, Meizu, Asus, Lenovo, Nokia, Sony, Letv, Tecno, Infinix.
     *
     * 1. Standard Android: Battery exemption dialog
     * 2. OEM-specific: AutoStart/battery manager settings intent
     */
    private fun promptOemProtections() {
        val ctx = context ?: return

        // Skip if we've already applied everything successfully
        if (prefs.getBoolean("oem_protection_complete", false) &&
            !OemProtection.isBatteryOptimized(ctx)) return

        Thread {
            val result = OemProtection.applyProtections(ctx)
            activity?.runOnUiThread {
                when {
                    result.batteryExempt && !OemProtection.needsOemProtection() -> {
                        prefs.edit().putBoolean("oem_protection_complete", true).apply()
                    }
                    result.autoStartOpened -> {
                        Toast.makeText(ctx, "Enable AutoStart for Kyōkan to keep it running", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        stopEngineStatusRefresh()
        stopPermissionRefresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshStatus()
            startEngineStatusRefresh()
            startPermissionRefresh()
        } else {
            stopEngineStatusRefresh()
            stopPermissionRefresh()
        }
    }

    /** Polls engine status every second while loading, stops when ready or errored */
    private fun startEngineStatusRefresh() {
        stopEngineStatusRefresh()
        engineRefreshRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                val c = context ?: return
                view?.findViewById<TextView>(R.id.engine_status_text)?.let { updateEngineStatus(it) }
                val s = TtsBridge.readStatus(c)
                if (!s.ready && s.error == null) {
                    refreshHandler.postDelayed(this, 1000)
                } else {
                    refreshStatus()
                }
            }
        }
        refreshHandler.postDelayed(engineRefreshRunnable!!, 1000)
    }

    private fun stopEngineStatusRefresh() {
        engineRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        engineRefreshRunnable = null
    }

    /**
     * Polls permission + service status every 2s for up to 120s after returning from settings.
     * The system can take 10-30s to bind the NotificationListenerService after the user
     * toggles permission, so we need a generous window.
     */
    private fun startPermissionRefresh() {
        stopPermissionRefresh()
        var attempts = 0
        permissionRefreshRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                refreshStatus()
                val nowGranted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
                val serviceAlive = context?.let { TtsBridge.readStatus(it).alive } == true
                // Stop polling once permission granted AND service is alive, or after 120s
                if ((nowGranted && serviceAlive) || ++attempts > 60) {
                    // Also kick off engine status polling since service just came alive
                    if (nowGranted) startEngineStatusRefresh()
                    return
                }
                refreshHandler.postDelayed(this, 2000)
            }
        }
        refreshHandler.postDelayed(permissionRefreshRunnable!!, 2000)
    }

    private fun stopPermissionRefresh() {
        permissionRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        permissionRefreshRunnable = null
    }

    private fun refreshStatus() {
        try {
            view?.let {
                updateStatus(
                    it.findViewById(R.id.status_text),
                    it.findViewById(R.id.service_status_text),
                    it.findViewById(R.id.btn_permission)
                )
                updateEngineStatus(it.findViewById(R.id.engine_status_text))
                updateLogPath(it.findViewById(R.id.log_path_text))
                updateVoiceCommandStatus(it.findViewById(R.id.voice_command_status))
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error refreshing status", e)
        }
    }

    override fun onDestroyView() {
        try {
            stopEngineStatusRefresh()
            stopPermissionRefresh()
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error in onDestroyView", e)
        }
        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        try {
            if (requestCode == AUDIO_PERMISSION_CODE) {
                val ctx = context ?: return
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    TtsBridge.startVoiceCommands(ctx)
                } else {
                    prefs.edit().putBoolean("voice_commands_enabled", false).apply()
                    view?.findViewById<SwitchCompat>(R.id.switch_voice_commands)?.isChecked = false
                    Toast.makeText(ctx, "Microphone permission required for voice commands", Toast.LENGTH_SHORT).show()
                }
                view?.let { updateVoiceCommandStatus(it.findViewById(R.id.voice_command_status)) }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error in onRequestPermissionsResult", e)
        }
    }

    private fun updateStatus(tv: TextView, serviceStatusTv: TextView, btn: Button) {
        val ctx = context ?: return
        val granted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
        // Reset restricted-settings flag once permission is granted, so the flow
        // restarts correctly if the user ever revokes and needs to re-grant.
        if (granted) prefs.edit().putBoolean("restricted_settings_prompted", false).apply()
        tv.text = if (granted) "✓ Active — reading notifications"
                  else "✗ Notification access required"
        tv.setTextColor(ctx.getColor(if (granted) android.R.color.holo_green_dark else android.R.color.holo_red_dark))

        // Change button appearance based on permission state
        if (granted) {
            btn.text = "Notification Settings"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.surface))
            btn.setTextColor(ctx.getColor(R.color.text_dim))
        } else {
            btn.text = "Grant Permission"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.green))
            btn.setTextColor(0xFF000000.toInt())
        }

        // Update contextual permission instructions
        val instructionsTv = view?.findViewById<TextView>(R.id.permission_instructions)
        if (granted) {
            val serviceAlive = TtsBridge.readStatus(ctx).alive
            if (serviceAlive) {
                instructionsTv?.visibility = android.view.View.GONE
            } else {
                instructionsTv?.visibility = android.view.View.VISIBLE
                instructionsTv?.text = "Permission granted. Service is starting — this may take a moment."
                instructionsTv?.setTextColor(ctx.getColor(R.color.status_warning))
            }
        } else {
            instructionsTv?.visibility = android.view.View.VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alreadyPrompted = prefs.getBoolean("restricted_settings_prompted", false)
                instructionsTv?.text = if (!alreadyPrompted) {
                    "Step 1: Tap button below → tap ⋮ menu → \"Allow restricted settings\"\nStep 2: Come back → tap button again → enable Kyōkan in the list"
                } else {
                    "Tap the button below → find Kyōkan in the list → enable it"
                }
            } else {
                instructionsTv?.text = "Tap the button below → find Kyōkan in the list → enable it"
            }
            instructionsTv?.setTextColor(ctx.getColor(R.color.status_warning))
        }

        // Check service alive status via cross-process status file
        val serviceAlive = TtsBridge.readStatus(ctx).alive
        serviceStatusTv.text = when {
            granted && serviceAlive -> "⬤ Service running in background"
            granted -> "◯ Service starting…"
            else -> "◯ Service not active"
        }
        serviceStatusTv.setTextColor(ctx.getColor(when {
            granted && serviceAlive -> R.color.status_active
            granted -> R.color.status_warning
            else -> R.color.status_inactive
        }))
    }

    private fun updateEngineStatus(tv: TextView) {
        val ctx = context ?: return
        val s = TtsBridge.readStatus(ctx)

        val isFallback = s.ready && s.status.contains("fallback", ignoreCase = true)
        tv.text = when {
            isFallback -> "⬤ TTS engine: ready (Piper fallback)"
            s.ready -> "⬤ TTS engine: ready"
            s.error != null -> "✗ TTS engine: ${s.error}"
            s.initProgress > 0 -> "◯ TTS engine: ${s.status} (${s.initProgress}%)"
            else -> "◯ TTS engine: ${s.status}"
        }
        tv.setTextColor(ctx.getColor(when {
            isFallback -> R.color.status_warning
            s.ready -> R.color.status_active
            s.error != null -> R.color.status_error
            else -> R.color.status_warning
        }))

        // Show/hide retry button when engine is in error state
        val retryBtn = view?.findViewById<Button>(R.id.btn_retry_engine)
        if (retryBtn != null) {
            if (s.error != null && !s.ready) {
                retryBtn.visibility = View.VISIBLE
                retryBtn.setOnClickListener {
                    retryBtn.visibility = View.GONE
                    tv.text = "◯ TTS engine: retrying…"
                    tv.setTextColor(ctx.getColor(R.color.status_warning))
                    TtsBridge.retryEngineInit(ctx)
                    startEngineStatusRefresh()
                }
            } else {
                retryBtn.visibility = View.GONE
            }
        }
    }

    private fun updateLogPath(tv: TextView) {
        val logDir = ReaderApplication.resolvedLogDir
        val logFiles = try { logDir?.listFiles { f -> f.name.endsWith(".log") } } catch (_: Exception) { null }
        val logCount = logFiles?.size ?: 0
        val path = ReaderApplication.logLocationDescription
        tv.text = "logs ($logCount): $path"

        // Tap to copy the most recent log file content to clipboard
        tv.setOnClickListener {
            try {
                val ctx = context ?: return@setOnClickListener
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager

                // Find the most recent log file
                val latestLog = try {
                    logDir?.listFiles { f -> f.name.endsWith(".log") }
                        ?.maxByOrNull { it.lastModified() }
                } catch (_: Exception) { null }

                if (latestLog != null && latestLog.exists()) {
                    val content = latestLog.readText()
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Last log", content))
                    Toast.makeText(ctx, "Last log copied (${latestLog.name})", Toast.LENGTH_SHORT).show()
                } else {
                    // No log files — fall back to copying the path
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Log path", logDir?.absolutePath ?: path))
                    Toast.makeText(ctx, "No logs yet — path copied", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error copying log", e)
            }
        }
    }

    /**
     * Refreshes the init log panel with process log from :tts + latest init log file.
     */
    private fun refreshLogPanel(ctx: Context, tv: TextView) {
        try {
            // Re-request fresh process log from :tts
            TtsBridge.requestProcessLog(ctx)

            val sb = StringBuilder()

            // 1. Process log (live init steps from :tts memory)
            val processLog = TtsBridge.readProcessLog(ctx)
            if (processLog.isNotEmpty()) {
                sb.appendLine("═══ LIVE PROCESS LOG ═══")
                sb.appendLine(processLog)
                sb.appendLine()
            }

            // 2. TTS status file
            val statusFile = java.io.File(ctx.filesDir, "tts_status.json")
            if (statusFile.exists()) {
                sb.appendLine("═══ TTS STATUS ═══")
                try {
                    val json = org.json.JSONObject(statusFile.readText())
                    sb.appendLine("ready    : ${json.optBoolean("ready")}")
                    sb.appendLine("status   : ${json.optString("status")}")
                    sb.appendLine("error    : ${json.optString("error").ifEmpty { "(none)" }}")
                    sb.appendLine("alive    : ${json.optBoolean("alive")}")
                    sb.appendLine("progress : ${json.optInt("initProgress")}%")
                    val ts = json.optLong("ts", 0)
                    if (ts > 0) {
                        val age = (System.currentTimeMillis() - ts) / 1000
                        sb.appendLine("last update: ${age}s ago")
                    }
                } catch (_: Throwable) {
                    sb.appendLine(statusFile.readText())
                }
                sb.appendLine()
            }

            // 3. Latest init log file
            val dirs = listOfNotNull(
                ReaderApplication.resolvedLogDir,
                java.io.File(ctx.applicationContext.filesDir, "logs")
            )
            val latestLog = dirs.flatMap { dir ->
                dir.listFiles { f -> f.name.startsWith("engine_init_") }?.toList() ?: emptyList()
            }.maxByOrNull { it.lastModified() }

            if (latestLog != null && latestLog.exists()) {
                sb.appendLine("═══ LATEST INIT LOG (${latestLog.name}) ═══")
                sb.appendLine(latestLog.readText())
            }

            if (sb.isEmpty()) {
                sb.appendLine("(no logs available — engine may not have started yet)")
            }

            tv.text = sb.toString()
        } catch (e: Throwable) {
            tv.text = "(error reading logs: ${e.message})"
        }
    }

    private fun updateVoiceCommandStatus(tv: TextView) {
        val ctx = context ?: return
        val enabled = prefs.getBoolean("voice_commands_enabled", false)
        val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val s = TtsBridge.readStatus(ctx)
        val listening = s.voiceCmdListening

        tv.text = when {
            !enabled -> "Voice commands: off"
            !hasPerm -> "Voice commands: microphone permission needed"
            listening -> {
                val wake = s.voiceCmdWakeWord
                if (wake.isNotBlank())
                    "🎤 Say \"$wake\" + command: repeat · how long ago? · stop · what time?"
                else
                    "🎤 Listening for: \"repeat\" · \"how long ago?\" · \"stop\" · \"what time?\""
            }
            else -> "Voice commands: starting…"
        }
        val colorCtx = context ?: return
        tv.setTextColor(colorCtx.getColor(when {
            listening -> R.color.status_info
            enabled && !hasPerm -> R.color.status_warning
            else -> R.color.status_inactive
        }))
    }

    private fun seek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) onChange(v) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}

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

        updateStatus(statusText, serviceStatusText, btnPermission)
        updateEngineStatus(engineStatusText)
        updateLogPath(logPathText)
        btnPermission.setOnClickListener {
            val granted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: sideloaded apps need "Allow restricted settings" first.
                // Open app details where the user can enable it via the ⋮ menu.
                try {
                    val appDetails = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${ctx.packageName}")
                    )
                    startActivity(appDetails)
                    Toast.makeText(ctx,
                        "Tap ⋮ menu → \"Allow restricted settings\", then grant notification access",
                        Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            } else {
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
                // Check RECORD_AUDIO permission
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
                } else {
                    VoiceCommandListener.start(ctx.applicationContext)
                }
            } else {
                VoiceCommandListener.stop()
            }
            updateVoiceCommandStatus(voiceCmdStatus)
        }

        VoiceCommandListener.onStatusChanged = { _ ->
            activity?.runOnUiThread {
                if (isAdded) view?.findViewById<TextView>(R.id.voice_command_status)?.let {
                    updateVoiceCommandStatus(it)
                }
            }
        }

        // Start voice commands if enabled and permission granted
        val voiceCmdCtx = context
        if (voiceCmdCtx != null &&
            prefs.getBoolean("voice_commands_enabled", false) &&
            ContextCompat.checkSelfPermission(voiceCmdCtx, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            VoiceCommandListener.start(voiceCmdCtx.applicationContext)
        }

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

        btnStop.setOnClickListener { NotificationReaderService.instance?.stopSpeaking() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshStatus()
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
            VoiceCommandListener.onStatusChanged = null
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
                    VoiceCommandListener.start(ctx.applicationContext)
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
        tv.text = if (granted) "✓ Active — reading notifications"
                  else "✗ Notification access required"
        tv.setTextColor(ctx.getColor(if (granted) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        btn.text = if (granted) "Notification Settings" else "Grant Permission"

        // Show service instance and foreground status
        val serviceAlive = NotificationReaderService.instance != null
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
        val status = SherpaEngine.statusMessage
        val error = SherpaEngine.errorMessage
        val ready = SherpaEngine.isReady

        tv.text = when {
            ready -> "⬤ TTS engine: ready"
            error != null -> "✗ TTS engine: $error"
            else -> "◯ TTS engine: $status"
        }
        tv.setTextColor(ctx.getColor(when {
            ready -> R.color.status_active
            error != null -> R.color.status_error
            else -> R.color.status_warning
        }))
    }

    private fun updateLogPath(tv: TextView) {
        val logDir = ReaderApplication.resolvedLogDir
        val logCount = try { logDir?.listFiles { f -> f.name.endsWith(".log") }?.size ?: 0 } catch (_: Exception) { 0 }
        val path = ReaderApplication.logLocationDescription
        tv.text = "logs ($logCount): $path"

        // Tap to copy path to clipboard
        tv.setOnClickListener {
            try {
                val ctx = context ?: return@setOnClickListener
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Log path", logDir?.absolutePath ?: path))
                Toast.makeText(ctx, "Log path copied", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error copying log path", e)
            }
        }
    }

    private fun updateVoiceCommandStatus(tv: TextView) {
        val ctx = context ?: return
        val enabled = prefs.getBoolean("voice_commands_enabled", false)
        val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val listening = VoiceCommandListener.isListening

        tv.text = when {
            !enabled -> "Voice commands: off"
            !hasPerm -> "Voice commands: microphone permission needed"
            listening -> {
                val wake = VoiceCommandListener.wakeWord
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

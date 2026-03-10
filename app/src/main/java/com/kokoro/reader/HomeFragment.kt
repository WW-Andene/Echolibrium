package com.kokoro.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
        private const val STORAGE_PERMISSION_CODE = 1002
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

        // Eagerly warm up the voice engine so it's ready when user wants to test
        SherpaEngine.warmUp(ctx.applicationContext)

        // ── Storage permission for crash logs ─────────────────────────────────
        // Crash logs are written to /storage/emulated/0/WW_Andene/
        // On Android 11+ this needs MANAGE_EXTERNAL_STORAGE ("All files access")
        // On Android ≤10 this needs WRITE_EXTERNAL_STORAGE
        requestStoragePermissionIfNeeded()

        updateStatus(statusText, serviceStatusText, btnPermission)
        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
        try {
            view?.let {
                updateStatus(
                    it.findViewById(R.id.status_text),
                    it.findViewById(R.id.service_status_text),
                    it.findViewById(R.id.btn_permission)
                )
                updateVoiceCommandStatus(it.findViewById(R.id.voice_command_status))
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error in onResume", e)
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

    /**
     * Ensures storage permission is granted so crash logs can be written to
     * /storage/emulated/0/WW_Andene/.
     *
     * Android 11+ (API 30+): needs MANAGE_EXTERNAL_STORAGE → opens "All files access" settings.
     * Android ≤10 (API ≤29): needs WRITE_EXTERNAL_STORAGE → standard permission dialog.
     */
    private fun requestStoragePermissionIfNeeded() {
        try {
            val ctx = context ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ — check MANAGE_EXTERNAL_STORAGE
                if (!Environment.isExternalStorageManager()) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback for devices that don't support the per-app intent
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        } catch (_: Exception) {
                            android.util.Log.w("HomeFragment", "Cannot open all-files-access settings", e)
                        }
                    }
                }
            } else {
                // Android ≤10 — check WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        STORAGE_PERMISSION_CODE
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error requesting storage permission", e)
        }
    }

    private fun updateStatus(tv: TextView, serviceStatusTv: TextView, btn: Button) {
        val ctx = context ?: return
        val granted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
        tv.text = if (granted) "✓ Active — reading notifications with Kokoro"
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
        serviceStatusTv.setTextColor(when {
            granted && serviceAlive -> 0xFF00ff88.toInt()
            granted -> 0xFFffaa00.toInt()
            else -> 0xFF666666.toInt()
        })
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
        tv.setTextColor(when {
            listening -> 0xFF00ccff.toInt()
            enabled && !hasPerm -> 0xFFffaa00.toInt()
            else -> 0xFF666666.toInt()
        })
    }

    private fun seek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) onChange(v) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}

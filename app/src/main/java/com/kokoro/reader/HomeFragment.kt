package com.kokoro.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class HomeFragment : Fragment() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    companion object {
        private const val AUDIO_PERMISSION_CODE = 1001
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_home, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
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
        SherpaEngine.warmUp(requireContext().applicationContext)

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
            if (enabled) {
                // Check RECORD_AUDIO permission
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
                } else {
                    VoiceCommandListener.start(requireContext().applicationContext)
                }
            } else {
                VoiceCommandListener.stop()
            }
            updateVoiceCommandStatus(voiceCmdStatus)
        }

        VoiceCommandListener.onStatusChanged = { listening ->
            activity?.runOnUiThread { updateVoiceCommandStatus(voiceCmdStatus) }
        }

        // Start voice commands if enabled and permission granted
        if (prefs.getBoolean("voice_commands_enabled", false) &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            VoiceCommandListener.start(requireContext().applicationContext)
        }

        val modes = arrayOf("Full (App + Title + Text)", "App + Title", "App name only", "Text only")
        val modeVals = arrayOf("full", "title_only", "app_only", "text_only")
        spinnerMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerMode.setSelection(modeVals.indexOf(prefs.getString("read_mode", "full")).coerceAtLeast(0))
        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v2: View?, pos: Int, id: Long) {
                prefs.edit().putString("read_mode", modeVals[pos]).apply()
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
        view?.let {
            updateStatus(
                it.findViewById(R.id.status_text),
                it.findViewById(R.id.service_status_text),
                it.findViewById(R.id.btn_permission)
            )
            updateVoiceCommandStatus(it.findViewById(R.id.voice_command_status))
        }
    }

    override fun onDestroyView() {
        VoiceCommandListener.onStatusChanged = null
        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoiceCommandListener.start(requireContext().applicationContext)
            } else {
                prefs.edit().putBoolean("voice_commands_enabled", false).apply()
                view?.findViewById<SwitchCompat>(R.id.switch_voice_commands)?.isChecked = false
                Toast.makeText(context, "Microphone permission required for voice commands", Toast.LENGTH_SHORT).show()
            }
            view?.let { updateVoiceCommandStatus(it.findViewById(R.id.voice_command_status)) }
        }
    }

    private fun updateStatus(tv: TextView, serviceStatusTv: TextView, btn: Button) {
        val granted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
        tv.text = if (granted) "✓ Active — reading notifications with Kokoro"
                  else "✗ Notification access required"
        tv.setTextColor(requireContext().getColor(if (granted) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
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
        val enabled = prefs.getBoolean("voice_commands_enabled", false)
        val hasPerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val listening = VoiceCommandListener.isListening

        tv.text = when {
            !enabled -> "Voice commands: off"
            !hasPerm -> "Voice commands: microphone permission needed"
            listening -> "🎤 Listening for: \"repeat\" · \"how long ago?\" · \"stop\" · \"what time?\""
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

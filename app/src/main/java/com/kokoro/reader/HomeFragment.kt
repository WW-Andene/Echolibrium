package com.kokoro.reader

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.content.Context
import android.content.Intent
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
        val btnSetup      = v.findViewById<Button>(R.id.btn_setup)
        val txtSetup      = v.findViewById<TextView>(R.id.txt_setup_status)
        val switchEnabled = v.findViewById<SwitchCompat>(R.id.switch_enabled)
        val spinnerMode   = v.findViewById<Spinner>(R.id.spinner_read_mode)
        val switchAppName = v.findViewById<SwitchCompat>(R.id.switch_read_app_name)
        val switchDnd     = v.findViewById<SwitchCompat>(R.id.switch_dnd)
        val layoutDnd     = v.findViewById<View>(R.id.layout_dnd)
        val seekDndStart  = v.findViewById<SeekBar>(R.id.seek_dnd_start)
        val seekDndEnd    = v.findViewById<SeekBar>(R.id.seek_dnd_end)
        val txtDndStart   = v.findViewById<TextView>(R.id.txt_dnd_start)
        val txtDndEnd     = v.findViewById<TextView>(R.id.txt_dnd_end)
        updateSetup(btnSetup, txtSetup)
        btnSetup.setOnClickListener { openNextSetupStep() }

        switchEnabled.isChecked = prefs.getBoolean("service_enabled", true)
        switchEnabled.setOnCheckedChangeListener { _, v2 -> prefs.edit().putBoolean("service_enabled", v2).apply() }

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

        // Listening toggle — starts/stops VoiceCommandListener with mic permission
        val switchListening = v.findViewById<SwitchCompat>(R.id.switch_listening)
        val listeningStatus = v.findViewById<TextView>(R.id.listening_status)
        switchListening.isChecked = prefs.getBoolean("listening_enabled", false)
        updateListeningStatus(listeningStatus)
        switchListening.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("listening_enabled", enabled).apply()
            val ctx = context ?: return@setOnCheckedChangeListener
            if (enabled) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
                } else {
                    VoiceCommandListener.start(ctx)
                }
            } else {
                VoiceCommandListener.stop()
            }
            updateListeningStatus(listeningStatus)
        }

    }

    override fun onResume() {
        super.onResume()
        view?.let {
            updateSetup(it.findViewById(R.id.btn_setup), it.findViewById(R.id.txt_setup_status))
            updateListeningStatus(it.findViewById(R.id.listening_status))
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == AUDIO_PERMISSION_CODE) {
            val ctx = context ?: return
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoiceCommandListener.start(ctx)
            } else {
                prefs.edit().putBoolean("listening_enabled", false).apply()
                view?.findViewById<SwitchCompat>(R.id.switch_listening)?.isChecked = false
                Toast.makeText(ctx, "Microphone permission required for listening", Toast.LENGTH_SHORT).show()
            }
            view?.let { updateListeningStatus(it.findViewById(R.id.listening_status)) }
        }
    }

    private fun isNotifGranted() = (activity as? MainActivity)?.isNotificationAccessGranted() == true

    private fun isBatteryExempt(): Boolean {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun needsRestricted(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return false
        // Once notification access is granted, restricted settings must be allowed
        if (isNotifGranted()) return false
        // Track whether user already visited restricted settings screen
        return !prefs.getBoolean("restricted_done", false)
    }

    private fun updateSetup(btn: Button, txt: TextView) {
        val notif = isNotifGranted()
        val battery = isBatteryExempt()
        val restricted = needsRestricted()
        // If notif is granted, restricted is implicitly done — persist it
        if (notif) prefs.edit().putBoolean("restricted_done", true).apply()

        val steps = mutableListOf<String>()
        // Order: restricted → battery → notifications
        if (restricted) steps.add("Allow restricted settings")
        if (!battery) steps.add("Disable battery optimization")
        if (!notif) steps.add("Grant notification access")

        if (steps.isEmpty()) {
            btn.text = "✓ All permissions granted"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1a2a1a.toInt())
            btn.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            txt.text = "Restricted ✓  Battery ✓  Notifications ✓"
            txt.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
        } else {
            btn.text = "Setup: ${steps.first()}"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00ff88.toInt())
            btn.setTextColor(0xFF000000.toInt())
            val status = buildString {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    append(if (!restricted) "✓ Restricted" else "✗ Restricted")
                    append("  ·  ")
                }
                append(if (battery) "✓ Battery" else "✗ Battery")
                append("  ·  ")
                append(if (notif) "✓ Notifications" else "✗ Notifications")
            }
            txt.text = status
            txt.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
        }
    }

    private fun openNextSetupStep() {
        val ctx = requireContext()
        when {
            // Step 1: Restricted settings (Android 13+ sideloaded apps)
            needsRestricted() -> {
                prefs.edit().putBoolean("restricted_done", true).apply()
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                })
                Toast.makeText(ctx, "Tap \"Allow restricted settings\" at the top", Toast.LENGTH_LONG).show()
            }
            // Step 2: Battery optimization — system dialog, one tap
            !isBatteryExempt() -> {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                })
            }
            // Step 3: Notification listener access
            !isNotifGranted() -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                Toast.makeText(ctx, "Enable Kokoro Reader", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(ctx, "All set!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateListeningStatus(tv: TextView) {
        val ctx = context ?: return
        val enabled = prefs.getBoolean("listening_enabled", false)
        val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val listening = VoiceCommandListener.isListening
        val wake = VoiceCommandListener.wakeWord

        tv.text = when {
            !enabled -> "Listening: off"
            !hasPerm -> "Listening: microphone permission needed"
            listening && wake.isNotBlank() ->
                "Listening: say \"$wake\" + command: repeat / how long ago? / stop / what time?"
            listening -> "Listening for: repeat / how long ago? / stop / what time?"
            else -> "Listening: starting…"
        }
        tv.setTextColor(ctx.getColor(when {
            listening -> android.R.color.holo_green_dark
            enabled && !hasPerm -> android.R.color.holo_orange_dark
            else -> android.R.color.darker_gray
        }))
    }

    private fun loadWordingRules(): List<Pair<String, String>> {
        val json = prefs.getString("wording_rules", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { val o = arr.getJSONObject(it); Pair(o.optString("find"), o.optString("replace")) }
        } catch (e: Exception) { emptyList() }
    }

    private fun seek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) onChange(v) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}

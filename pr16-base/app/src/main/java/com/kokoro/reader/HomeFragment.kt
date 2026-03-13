package com.kokoro.reader

import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class HomeFragment : Fragment() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_home, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        val statusText    = v.findViewById<TextView>(R.id.status_text)
        val btnPermission = v.findViewById<Button>(R.id.btn_permission)
        val switchEnabled = v.findViewById<SwitchCompat>(R.id.switch_enabled)
        val spinnerMode   = v.findViewById<Spinner>(R.id.spinner_read_mode)
        val switchAppName = v.findViewById<SwitchCompat>(R.id.switch_read_app_name)
        val switchDnd     = v.findViewById<SwitchCompat>(R.id.switch_dnd)
        val layoutDnd     = v.findViewById<View>(R.id.layout_dnd)
        val seekDndStart  = v.findViewById<SeekBar>(R.id.seek_dnd_start)
        val seekDndEnd    = v.findViewById<SeekBar>(R.id.seek_dnd_end)
        val txtDndStart   = v.findViewById<TextView>(R.id.txt_dnd_start)
        val txtDndEnd     = v.findViewById<TextView>(R.id.txt_dnd_end)
        updateStatus(statusText, btnPermission)
        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

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

        // Listening toggle
        val switchListening = v.findViewById<SwitchCompat>(R.id.switch_listening)
        val listeningStatus = v.findViewById<TextView>(R.id.listening_status)
        switchListening.isChecked = prefs.getBoolean("listening_enabled", true)
        updateListeningStatus(listeningStatus)
        switchListening.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("listening_enabled", enabled).apply()
            updateListeningStatus(listeningStatus)
        }

        // Battery optimization bypass
        val btnBattery = v.findViewById<Button>(R.id.btn_battery)
        val txtBatteryStatus = v.findViewById<TextView>(R.id.txt_battery_status)
        updateBatteryStatus(btnBattery, txtBatteryStatus)
        btnBattery.setOnClickListener {
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(requireContext().packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } else {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        // Restricted settings (Android 13+ sideloaded apps)
        val btnRestricted = v.findViewById<Button>(R.id.btn_restricted)
        val txtRestrictedStatus = v.findViewById<TextView>(R.id.txt_restricted_status)
        updateRestrictedStatus(txtRestrictedStatus)
        btnRestricted.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            updateStatus(it.findViewById(R.id.status_text), it.findViewById(R.id.btn_permission))
            updateListeningStatus(it.findViewById(R.id.listening_status))
            updateBatteryStatus(it.findViewById(R.id.btn_battery), it.findViewById(R.id.txt_battery_status))
            updateRestrictedStatus(it.findViewById(R.id.txt_restricted_status))
        }
    }

    private fun updateStatus(tv: TextView, btn: Button) {
        val granted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
        tv.text = if (granted) "✓ Active — reading notifications with Kokoro"
                  else "✗ Notification access required"
        tv.setTextColor(requireContext().getColor(if (granted) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        btn.text = if (granted) "Notification Settings" else "Grant Permission"
    }

    private fun updateBatteryStatus(btn: Button, txt: TextView) {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val exempt = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        if (exempt) {
            btn.text = "✓ Battery Optimization Disabled"
            btn.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            txt.text = "App can run freely in background"
        } else {
            btn.text = "⚡ Disable Battery Optimization"
            btn.setTextColor(0xFF88ccff.toInt())
            txt.text = "Required to keep reading when screen is off"
        }
    }

    private fun updateRestrictedStatus(txt: TextView) {
        val granted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
        if (granted) {
            txt.text = "Notification access is granted"
        } else {
            txt.text = "On Android 13+, sideloaded apps need restricted settings allowed"
        }
    }

    private fun updateListeningStatus(tv: TextView) {
        val enabled = prefs.getBoolean("listening_enabled", true)
        tv.text = if (enabled) "Listening: active — notifications will be read aloud"
                  else "Listening: off — notifications will be ignored"
        tv.setTextColor(requireContext().getColor(
            if (enabled) android.R.color.holo_green_dark else android.R.color.darker_gray
        ))
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

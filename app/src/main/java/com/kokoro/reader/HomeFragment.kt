package com.kokoro.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class HomeFragment : Fragment() {

    companion object {
        private const val SHERPA_TTS_PACKAGE = "com.k2fsa.sherpa.onnx.tts.engine"
    }

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_home, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        val statusText    = v.findViewById<TextView>(R.id.status_text)
        val btnPermission = v.findViewById<Button>(R.id.btn_permission)
        val btnSherpaTts  = v.findViewById<Button>(R.id.btn_sherpa_settings)
        val switchEnabled = v.findViewById<SwitchCompat>(R.id.switch_enabled)
        val spinnerMode   = v.findViewById<Spinner>(R.id.spinner_read_mode)
        val switchAppName = v.findViewById<SwitchCompat>(R.id.switch_read_app_name)
        val switchDnd     = v.findViewById<SwitchCompat>(R.id.switch_dnd)
        val layoutDnd     = v.findViewById<View>(R.id.layout_dnd)
        val seekDndStart  = v.findViewById<SeekBar>(R.id.seek_dnd_start)
        val seekDndEnd    = v.findViewById<SeekBar>(R.id.seek_dnd_end)
        val txtDndStart   = v.findViewById<TextView>(R.id.txt_dnd_start)
        val txtDndEnd     = v.findViewById<TextView>(R.id.txt_dnd_end)
        val btnStop       = v.findViewById<Button>(R.id.btn_stop)

        updateStatus(statusText, btnPermission)
        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Deep-link to SherpaTTS settings to download voices
        updateSherpaTtsButton(btnSherpaTts)
        btnSherpaTts.setOnClickListener {
            val pm = requireContext().packageManager
            val launchIntent = try {
                pm.getLaunchIntentForPackage(SHERPA_TTS_PACKAGE)
            } catch (e: Exception) { null }
                ?: try {
                    pm.getLaunchIntentForPackage("com.k2fsa.sherpa.onnx.tts")
                } catch (e: Exception) { null }

            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                // SherpaTTS not installed — open the GitHub releases page
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models")))
            }
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

        btnStop.setOnClickListener { NotificationReaderService.instance?.stopSpeaking() }
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            updateStatus(it.findViewById(R.id.status_text), it.findViewById(R.id.btn_permission))
            updateSherpaTtsButton(it.findViewById(R.id.btn_sherpa_settings))
        }
    }

    private fun updateSherpaTtsButton(btn: Button) {
        val installed = try {
            requireContext().packageManager.getPackageInfo(SHERPA_TTS_PACKAGE, 0)
            true
        } catch (e: Exception) {
            // Also check the alternate package name (TTS engine vs standalone app)
            try {
                requireContext().packageManager.getPackageInfo("com.k2fsa.sherpa.onnx.tts", 0)
                true
            } catch (e2: Exception) { false }
        }

        if (installed) {
            btn.text = "🎙 Open SherpaTTS — Download Voices"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1a2a3a.toInt())
            btn.setTextColor(0xFF00ccff.toInt())
        } else {
            btn.text = "⚠ SherpaTTS not installed — Tap to download"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2a2a1a.toInt())
            btn.setTextColor(0xFFffcc00.toInt())
        }
    }

    private fun updateStatus(tv: TextView, btn: Button) {
        val granted = (activity as? MainActivity)?.isNotificationAccessGranted() == true
        tv.text = if (granted) "✓ Active — reading notifications with Kokoro"
                  else "✗ Notification access required"
        tv.setTextColor(requireContext().getColor(if (granted) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        btn.text = if (granted) "Notification Settings" else "Grant Permission"
    }

    private fun seek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) onChange(v) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}

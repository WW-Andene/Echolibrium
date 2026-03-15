package com.echolibrium.kyokan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager

class HomeFragment : Fragment() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private val c by lazy { requireContext().container }
    private val viewModel: HomeViewModel by viewModels()

    private var breathAnimator: ObjectAnimator? = null

    companion object {
        private const val AUDIO_PERMISSION_CODE = 1001
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        v.findViewById<TextView>(R.id.txt_version).text =
            "v${BuildConfig.VERSION_NAME}  ·  Kokoro + Piper + Orpheus"

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
        // L15: Show onboarding for first-time users
        val txtOnboarding = v.findViewById<TextView>(R.id.txt_onboarding)
        if (!isNotifGranted() && !prefs.getBoolean("onboarding_dismissed", false)) {
            txtOnboarding.visibility = View.VISIBLE
        }

        updateSetup(btnSetup, txtSetup)
        btnSetup.setOnClickListener { openNextSetupStep() }

        switchEnabled.isChecked = prefs.getBoolean("service_enabled", true)
        switchEnabled.setOnCheckedChangeListener { _, v2 -> prefs.edit().putBoolean("service_enabled", v2).apply() }

        val modes = arrayOf("Full (App + Title + Text)", "App + Title", "App name only", "Text only")
        val modeVals = arrayOf("full", "title_only", "app_only", "text_only")
        spinnerMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerMode.setSelection(modeVals.indexOf(prefs.getString("read_mode", "full")).coerceAtLeast(0))
        spinnerMode.onItemSelectedSkipFirst { pos ->
            prefs.edit().putString("read_mode", modeVals[pos]).apply()
        }

        switchAppName.isChecked = prefs.getBoolean("read_app_name", true)
        switchAppName.setOnCheckedChangeListener { _, v2 -> prefs.edit().putBoolean("read_app_name", v2).apply() }

        switchDnd.isChecked = prefs.getBoolean("dnd_enabled", false)
        layoutDnd.visibility = if (switchDnd.isChecked) View.VISIBLE else View.GONE
        switchDnd.setOnCheckedChangeListener { _, v2 ->
            prefs.edit().putBoolean("dnd_enabled", v2).apply()
            val parent = layoutDnd.parent as? ViewGroup
            if (parent != null) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 250 })
            }
            layoutDnd.visibility = if (v2) View.VISIBLE else View.GONE
        }

        val dndStart = prefs.getInt("dnd_start", 22)
        seekDndStart.max = 23; seekDndStart.progress = dndStart
        txtDndStart.text = getString(R.string.silence_from, formatHour(dndStart))
        seekDndStart.setOnSeekBarChangeListener(onSeekBarChange { h ->
            prefs.edit().putInt("dnd_start", h).apply()
            txtDndStart.text = getString(R.string.silence_from, formatHour(h))
        })

        val dndEnd = prefs.getInt("dnd_end", 8)
        seekDndEnd.max = 23; seekDndEnd.progress = dndEnd
        txtDndEnd.text = getString(R.string.until_time, formatHour(dndEnd))
        seekDndEnd.setOnSeekBarChangeListener(onSeekBarChange { h ->
            prefs.edit().putInt("dnd_end", h).apply()
            txtDndEnd.text = getString(R.string.until_time, formatHour(h))
        })

        // L14: Dark mode toggle
        val switchDarkMode = v.findViewById<SwitchCompat>(R.id.switch_dark_mode)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", true)
        switchDarkMode.setOnCheckedChangeListener { _, dark ->
            prefs.edit().putBoolean("dark_mode", dark).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Listening toggle — delegates to HomeViewModel (M28)
        val switchListening = v.findViewById<SwitchCompat>(R.id.switch_listening)
        val listeningStatus = v.findViewById<TextView>(R.id.listening_status)
        viewModel.listeningEnabled.observe(viewLifecycleOwner, Observer { enabled ->
            if (switchListening.isChecked != enabled) switchListening.isChecked = enabled
            updateListeningStatus(listeningStatus)
        })
        switchListening.setOnCheckedChangeListener { _, enabled ->
            viewModel.setListeningEnabled(enabled)
            val ctx = context ?: return@setOnCheckedChangeListener
            if (enabled) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
                } else {
                    viewModel.startListening()
                }
            } else {
                viewModel.stopListening()
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

    override fun onDestroyView() {
        breathAnimator?.cancel()
        breathAnimator = null
        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == AUDIO_PERMISSION_CODE) {
            val ctx = context ?: return
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewModel.startListening()
            } else {
                viewModel.setListeningEnabled(false)
                Toast.makeText(ctx, getString(R.string.mic_permission_required), Toast.LENGTH_SHORT).show()
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
            btn.text = "\u2713 ${getString(R.string.all_permissions_granted)}"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.btn_primary_bg))
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_ready))
            txt.text = getString(R.string.status_all_granted)
            txt.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_ready))
            // L15: Dismiss onboarding once setup complete
            prefs.edit().putBoolean("onboarding_dismissed", true).apply()
            view?.findViewById<TextView>(R.id.txt_onboarding)?.visibility = View.GONE
            // Post-setup guidance (M22)
            showGuidance()
        } else {
            btn.text = getString(R.string.setup_label, steps.first())
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.primary))
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_accent))
            val status = buildString {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    append(getString(if (!restricted) R.string.status_restricted_ok else R.string.status_restricted_fail))
                    append("  ·  ")
                }
                append(getString(if (battery) R.string.status_battery_ok else R.string.status_battery_fail))
                append("  ·  ")
                append(getString(if (notif) R.string.status_notif_ok else R.string.status_notif_fail))
            }
            txt.text = status
            txt.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_dnd))
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
                Toast.makeText(ctx, getString(R.string.allow_restricted_settings), Toast.LENGTH_LONG).show()
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
                Toast.makeText(ctx, getString(R.string.enable_kyokan), Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(ctx, getString(R.string.all_set), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showGuidance() {
        val guidance = view?.findViewById<TextView>(R.id.txt_guidance) ?: return
        val hasVoice = viewModel.isVoiceReady()
        val hasProfile = VoiceProfile.loadAll(prefs).any { it.voiceName.isNotBlank() }
        val hasRules = AppRule.loadAll(prefs).isNotEmpty()

        val tips = mutableListOf<String>()
        if (!hasVoice) tips.add(getString(R.string.guidance_download_voice))
        if (!hasProfile) tips.add(getString(R.string.guidance_setup_profile))
        if (!hasRules) tips.add(getString(R.string.guidance_configure_apps))

        if (tips.isNotEmpty()) {
            guidance.text = "${getString(R.string.guidance_title)}\n${tips.joinToString("\n")}"
            guidance.visibility = View.VISIBLE
        } else {
            // F-05: Show contextual tip for returning users instead of hiding
            guidance.text = getString(R.string.guidance_all_set)
            guidance.visibility = View.VISIBLE
        }
    }

    private fun updateListeningStatus(tv: TextView) {
        val ctx = context ?: return
        val enabled = prefs.getBoolean("listening_enabled", false)
        val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val listening = c.voiceCommandListener.isListening
        val wake = c.voiceCommandListener.wakeWord

        tv.text = when {
            !enabled -> getString(R.string.listening_off)
            !hasPerm -> getString(R.string.listening_mic_needed)
            listening && wake.isNotBlank() -> getString(R.string.listening_status_wake, wake)
            listening -> getString(R.string.listening_status_active)
            else -> getString(R.string.listening_starting)
        }
        tv.setTextColor(ContextCompat.getColor(ctx, when {
            listening -> R.color.status_ready
            enabled && !hasPerm -> R.color.accent_dnd
            else -> R.color.text_disabled
        }))

        // Breathing animation when actively listening (G-01: skip if animations disabled)
        if (listening && AnimationUtil.areAnimationsEnabled(ctx)) {
            if (breathAnimator == null || !breathAnimator!!.isRunning) {
                breathAnimator = ObjectAnimator.ofFloat(tv, "alpha", 1f, 0.4f).apply {
                    duration = 1500
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        } else {
            breathAnimator?.cancel()
            breathAnimator = null
            tv.alpha = 1f
        }
    }

    /** A-05: Locale-aware hour formatting — US users see "10:00 PM", others see native format. */
    private fun formatHour(hour: Int): String {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
        }
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(cal.time)
    }

}

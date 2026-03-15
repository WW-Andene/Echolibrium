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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import java.io.File

/**
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class HomeFragment : Fragment() {

    private val c by lazy { requireContext().container }
    private val repo by lazy { c.repo }
    private val viewModel: HomeViewModel by viewModels()

    private var breathAnimator: ObjectAnimator? = null

    /** B-10: File picker callback for data import. */
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ctx = context ?: return@registerForActivityResult
        try {
            val json = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@registerForActivityResult
            AlertDialog.Builder(ctx)
                .setTitle(getString(R.string.import_confirm_title))
                .setMessage(getString(R.string.import_confirm_message))
                .setPositiveButton(getString(R.string.import_data)) { _, _ ->
                    if (repo.importAll(org.json.JSONObject(json))) {
                        Toast.makeText(ctx, getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        } catch (_: Exception) {
            Toast.makeText(ctx, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val AUDIO_PERMISSION_CODE = 1001
        private const val POST_NOTIF_PERMISSION_CODE = 1002
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
        val txtOnboarding = v.findViewById<TextView>(R.id.txt_onboarding)
        if (!isNotifGranted() && !repo.getBoolean("onboarding_dismissed", false)) {
            txtOnboarding.visibility = View.VISIBLE
        }

        updateSetup(btnSetup, txtSetup)
        btnSetup.setOnClickListener { openNextSetupStep() }

        switchEnabled.isChecked = repo.getBoolean("service_enabled", true)
        switchEnabled.setOnCheckedChangeListener { _, v2 -> repo.putBoolean("service_enabled", v2) }

        val modes = arrayOf("Full (App + Title + Text)", "App + Title", "App name only", "Text only")
        val modeVals = arrayOf("full", "title_only", "app_only", "text_only")
        spinnerMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerMode.setSelection(modeVals.indexOf(repo.getString("read_mode", "full")).coerceAtLeast(0))
        spinnerMode.onItemSelectedSkipFirst { pos ->
            repo.putString("read_mode", modeVals[pos])
        }

        switchAppName.isChecked = repo.getBoolean("read_app_name", true)
        switchAppName.setOnCheckedChangeListener { _, v2 -> repo.putBoolean("read_app_name", v2) }

        switchDnd.isChecked = repo.getBoolean("dnd_enabled", false)
        layoutDnd.visibility = if (switchDnd.isChecked) View.VISIBLE else View.GONE
        switchDnd.setOnCheckedChangeListener { _, v2 ->
            repo.putBoolean("dnd_enabled", v2)
            val parent = layoutDnd.parent as? ViewGroup
            if (parent != null) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 250 })
            }
            layoutDnd.visibility = if (v2) View.VISIBLE else View.GONE
        }

        val dndStart = repo.getInt("dnd_start", 22)
        seekDndStart.max = 23; seekDndStart.progress = dndStart
        txtDndStart.text = getString(R.string.silence_from, formatHour(dndStart))
        seekDndStart.setOnSeekBarChangeListener(onSeekBarChange { h ->
            repo.putInt("dnd_start", h)
            txtDndStart.text = getString(R.string.silence_from, formatHour(h))
        })

        val dndEnd = repo.getInt("dnd_end", 8)
        seekDndEnd.max = 23; seekDndEnd.progress = dndEnd
        txtDndEnd.text = getString(R.string.until_time, formatHour(dndEnd))
        seekDndEnd.setOnSeekBarChangeListener(onSeekBarChange { h ->
            repo.putInt("dnd_end", h)
            txtDndEnd.text = getString(R.string.until_time, formatHour(h))
        })

        // L14: Dark mode toggle
        val switchDarkMode = v.findViewById<SwitchCompat>(R.id.switch_dark_mode)
        switchDarkMode.isChecked = repo.getBoolean("dark_mode", true)
        switchDarkMode.setOnCheckedChangeListener { _, dark ->
            repo.putBoolean("dark_mode", dark)
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

        // B-10: Data export/import buttons
        v.findViewById<Button>(R.id.btn_export).setOnClickListener { exportData() }
        v.findViewById<Button>(R.id.btn_import).setOnClickListener { importLauncher.launch("application/json") }
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
        if (requestCode == POST_NOTIF_PERMISSION_CODE) {
            // C-01: Refresh setup status after POST_NOTIFICATIONS result (granted or denied)
            view?.let { updateSetup(it.findViewById(R.id.btn_setup), it.findViewById(R.id.txt_setup_status)) }
        }
    }

    private fun isNotifGranted() = (activity as? MainActivity)?.isNotificationAccessGranted() == true

    private fun isBatteryExempt(): Boolean {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun needsRestricted(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return false
        if (isNotifGranted()) return false
        return !repo.getBoolean("restricted_done", false)
    }

    /** C-01: On Android 13+, POST_NOTIFICATIONS must be granted at runtime for foreground service notification. */
    private fun needsPostNotifPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return false
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun updateSetup(btn: Button, txt: TextView) {
        val notif = isNotifGranted()
        val battery = isBatteryExempt()
        val restricted = needsRestricted()
        val postNotif = needsPostNotifPermission()
        if (notif) repo.putBoolean("restricted_done", true)

        val steps = mutableListOf<String>()
        if (restricted) steps.add(getString(R.string.setup_step_restricted))
        if (postNotif) steps.add(getString(R.string.setup_step_post_notif))
        if (!battery) steps.add(getString(R.string.setup_step_battery))
        if (!notif) steps.add(getString(R.string.setup_step_notif_access))

        if (steps.isEmpty()) {
            btn.text = "\u2713 ${getString(R.string.all_permissions_granted)}"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.btn_primary_bg))
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_ready))
            txt.text = getString(R.string.status_all_granted)
            txt.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_ready))
            repo.putBoolean("onboarding_dismissed", true)
            view?.findViewById<TextView>(R.id.txt_onboarding)?.visibility = View.GONE
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
                    append(getString(if (!postNotif) R.string.status_post_notif_ok else R.string.status_post_notif_fail))
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
            needsRestricted() -> {
                repo.putBoolean("restricted_done", true)
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                })
                Toast.makeText(ctx, getString(R.string.allow_restricted_settings), Toast.LENGTH_LONG).show()
            }
            needsPostNotifPermission() -> {
                @Suppress("DEPRECATION")
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), POST_NOTIF_PERMISSION_CODE)
            }
            !isBatteryExempt() -> {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                })
            }
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
        val hasProfile = repo.getProfiles().any { it.voiceName.isNotBlank() }
        val hasRules = repo.getAppRules().isNotEmpty()

        val tips = mutableListOf<String>()
        if (!hasVoice) tips.add(getString(R.string.guidance_download_voice))
        if (!hasProfile) tips.add(getString(R.string.guidance_setup_profile))
        if (!hasRules) tips.add(getString(R.string.guidance_configure_apps))

        if (tips.isNotEmpty()) {
            guidance.text = "${getString(R.string.guidance_title)}\n${tips.joinToString("\n")}"
            guidance.visibility = View.VISIBLE
        } else {
            guidance.text = getString(R.string.guidance_all_set)
            guidance.visibility = View.VISIBLE
        }
    }

    private fun updateListeningStatus(tv: TextView) {
        val ctx = context ?: return
        val enabled = repo.getBoolean("listening_enabled", false)
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

    /** B-10: Export all user data as JSON via share intent. */
    private fun exportData() {
        val ctx = requireContext()
        val json = repo.exportAll().toString(2)
        val file = File(ctx.cacheDir, "kyokan_backup.json")
        file.writeText(json)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, getString(R.string.export_data)))
    }

    /** A-05: Locale-aware hour formatting. */
    private fun formatHour(hour: Int): String {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
        }
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(cal.time)
    }

}

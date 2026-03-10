package com.kokoro.reader

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.*
import android.widget.*
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class AppsFragment : Fragment() {
    private lateinit var prefs: android.content.SharedPreferences
    private var rules = mutableListOf<AppRule>()
    private var profiles = listOf<VoiceProfile>()
    private var container: LinearLayout? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? =
        try { i.inflate(R.layout.fragment_apps, c, false) }
        catch (e: Exception) { android.util.Log.e("AppsFragment", "Layout inflation failed", e); null }

    override fun onViewCreated(v: View, s: Bundle?) {
        try {
            prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            container = v.findViewById(R.id.apps_container)
            rules = AppRule.loadAll(prefs)
            profiles = VoiceProfile.loadAll(prefs)
            v.findViewById<Button>(R.id.btn_load_apps).setOnClickListener { loadInstalledApps() }
            renderRules()
        } catch (e: Exception) {
            android.util.Log.e("AppsFragment", "Error initializing apps view", e)
            showErrorFallback(v, "Apps failed to load: ${e.message}")
        }
    }

    private fun showErrorFallback(v: View, message: String) {
        try {
            val ctx = context ?: return
            val fallback = v.findViewById<ViewGroup>(R.id.apps_container) ?: (v as? ViewGroup) ?: return
            fallback.removeAllViews()
            fallback.addView(TextView(ctx).apply {
                text = "⚠ $message\n\nTry restarting the app."
                setTextColor(0xFFff4444.toInt())
                textSize = 14f
                setPadding(20, 40, 20, 40)
            })
        } catch (e2: Exception) {
            android.util.Log.e("AppsFragment", "Error showing fallback UI", e2)
        }
    }

    private fun loadInstalledApps() {
        val btn = view?.findViewById<Button>(R.id.btn_load_apps) ?: return
        btn.isEnabled = false
        btn.text = "LOADING..."

        val ctx = context?.applicationContext ?: return
        Thread {
            val pm = ctx.packageManager
            val apps = try {
                @Suppress("DEPRECATION") pm.getInstalledApplications(0)
            } catch (e: Exception) { emptyList() }

            val newRules = apps
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .sortedBy { try { pm.getApplicationLabel(it).toString().lowercase() } catch (e: Exception) { it.packageName } }
                .mapNotNull { info ->
                    val pkg = info.packageName
                    val label = try { pm.getApplicationLabel(info).toString() } catch (e: Exception) { pkg }
                    if (rules.none { it.packageName == pkg }) AppRule(packageName = pkg, appLabel = label) else null
                }

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                rules.addAll(newRules)
                if (rules.isEmpty()) {
                    Toast.makeText(context ?: return@runOnUiThread, "No user apps found.", Toast.LENGTH_SHORT).show()
                }
                AppRule.saveAll(rules, prefs)
                renderRules()
                btn.isEnabled = true
                btn.text = "RELOAD APPS"
            }
        }.apply { name = "AppsFragment-load"; isDaemon = true; start() }
    }

    private fun renderRules() {
        if (!isAdded || view == null) return
        val ctx = context ?: return
        val c = container ?: return
        c.removeAllViews()
        rules.forEach { c.addView(buildRow(ctx, it)) }
    }

    private fun buildRow(ctx: android.content.Context, rule: AppRule): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 3); layoutParams = lp
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF111111.toInt()); setPadding(16, 14, 16, 14)
        }
        val label = TextView(ctx).apply {
            text = rule.appLabel; textSize = 14f; setTextColor(0xFFcccccc.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggle = SwitchCompat(ctx).apply { isChecked = rule.enabled }
        top.addView(label); top.addView(toggle); root.addView(top)

        val bottom = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0d0d0d.toInt()); setPadding(16, 8, 16, 8)
            visibility = if (rule.enabled) View.VISIBLE else View.GONE
        }
        toggle.setOnCheckedChangeListener { _, v ->
            bottom.visibility = if (v) View.VISIBLE else View.GONE
            updateRule(rule.copy(enabled = v))
        }

        val modes = arrayOf("Full", "Title only", "App only", "Text only", "Skip")
        val modeVals = arrayOf("full", "title_only", "app_only", "text_only", "skip")
        val modeSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, modes)
            setSelection(modeVals.indexOf(rule.readMode).coerceAtLeast(0))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val mode = modeVals.getOrNull(pos) ?: return
                    rules.find { it.packageName == rule.packageName }?.let { updateRule(it.copy(readMode = mode)) }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        val profileNames = listOf("Global") + profiles.map { "${it.emoji} ${it.name}" }
        val profileSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, profileNames)
            val idx = profiles.indexOfFirst { it.id == rule.profileId }
            setSelection((idx + 1).coerceAtLeast(0))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val pid = if (pos == 0) "" else profiles.getOrNull(pos - 1)?.id ?: ""
                    rules.find { it.packageName == rule.packageName }?.let { updateRule(it.copy(profileId = pid)) }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        bottom.addView(modeSpinner); bottom.addView(profileSpinner); root.addView(bottom)
        return root
    }

    private fun updateRule(updated: AppRule) {
        val idx = rules.indexOfFirst { it.packageName == updated.packageName }
        if (idx >= 0) rules[idx] = updated else rules.add(updated)
        AppRule.saveAll(rules, prefs)
    }
}

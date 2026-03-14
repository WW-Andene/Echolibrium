package com.echolibrium.kyokan

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class AppsFragment : Fragment() {
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private var rules = mutableListOf<AppRule>()
    private var profiles = listOf<VoiceProfile>()
    private lateinit var container: LinearLayout
    private var searchFilter = ""
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_apps, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        container = v.findViewById(R.id.apps_container)
        rules = AppRule.loadAll(prefs)
        profiles = VoiceProfile.loadAll(prefs)
        v.findViewById<Button>(R.id.btn_load_apps).setOnClickListener { loadInstalledApps() }

        // Search bar with debounce (E3: avoid rebuilding all rows on every keystroke)
        v.findViewById<EditText>(R.id.et_search).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchFilter = s?.toString()?.trim() ?: ""
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { renderRules() }
                searchHandler.postDelayed(searchRunnable!!, 250)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Select / Deselect all
        v.findViewById<Button>(R.id.btn_select_all).setOnClickListener { setAllEnabled(true) }
        v.findViewById<Button>(R.id.btn_deselect_all).setOnClickListener { setAllEnabled(false) }

        renderRules()
    }

    private fun setAllEnabled(enabled: Boolean) {
        val filtered = filteredRules()
        filtered.forEach { rule ->
            val idx = rules.indexOfFirst { it.packageName == rule.packageName }
            if (idx >= 0) rules[idx] = rules[idx].copy(enabled = enabled)
        }
        AppRule.saveAll(rules, prefs)
        renderRules()
    }

    private fun filteredRules(): List<AppRule> {
        if (searchFilter.isBlank()) return rules
        return rules.filter { it.appLabel.contains(searchFilter, ignoreCase = true) }
    }

    private fun loadInstalledApps() {
        val btn = view?.findViewById<Button>(R.id.btn_load_apps) ?: return
        btn.isEnabled = false
        btn.text = "LOADING..."

        Thread {
            val pm = requireContext().packageManager
            // Use queryIntentActivities with LAUNCHER intent — works with scoped <queries>
            // instead of QUERY_ALL_PACKAGES. Returns all apps with a launcher icon (user apps).
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = try {
                pm.queryIntentActivities(launcherIntent, 0)
            } catch (e: Exception) { emptyList() }

            val newRules = resolveInfos
                .mapNotNull { ri -> ri.activityInfo?.applicationInfo }
                .distinctBy { it.packageName }
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
                    Toast.makeText(requireContext(), "No user apps found.", Toast.LENGTH_SHORT).show()
                }
                AppRule.saveAll(rules, prefs)
                renderRules()
                btn.isEnabled = true
                btn.text = "RELOAD APPS"
            }
        }.start()
    }

    private fun renderRules() {
        container.removeAllViews()
        filteredRules().forEach { container.addView(buildRow(it)) }
    }

    private fun buildRow(rule: AppRule): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 3); layoutParams = lp
        }

        val top = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF111111.toInt()); setPadding(16, 14, 16, 14)
        }
        val label = TextView(requireContext()).apply {
            text = rule.appLabel; textSize = 14f; setTextColor(0xFFcccccc.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggle = SwitchCompat(requireContext()).apply { isChecked = rule.enabled }
        top.addView(label); top.addView(toggle); root.addView(top)

        val bottom = LinearLayout(requireContext()).apply {
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
        val modeSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, modes)
            setSelection(modeVals.indexOf(rule.readMode).coerceAtLeast(0))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    rules.find { it.packageName == rule.packageName }?.let { updateRule(it.copy(readMode = modeVals[pos])) }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        val profileNames = listOf("Global") + profiles.map { "${it.emoji} ${it.name}" }
        val profileSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, profileNames)
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

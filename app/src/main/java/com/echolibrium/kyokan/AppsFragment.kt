package com.echolibrium.kyokan

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Thread safety (M9): `rules` MutableList is populated from a background thread via
 * `loadInstalledApps()`, but all mutations post results to `runOnUiThread` before
 * updating `rules`, ensuring single-thread access. The `profiles` list is read-only
 * after loading and refreshed via SharedPreferences listener on the main thread.
 *
 * M12: Uses RecyclerView for efficient scrolling of potentially hundreds of app rules.
 */
class AppsFragment : Fragment() {
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private var rules = mutableListOf<AppRule>()
    private var profiles = listOf<VoiceProfile>()
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AppRuleAdapter
    private var searchFilter = ""
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "voice_profiles" && isAdded) {
            profiles = VoiceProfile.loadAll(prefs)
            submitList()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_apps, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        recycler = v.findViewById(R.id.apps_recycler)
        adapter = AppRuleAdapter { updated -> updateRule(updated) }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        rules = AppRule.loadAll(prefs)
        profiles = VoiceProfile.loadAll(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        v.findViewById<Button>(R.id.btn_load_apps).setOnClickListener { loadInstalledApps() }

        // Search bar with debounce (E3: avoid rebuilding all rows on every keystroke)
        v.findViewById<EditText>(R.id.et_search).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchFilter = s?.toString()?.trim() ?: ""
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { submitList() }
                searchHandler.postDelayed(searchRunnable!!, 250)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Select / Deselect all
        v.findViewById<Button>(R.id.btn_select_all).setOnClickListener { setAllEnabled(true) }
        v.findViewById<Button>(R.id.btn_deselect_all).setOnClickListener { setAllEnabled(false) }

        submitList()
    }

    private fun setAllEnabled(enabled: Boolean) {
        val filtered = filteredRules()
        filtered.forEach { rule ->
            val idx = rules.indexOfFirst { it.packageName == rule.packageName }
            if (idx >= 0) rules[idx] = rules[idx].copy(enabled = enabled)
        }
        AppRule.saveAll(rules, prefs)
        submitList()
    }

    private fun filteredRules(): List<AppRule> {
        if (searchFilter.isBlank()) return rules
        return rules.filter { it.appLabel.contains(searchFilter, ignoreCase = true) }
    }

    private fun submitList() {
        adapter.submitList(filteredRules().map { AppRuleAdapter.Item(it, profiles) })
    }

    private fun loadInstalledApps() {
        val btn = view?.findViewById<Button>(R.id.btn_load_apps) ?: return
        btn.isEnabled = false
        btn.text = "LOADING..."

        Thread {
            val pm = requireContext().packageManager
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
                submitList()
                btn.isEnabled = true
                btn.text = "RELOAD APPS"
            }
        }.start()
    }

    override fun onDestroyView() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroyView()
    }

    private fun updateRule(updated: AppRule) {
        val idx = rules.indexOfFirst { it.packageName == updated.packageName }
        if (idx >= 0) rules[idx] = updated else rules.add(updated)
        AppRule.saveAll(rules, prefs)
    }
}

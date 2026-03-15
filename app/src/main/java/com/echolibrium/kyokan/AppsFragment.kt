package com.echolibrium.kyokan

import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class AppsFragment : Fragment() {
    private val c by lazy { requireContext().container }
    private val repo by lazy { c.repo }
    private var rules = mutableListOf<AppRule>()
    private var profiles = listOf<VoiceProfile>()
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AppRuleAdapter
    private var searchFilter = ""
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val repoListener: (String) -> Unit = { key ->
        if (key == "voice_profiles" && isAdded) {
            profiles = repo.getProfiles()
            submitList()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_apps, container, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        recycler = v.findViewById(R.id.apps_recycler)
        adapter = AppRuleAdapter { updated -> updateRule(updated) }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        rules = repo.getAppRules().toMutableList()
        profiles = repo.getProfiles()
        repo.addChangeListener(repoListener)
        v.findViewById<Button>(R.id.btn_load_apps).setOnClickListener { loadInstalledApps() }

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
        repo.saveAppRules(rules)
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
        btn.text = getString(R.string.loading)

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
                    Toast.makeText(requireContext(), getString(R.string.no_user_apps), Toast.LENGTH_SHORT).show()
                }
                repo.saveAppRules(rules)
                submitList()
                btn.isEnabled = true
                btn.text = getString(R.string.reload_apps)
            }
        }.start()
    }

    override fun onDestroyView() {
        repo.removeChangeListener(repoListener)
        super.onDestroyView()
    }

    private fun updateRule(updated: AppRule) {
        val idx = rules.indexOfFirst { it.packageName == updated.packageName }
        if (idx >= 0) rules[idx] = updated else rules.add(updated)
        repo.saveAppRules(rules)
    }
}

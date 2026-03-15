package com.echolibrium.kyokan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for app rules list (M12: replaces LinearLayout + addView).
 */
class AppRuleAdapter(
    private val onRuleChanged: (AppRule) -> Unit
) : ListAdapter<AppRuleAdapter.Item, AppRuleAdapter.ViewHolder>(DIFF) {

    data class Item(val rule: AppRule, val profiles: List<VoiceProfile>)

    companion object {
        private val MODES = arrayOf("Full", "Title only", "App only", "Text only", "Skip")
        private val MODE_VALS = arrayOf("full", "title_only", "app_only", "text_only", "skip")

        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.rule.packageName == b.rule.packageName
            override fun areContentsTheSame(a: Item, b: Item) = a.rule == b.rule && a.profiles == b.profiles
        }
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvLabel: TextView = v.findViewById(R.id.tv_app_label)
        val switchEnabled: SwitchCompat = v.findViewById(R.id.switch_app_enabled)
        val layoutOptions: View = v.findViewById(R.id.layout_options)
        val spinnerMode: Spinner = v.findViewById(R.id.spinner_mode)
        val spinnerProfile: Spinner = v.findViewById(R.id.spinner_profile)
        var modeInitDone = false
        var profileInitDone = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_rule, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(h: ViewHolder, position: Int) {
        val item = getItem(position)
        val rule = item.rule
        val profiles = item.profiles
        val ctx = h.itemView.context

        h.tvLabel.text = rule.appLabel

        // Reset init flags before setting values
        h.modeInitDone = false
        h.profileInitDone = false

        h.switchEnabled.setOnCheckedChangeListener(null)
        h.switchEnabled.isChecked = rule.enabled
        h.layoutOptions.visibility = if (rule.enabled) View.VISIBLE else View.GONE
        h.switchEnabled.setOnCheckedChangeListener { _, checked ->
            h.layoutOptions.visibility = if (checked) View.VISIBLE else View.GONE
            onRuleChanged(rule.copy(enabled = checked))
        }

        // Mode spinner
        h.spinnerMode.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, MODES)
        h.spinnerMode.setSelection(MODE_VALS.indexOf(rule.readMode).coerceAtLeast(0))
        h.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!h.modeInitDone) { h.modeInitDone = true; return }
                onRuleChanged(rule.copy(readMode = MODE_VALS[pos]))
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Profile spinner
        val profileNames = listOf("Global") + profiles.map { "${it.emoji} ${it.name}" }
        h.spinnerProfile.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, profileNames)
        val profileIdx = profiles.indexOfFirst { it.id == rule.profileId }
        h.spinnerProfile.setSelection((profileIdx + 1).coerceAtLeast(0))
        h.spinnerProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!h.profileInitDone) { h.profileInitDone = true; return }
                val pid = if (pos == 0) "" else profiles.getOrNull(pos - 1)?.id ?: ""
                onRuleChanged(rule.copy(profileId = pid))
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }
}

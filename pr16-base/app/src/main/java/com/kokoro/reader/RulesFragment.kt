package com.kokoro.reader

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

class RulesFragment : Fragment() {
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private val rules = mutableListOf<Pair<String, String>>()
    private lateinit var container: LinearLayout

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_rules, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        container = v.findViewById(R.id.rules_container)

        // ── Collapsible: Word Rules ──
        val labelWord = v.findViewById<TextView>(R.id.label_word_rules)
        val sectionWord = v.findViewById<LinearLayout>(R.id.section_word_rules)
        labelWord.setOnClickListener { toggleSection(labelWord, sectionWord, "WORD RULES") }

        // ── Collapsible: Notification Rules ──
        val labelNotif = v.findViewById<TextView>(R.id.label_notif_rules)
        val sectionNotif = v.findViewById<LinearLayout>(R.id.section_notif_rules)
        labelNotif.setOnClickListener { toggleSection(labelNotif, sectionNotif, "NOTIFICATION RULES") }

        // ── Word rules ──
        loadRules()
        if (rules.isEmpty()) {
            rules.addAll(listOf(
                "WhatsApp" to "Message", "Gmail" to "Email",
                "lol" to "laugh out loud", "tbh" to "to be honest",
                "idk" to "I don't know", "omg" to "oh my god",
                "brb" to "be right back", "ngl" to "not gonna lie",
                "https://" to "link", "http://" to "link"
            ))
            saveRules()
        }
        renderRules()
        v.findViewById<Button>(R.id.btn_add_rule).setOnClickListener {
            rules.add("" to ""); saveRules(); renderRules()
        }

        // ── Notification rules ──
        val switchReadOnce = v.findViewById<SwitchCompat>(R.id.switch_read_once)
        switchReadOnce.isChecked = prefs.getBoolean("notif_read_once", true)
        switchReadOnce.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_read_once", checked).apply()
        }

        val switchSkipSwiped = v.findViewById<SwitchCompat>(R.id.switch_skip_swiped)
        switchSkipSwiped.isChecked = prefs.getBoolean("notif_skip_swiped", true)
        switchSkipSwiped.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_skip_swiped", checked).apply()
        }

        val switchReadOngoing = v.findViewById<SwitchCompat>(R.id.switch_read_ongoing)
        switchReadOngoing.isChecked = prefs.getBoolean("notif_read_ongoing", false)
        switchReadOngoing.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_read_ongoing", checked).apply()
        }

        val txtCooldown = v.findViewById<TextView>(R.id.txt_cooldown)
        val seekCooldown = v.findViewById<SeekBar>(R.id.seek_cooldown)
        val cooldown = prefs.getInt("notif_cooldown", 3)
        seekCooldown.progress = cooldown
        txtCooldown.text = "Cooldown per app: ${cooldown}s"
        seekCooldown.setOnSeekBarChangeListener(seek { value ->
            prefs.edit().putInt("notif_cooldown", value).apply()
            txtCooldown.text = "Cooldown per app: ${value}s"
        })

        val txtMaxQueue = v.findViewById<TextView>(R.id.txt_max_queue)
        val seekMaxQueue = v.findViewById<SeekBar>(R.id.seek_max_queue)
        val maxQueue = prefs.getInt("notif_max_queue", 10)
        seekMaxQueue.progress = maxQueue
        txtMaxQueue.text = "Max queue size: $maxQueue"
        seekMaxQueue.setOnSeekBarChangeListener(seek { value ->
            prefs.edit().putInt("notif_max_queue", value.coerceAtLeast(1)).apply()
            txtMaxQueue.text = "Max queue size: ${value.coerceAtLeast(1)}"
        })
    }

    private fun toggleSection(label: TextView, section: LinearLayout, name: String) {
        val visible = section.visibility == View.VISIBLE
        section.visibility = if (visible) View.GONE else View.VISIBLE
        label.text = "${if (visible) "▸" else "▾"} // $name"
    }

    // ── Word rules ──

    private fun loadRules() {
        rules.clear()
        val json = prefs.getString("wording_rules", null) ?: return
        try {
            val arr = JSONArray(json)
            repeat(arr.length()) { rules.add(arr.getJSONObject(it).optString("find") to arr.getJSONObject(it).optString("replace")) }
        } catch (_: Exception) {}
    }

    private fun saveRules() {
        val arr = JSONArray()
        rules.forEach { (f, r) -> arr.put(JSONObject().apply { put("find", f); put("replace", r) }) }
        prefs.edit().putString("wording_rules", arr.toString()).apply()
    }

    private fun renderRules() {
        container.removeAllViews()
        rules.forEachIndexed { idx, (find, replace) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(12, 10, 12, 10)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 4); layoutParams = lp
            }

            fun editText(hint: String, value: String, onChanged: (String) -> Unit) =
                EditText(requireContext()).apply {
                    setText(value); this.hint = hint; textSize = 13f
                    setTextColor(0xFFcccccc.toInt()); setHintTextColor(0xFF444444.toInt())
                    setBackgroundColor(0xFF222222.toInt()); setPadding(12, 8, 12, 8)
                    addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) { onChanged(s.toString()); saveRules() }
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    })
                }

            val etFind = editText("Find…", find) { rules[idx] = it to rules[idx].second }
            etFind.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)

            val arrow = TextView(requireContext()).apply {
                text = "→"; textSize = 14f; setTextColor(0xFF00ff88.toInt()); setPadding(8, 0, 8, 0)
            }

            val etReplace = editText("Replace…", replace) { rules[idx] = rules[idx].first to it }
            etReplace.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)

            val btnDel = Button(requireContext()).apply {
                text = "✕"; textSize = 12f; setTextColor(0xFFff4444.toInt())
                setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(16, 8, 16, 8)
                setOnClickListener { rules.removeAt(idx); saveRules(); renderRules() }
            }

            row.addView(etFind); row.addView(arrow); row.addView(etReplace); row.addView(btnDel)
            container.addView(row)
        }
    }

    private fun seek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) onChange(v) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}

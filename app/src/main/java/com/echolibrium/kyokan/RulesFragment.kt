package com.echolibrium.kyokan

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import android.widget.ArrayAdapter

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

        val switchStopOnSwipe = v.findViewById<SwitchCompat>(R.id.switch_stop_on_swipe)
        switchStopOnSwipe.isChecked = prefs.getBoolean("notif_stop_on_swipe", false)
        switchStopOnSwipe.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_stop_on_swipe", checked).apply()
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
        seekCooldown.setOnSeekBarChangeListener(onSeekBarChange { value ->
            prefs.edit().putInt("notif_cooldown", value).apply()
            txtCooldown.text = "Cooldown per app: ${value}s"
        })

        val txtMaxQueue = v.findViewById<TextView>(R.id.txt_max_queue)
        val seekMaxQueue = v.findViewById<SeekBar>(R.id.seek_max_queue)
        val maxQueue = prefs.getInt("notif_max_queue", 10)
        seekMaxQueue.progress = maxQueue
        txtMaxQueue.text = "Max queue size: $maxQueue"
        seekMaxQueue.setOnSeekBarChangeListener(onSeekBarChange { value ->
            prefs.edit().putInt("notif_max_queue", value.coerceAtLeast(1)).apply()
            txtMaxQueue.text = "Max queue size: ${value.coerceAtLeast(1)}"
        })

        // ── Collapsible: Language Profiles ──
        val labelLang = v.findViewById<TextView>(R.id.label_lang_profiles)
        val sectionLang = v.findViewById<LinearLayout>(R.id.section_lang_profiles)
        labelLang.setOnClickListener { toggleSection(labelLang, sectionLang, "LANGUAGE PROFILES") }

        val switchLangRouting = v.findViewById<SwitchCompat>(R.id.switch_lang_routing)
        switchLangRouting.isChecked = prefs.getBoolean("lang_routing_enabled", false)
        switchLangRouting.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("lang_routing_enabled", checked).apply()
        }

        setupLangProfileSpinner(v, R.id.spinner_lang_en, "lang_profile_en")
        setupLangProfileSpinner(v, R.id.spinner_lang_fr, "lang_profile_fr")

        setupTranslateRoute(v, R.id.switch_translate_en, R.id.spinner_translate_en,
            R.id.label_translate_en_target, R.id.txt_translate_en_status,
            "translate_en_enabled", "translate_en_lang", "en")
        setupTranslateRoute(v, R.id.switch_translate_fr, R.id.spinner_translate_fr,
            R.id.label_translate_fr_target, R.id.txt_translate_fr_status,
            "translate_fr_enabled", "translate_fr_lang", "fr")
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
                    setTextColor(0xFFcccccc.toInt()); setHintTextColor(0xFF666666.toInt())
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

    private fun setupLangProfileSpinner(v: View, spinnerId: Int, prefKey: String) {
        val spinner = v.findViewById<Spinner>(spinnerId)
        val profiles = VoiceProfile.loadAll(prefs)
        val activeId = prefs.getString("active_profile_id", "") ?: ""
        val activeName = profiles.find { it.id == activeId }?.let { "${it.emoji} ${it.name}" } ?: "Active profile"
        val names = listOf("($activeName)") + profiles.map { "${it.emoji} ${it.name}" }
        val ids = listOf("") + profiles.map { it.id }

        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
        val savedId = prefs.getString(prefKey, "") ?: ""
        val idx = ids.indexOf(savedId).coerceAtLeast(0)
        spinner.setSelection(idx)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString(prefKey, ids[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private val translateCodes = NotificationTranslator.LANGUAGES.keys.toList().filter { it.isNotEmpty() }
    private val translateNames = NotificationTranslator.LANGUAGES.values.toList().filter { it != "Off (no translation)" }

    private fun setupTranslateRoute(v: View, switchId: Int, spinnerId: Int, labelId: Int,
                                     statusId: Int, enabledKey: String, langKey: String,
                                     sourceLang: String) {
        val switch = v.findViewById<SwitchCompat>(switchId)
        val spinner = v.findViewById<Spinner>(spinnerId)
        val label = v.findViewById<TextView>(labelId)
        val status = v.findViewById<TextView>(statusId)

        val enabled = prefs.getBoolean(enabledKey, false)
        switch.isChecked = enabled
        spinner.visibility = if (enabled) View.VISIBLE else View.GONE
        label.visibility = if (enabled) View.VISIBLE else View.GONE

        spinner.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, translateNames)
        val savedLang = prefs.getString(langKey, "") ?: ""
        val idx = translateCodes.indexOf(savedLang).coerceAtLeast(0)
        spinner.setSelection(idx)
        // Persist the resolved lang in case spinner listener doesn't fire (spinner is GONE)
        val resolvedLang = translateCodes[idx]
        if (savedLang.isEmpty() && resolvedLang.isNotEmpty()) {
            prefs.edit().putString(langKey, resolvedLang).apply()
        }
        updateTranslateStatus(status, enabled, if (savedLang.isEmpty()) resolvedLang else savedLang, sourceLang)

        switch.setOnCheckedChangeListener { _, checked ->
            val lang = translateCodes[spinner.selectedItemPosition]
            prefs.edit().putBoolean(enabledKey, checked).putString(langKey, lang).apply()
            spinner.visibility = if (checked) View.VISIBLE else View.GONE
            label.visibility = if (checked) View.VISIBLE else View.GONE
            updateTranslateStatus(status, checked, lang, sourceLang)
            // Pre-download model when toggle is turned on with a language already selected
            if (checked && lang.isNotBlank() && lang != sourceLang) {
                status.text = "Downloading model…"
                NotificationTranslator.ensureModel(sourceLang, lang) { ok ->
                    status.post {
                        updateTranslateStatus(status, true, lang, sourceLang)
                        if (!ok) status.text = "✗ Download failed — needs internet once"
                    }
                }
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val lang = translateCodes[pos]
                prefs.edit().putString(langKey, lang).apply()
                if (switch.isChecked && lang != sourceLang) {
                    status.text = "Downloading model…"
                    NotificationTranslator.ensureModel(sourceLang, lang) { ok ->
                        status.post {
                            updateTranslateStatus(status, true, lang, sourceLang)
                            if (!ok) status.text = "✗ Download failed — needs internet once"
                        }
                    }
                } else {
                    updateTranslateStatus(status, switch.isChecked, lang, sourceLang)
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun updateTranslateStatus(tv: TextView, enabled: Boolean, targetLang: String, sourceLang: String) {
        val srcName = NotificationTranslator.LANGUAGES[sourceLang] ?: sourceLang
        val tgtName = NotificationTranslator.LANGUAGES[targetLang] ?: targetLang
        tv.text = when {
            !enabled -> ""
            targetLang == sourceLang -> "Same language — no translation needed"
            targetLang.isNotBlank() -> "Will translate $srcName → $tgtName before speaking"
            else -> "Select target language"
        }
    }

}

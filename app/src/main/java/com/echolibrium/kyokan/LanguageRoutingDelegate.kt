package com.echolibrium.kyokan

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

/**
 * Delegate handling language-based voice routing and translation (M20: decomposed from RulesFragment).
 */
class LanguageRoutingDelegate(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val container: AppContainer
) {
    private val translateCodes = NotificationTranslator.LANGUAGES.keys.toList().filter { it.isNotEmpty() }
    private val translateNames = NotificationTranslator.LANGUAGES.values.toList().filter { it != "Off (no translation)" }

    /** Called when voice_profiles or active_profile_id changes to refresh spinners. */
    fun refreshProfileSpinners(v: View) {
        setupLangProfileSpinner(v, R.id.spinner_lang_en, "lang_profile_en")
        setupLangProfileSpinner(v, R.id.spinner_lang_fr, "lang_profile_fr")
    }

    fun setup(v: View) {
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

    private fun setupLangProfileSpinner(v: View, spinnerId: Int, prefKey: String) {
        val spinner = v.findViewById<Spinner>(spinnerId)
        val profiles = VoiceProfile.loadAll(prefs)
        val activeId = prefs.getString("active_profile_id", "") ?: ""
        val activeName = profiles.find { it.id == activeId }?.let { "${it.emoji} ${it.name}" } ?: "Active profile"
        val names = listOf("($activeName)") + profiles.map { "${it.emoji} ${it.name}" }
        val ids = listOf("") + profiles.map { it.id }

        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, names)
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

        spinner.adapter = ArrayAdapter(context,
            android.R.layout.simple_spinner_dropdown_item, translateNames)
        val savedLang = prefs.getString(langKey, "") ?: ""
        val idx = translateCodes.indexOf(savedLang).coerceAtLeast(0)
        spinner.setSelection(idx)
        val resolvedLang = translateCodes[idx]
        if (savedLang.isEmpty() && resolvedLang.isNotEmpty()) {
            prefs.edit().putString(langKey, resolvedLang).apply()
        }
        updateTranslateStatus(status, enabled, if (savedLang.isEmpty()) resolvedLang else savedLang, sourceLang)

        switch.setOnCheckedChangeListener { _, checked ->
            val lang = translateCodes[spinner.selectedItemPosition]
            prefs.edit().putBoolean(enabledKey, checked).putString(langKey, lang).apply()
            val parent = spinner.parent as? ViewGroup
            if (parent != null) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 200 })
            }
            spinner.visibility = if (checked) View.VISIBLE else View.GONE
            label.visibility = if (checked) View.VISIBLE else View.GONE
            updateTranslateStatus(status, checked, lang, sourceLang)
            if (checked && lang.isNotBlank() && lang != sourceLang) {
                status.text = "Downloading model…"
                container.notificationTranslator.ensureModel(sourceLang, lang) { ok ->
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
                    container.notificationTranslator.ensureModel(sourceLang, lang) { ok ->
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

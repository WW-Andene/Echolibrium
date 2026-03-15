package com.echolibrium.kyokan

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

/**
 * Delegate handling language-based voice routing and translation (M20: decomposed from RulesFragment).
 * L26: Now dynamically supports all languages in NotificationTranslator.LANGUAGES.
 */
class LanguageRoutingDelegate(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val container: AppContainer
) {
    private val translateEntries = NotificationTranslator.LANGUAGES.entries.filter { it.key.isNotEmpty() }
    private val translateCodes = translateEntries.map { it.key }
    private val translateNames = translateEntries.map { it.value }

    /** Language flags for display — falls back to language code if no emoji. */
    private val langFlags = mapOf(
        "en" to "\uD83C\uDDFA\uD83C\uDDF8\uD83C\uDDEC\uD83C\uDDE7", "fr" to "\uD83C\uDDEB\uD83C\uDDF7",
        "es" to "\uD83C\uDDEA\uD83C\uDDF8", "de" to "\uD83C\uDDE9\uD83C\uDDEA",
        "it" to "\uD83C\uDDEE\uD83C\uDDF9", "pt" to "\uD83C\uDDE7\uD83C\uDDF7",
        "nl" to "\uD83C\uDDF3\uD83C\uDDF1", "ru" to "\uD83C\uDDF7\uD83C\uDDFA",
        "ja" to "\uD83C\uDDEF\uD83C\uDDF5", "ko" to "\uD83C\uDDF0\uD83C\uDDF7",
        "zh" to "\uD83C\uDDE8\uD83C\uDDF3", "ar" to "\uD83C\uDDF8\uD83C\uDDE6",
        "hi" to "\uD83C\uDDEE\uD83C\uDDF3"
    )

    // Track dynamically created profile spinners for refresh
    private val profileSpinners = mutableMapOf<String, Spinner>()

    /** Called when voice_profiles or active_profile_id changes to refresh spinners. */
    fun refreshProfileSpinners(v: View) {
        for ((langCode, spinner) in profileSpinners) {
            refreshSpinner(spinner, "lang_profile_$langCode")
        }
    }

    fun setup(v: View) {
        val switchLangRouting = v.findViewById<SwitchCompat>(R.id.switch_lang_routing)
        switchLangRouting.isChecked = prefs.getBoolean("lang_routing_enabled", false)
        switchLangRouting.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("lang_routing_enabled", checked).apply()
        }

        // Build dynamic voice routing spinners
        val routingContainer = v.findViewById<LinearLayout>(R.id.voice_routing_container)
        routingContainer.removeAllViews()
        profileSpinners.clear()
        for (langCode in translateCodes) {
            val langName = NotificationTranslator.LANGUAGES[langCode] ?: langCode
            val flag = langFlags[langCode] ?: ""
            addVoiceRoutingRow(routingContainer, langCode, "$flag  $langName notifications \u2192 Voice:")
        }

        // Build dynamic translation routes
        val translationContainer = v.findViewById<LinearLayout>(R.id.translation_container)
        translationContainer.removeAllViews()
        for (langCode in translateCodes) {
            val langName = NotificationTranslator.LANGUAGES[langCode] ?: langCode
            addTranslationRow(translationContainer, langCode, langName)
        }
    }

    private fun addVoiceRoutingRow(parent: LinearLayout, langCode: String, label: String) {
        val dp = { px: Int -> (px * context.resources.displayMetrics.density).toInt() }

        val labelTv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            text = label
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        parent.addView(labelTv)

        val spinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        parent.addView(spinner)
        profileSpinners[langCode] = spinner

        val prefKey = "lang_profile_$langCode"
        setupProfileSpinner(spinner, prefKey)
    }

    private fun setupProfileSpinner(spinner: Spinner, prefKey: String) {
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

    private fun refreshSpinner(spinner: Spinner, prefKey: String) {
        val profiles = VoiceProfile.loadAll(prefs)
        val activeId = prefs.getString("active_profile_id", "") ?: ""
        val activeName = profiles.find { it.id == activeId }?.let { "${it.emoji} ${it.name}" } ?: "Active profile"
        val names = listOf("($activeName)") + profiles.map { "${it.emoji} ${it.name}" }
        val ids = listOf("") + profiles.map { it.id }

        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, names)
        val savedId = prefs.getString(prefKey, "") ?: ""
        val idx = ids.indexOf(savedId).coerceAtLeast(0)
        spinner.setSelection(idx)
    }

    private fun addTranslationRow(parent: LinearLayout, sourceLang: String, langName: String) {
        val dp = { px: Int -> (px * context.resources.displayMetrics.density).toInt() }
        val enabledKey = "translate_${sourceLang}_enabled"
        val langKey = "translate_${sourceLang}_lang"

        // Switch row
        val switchRow = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val switchLabel = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = context.getString(R.string.translate_lang_label, langName)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        val switch = SwitchCompat(context)
        switchRow.addView(switchLabel)
        switchRow.addView(switch)
        parent.addView(switchRow)

        // Target label
        val targetLabel = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
            text = context.getString(R.string.translate_to_label)
            setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            textSize = 11f
            setPadding(dp(8), 0, 0, 0)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            visibility = View.GONE
        }
        parent.addView(targetLabel)

        // Target spinner
        val spinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = View.GONE
        }
        parent.addView(spinner)

        // Status text
        val status = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            setTextColor(ContextCompat.getColor(context, R.color.text_dimmed))
            textSize = 10f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        parent.addView(status)

        // Wire up
        val enabled = prefs.getBoolean(enabledKey, false)
        switch.isChecked = enabled
        spinner.visibility = if (enabled) View.VISIBLE else View.GONE
        targetLabel.visibility = if (enabled) View.VISIBLE else View.GONE

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
            val p = spinner.parent as? ViewGroup
            if (p != null) {
                TransitionManager.beginDelayedTransition(p, AutoTransition().apply { duration = 200 })
            }
            spinner.visibility = if (checked) View.VISIBLE else View.GONE
            targetLabel.visibility = if (checked) View.VISIBLE else View.GONE
            updateTranslateStatus(status, checked, lang, sourceLang)
            if (checked && lang.isNotBlank() && lang != sourceLang) {
                status.text = "Downloading model\u2026"
                container.notificationTranslator.ensureModel(sourceLang, lang) { ok ->
                    status.post {
                        updateTranslateStatus(status, true, lang, sourceLang)
                        if (!ok) status.text = "\u2717 Download failed \u2014 needs internet once"
                    }
                }
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val lang = translateCodes[pos]
                prefs.edit().putString(langKey, lang).apply()
                if (switch.isChecked && lang != sourceLang) {
                    status.text = "Downloading model\u2026"
                    container.notificationTranslator.ensureModel(sourceLang, lang) { ok ->
                        status.post {
                            updateTranslateStatus(status, true, lang, sourceLang)
                            if (!ok) status.text = "\u2717 Download failed \u2014 needs internet once"
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
            targetLang == sourceLang -> "Same language \u2014 no translation needed"
            targetLang.isNotBlank() -> "Will translate $srcName \u2192 $tgtName before speaking"
            else -> "Select target language"
        }
    }
}

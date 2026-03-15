package com.echolibrium.kyokan

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

/**
 * Delegate handling language-based voice routing and translation (M20).
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class LanguageRoutingDelegate(
    private val context: Context,
    private val repo: SettingsRepository,
    private val container: AppContainer
) {
    private val translateEntries = NotificationTranslator.LANGUAGES.entries.filter { it.key.isNotEmpty() }
    private val translateCodes = translateEntries.map { it.key }
    private val translateNames = translateEntries.map { it.value }

    private val langFlags = mapOf(
        "en" to "\uD83C\uDDFA\uD83C\uDDF8\uD83C\uDDEC\uD83C\uDDE7", "fr" to "\uD83C\uDDEB\uD83C\uDDF7",
        "es" to "\uD83C\uDDEA\uD83C\uDDF8", "de" to "\uD83C\uDDE9\uD83C\uDDEA",
        "it" to "\uD83C\uDDEE\uD83C\uDDF9", "pt" to "\uD83C\uDDE7\uD83C\uDDF7",
        "nl" to "\uD83C\uDDF3\uD83C\uDDF1", "ru" to "\uD83C\uDDF7\uD83C\uDDFA",
        "ja" to "\uD83C\uDDEF\uD83C\uDDF5", "ko" to "\uD83C\uDDF0\uD83C\uDDF7",
        "zh" to "\uD83C\uDDE8\uD83C\uDDF3", "ar" to "\uD83C\uDDF8\uD83C\uDDE6",
        "hi" to "\uD83C\uDDEE\uD83C\uDDF3"
    )

    private val profileSpinners = mutableMapOf<String, Spinner>()

    fun refreshProfileSpinners(v: View) {
        val profiles = repo.getProfiles()
        val activeId = repo.activeProfileId
        for ((langCode, spinner) in profileSpinners) {
            refreshSpinner(spinner, "lang_profile_$langCode", profiles, activeId)
        }
    }

    fun setup(v: View) {
        val switchLangRouting = v.findViewById<SwitchCompat>(R.id.switch_lang_routing)
        switchLangRouting.isChecked = repo.getBoolean("lang_routing_enabled", false)
        switchLangRouting.setOnCheckedChangeListener { _, checked ->
            repo.putBoolean("lang_routing_enabled", checked)
        }

        // D-02: Cache profile list once instead of re-parsing per language
        val profiles = repo.getProfiles()
        val activeId = repo.activeProfileId

        val routingContainer = v.findViewById<LinearLayout>(R.id.voice_routing_container)
        routingContainer.removeAllViews()
        profileSpinners.clear()
        for (langCode in translateCodes) {
            val langName = NotificationTranslator.LANGUAGES[langCode] ?: langCode
            val flag = langFlags[langCode] ?: ""
            addVoiceRoutingRow(routingContainer, langCode, "$flag  $langName notifications \u2192 Voice:", profiles, activeId)
        }

        val translationContainer = v.findViewById<LinearLayout>(R.id.translation_container)
        translationContainer.removeAllViews()
        for (langCode in translateCodes) {
            val langName = NotificationTranslator.LANGUAGES[langCode] ?: langCode
            addTranslationRow(translationContainer, langCode, langName)
        }
    }

    private fun addVoiceRoutingRow(parent: LinearLayout, langCode: String, label: String, profiles: List<VoiceProfile>, activeId: String) {
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
            setPaddingRelative(dp(8), dp(8), dp(8), dp(8))
        }
        parent.addView(spinner)
        profileSpinners[langCode] = spinner

        val prefKey = "lang_profile_$langCode"
        setupProfileSpinner(spinner, prefKey, profiles, activeId)
    }

    private fun setupProfileSpinner(spinner: Spinner, prefKey: String, profiles: List<VoiceProfile>, activeId: String) {
        val activeName = profiles.find { it.id == activeId }?.let { "${it.emoji} ${it.name}" } ?: context.getString(R.string.active_profile_fallback)
        val names = listOf("($activeName)") + profiles.map { "${it.emoji} ${it.name}" }
        val ids = listOf("") + profiles.map { it.id }

        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, names)
        val savedId = repo.getString(prefKey)
        val idx = ids.indexOf(savedId).coerceAtLeast(0)
        spinner.setSelection(idx)

        spinner.onItemSelectedSkipFirst { position ->
            repo.putString(prefKey, ids[position])
        }
    }

    private fun refreshSpinner(spinner: Spinner, prefKey: String, profiles: List<VoiceProfile>, activeId: String) {
        val activeName = profiles.find { it.id == activeId }?.let { "${it.emoji} ${it.name}" } ?: context.getString(R.string.active_profile_fallback)
        val names = listOf("($activeName)") + profiles.map { "${it.emoji} ${it.name}" }
        val ids = listOf("") + profiles.map { it.id }

        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, names)
        val savedId = repo.getString(prefKey)
        val idx = ids.indexOf(savedId).coerceAtLeast(0)
        spinner.setSelection(idx)
    }

    private fun addTranslationRow(parent: LinearLayout, sourceLang: String, langName: String) {
        val dp = { px: Int -> (px * context.resources.displayMetrics.density).toInt() }
        val enabledKey = "translate_${sourceLang}_enabled"
        val langKey = "translate_${sourceLang}_lang"

        val switchRow = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(dp(8), dp(8), dp(8), dp(8))
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

        val targetLabel = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
            text = context.getString(R.string.translate_to_label)
            setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            textSize = 11f
            setPaddingRelative(dp(8), 0, 0, 0)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            visibility = View.GONE
        }
        parent.addView(targetLabel)

        val spinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
            setPaddingRelative(dp(8), dp(8), dp(8), dp(8))
            visibility = View.GONE
        }
        parent.addView(spinner)

        val status = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            setTextColor(ContextCompat.getColor(context, R.color.text_dimmed))
            textSize = 10f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        parent.addView(status)

        val enabled = repo.getBoolean(enabledKey, false)
        switch.isChecked = enabled
        spinner.visibility = if (enabled) View.VISIBLE else View.GONE
        targetLabel.visibility = if (enabled) View.VISIBLE else View.GONE

        spinner.adapter = ArrayAdapter(context,
            android.R.layout.simple_spinner_dropdown_item, translateNames)
        val savedLang = repo.getString(langKey)
        val idx = translateCodes.indexOf(savedLang).coerceAtLeast(0)
        spinner.setSelection(idx)
        val resolvedLang = translateCodes[idx]
        if (savedLang.isEmpty() && resolvedLang.isNotEmpty()) {
            repo.putString(langKey, resolvedLang)
        }
        updateTranslateStatus(status, enabled, if (savedLang.isEmpty()) resolvedLang else savedLang, sourceLang)

        switch.setOnCheckedChangeListener { _, checked ->
            val lang = translateCodes[spinner.selectedItemPosition]
            repo.putBoolean(enabledKey, checked)
            repo.putString(langKey, lang)
            val p = spinner.parent as? ViewGroup
            if (p != null) {
                TransitionManager.beginDelayedTransition(p, AutoTransition().apply { duration = 200 })
            }
            spinner.visibility = if (checked) View.VISIBLE else View.GONE
            targetLabel.visibility = if (checked) View.VISIBLE else View.GONE
            updateTranslateStatus(status, checked, lang, sourceLang)
            if (checked && lang.isNotBlank() && lang != sourceLang) {
                status.text = context.getString(R.string.downloading_model)
                container.notificationTranslator.ensureModel(sourceLang, lang) { ok ->
                    status.post {
                        updateTranslateStatus(status, true, lang, sourceLang)
                        if (!ok) status.text = context.getString(R.string.download_model_failed)
                    }
                }
            }
        }

        spinner.onItemSelectedSkipFirst { pos ->
            val lang = translateCodes[pos]
            repo.putString(langKey, lang)
            if (switch.isChecked && lang != sourceLang) {
                status.text = context.getString(R.string.downloading_model)
                container.notificationTranslator.ensureModel(sourceLang, lang) { ok ->
                    status.post {
                        updateTranslateStatus(status, true, lang, sourceLang)
                        if (!ok) status.text = context.getString(R.string.download_model_failed)
                    }
                }
            } else {
                updateTranslateStatus(status, switch.isChecked, lang, sourceLang)
            }
        }
    }

    private fun updateTranslateStatus(tv: TextView, enabled: Boolean, targetLang: String, sourceLang: String) {
        val srcName = NotificationTranslator.LANGUAGES[sourceLang] ?: sourceLang
        val tgtName = NotificationTranslator.LANGUAGES[targetLang] ?: targetLang
        tv.text = when {
            !enabled -> ""
            targetLang == sourceLang -> context.getString(R.string.translate_same_language)
            targetLang.isNotBlank() -> context.getString(R.string.translate_will_translate, srcName, tgtName)
            else -> context.getString(R.string.translate_select_target)
        }
    }
}

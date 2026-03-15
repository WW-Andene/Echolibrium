package com.echolibrium.kyokan

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

/**
 * Thin host fragment for the Rules tab (M20: decomposed into delegates).
 *
 * Each feature area is handled by its own delegate:
 * - [WordRulesDelegate]: find/replace word rules
 * - [NotificationRulesDelegate]: notification behavior (read-once, cooldown, etc.)
 * - [LanguageRoutingDelegate]: per-language voice routing + translation
 */
class RulesFragment : Fragment() {
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private val c by lazy { requireContext().container }

    private lateinit var wordRules: WordRulesDelegate
    private lateinit var notificationRules: NotificationRulesDelegate
    private lateinit var languageRouting: LanguageRoutingDelegate

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if ((key == "voice_profiles" || key == "active_profile_id") && isAdded) {
            val v = view ?: return@OnSharedPreferenceChangeListener
            languageRouting.refreshProfileSpinners(v)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_rules, container, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Delegates have intentionally different constructor signatures:
        // - WordRulesDelegate(Context, SharedPreferences, LinearLayout) — needs direct container access for view building
        // - NotificationRulesDelegate(SharedPreferences) — pure preference management, no view building
        // - LanguageRoutingDelegate(Context, SharedPreferences, AppContainer) — needs container for NotificationTranslator
        wordRules = WordRulesDelegate(requireContext(), prefs, v.findViewById(R.id.rules_container))
        notificationRules = NotificationRulesDelegate(prefs)
        languageRouting = LanguageRoutingDelegate(requireContext(), prefs, c)

        // Collapsible sections (L-04: shared helper)
        CollapsibleSectionHelper.setup(v, R.id.label_word_rules, R.id.section_word_rules, "Word replacements")
        CollapsibleSectionHelper.setup(v, R.id.label_notif_rules, R.id.section_notif_rules, "Notification behavior")
        CollapsibleSectionHelper.setup(v, R.id.label_lang_profiles, R.id.section_lang_profiles, "Language & translation")

        // Delegate setup
        wordRules.setup(v)
        notificationRules.setup(v)
        languageRouting.setup(v)
    }

    override fun onDestroyView() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroyView()
    }

}

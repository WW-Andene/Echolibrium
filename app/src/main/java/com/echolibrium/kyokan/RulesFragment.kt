package com.echolibrium.kyokan

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

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

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_rules, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Initialize delegates
        wordRules = WordRulesDelegate(requireContext(), prefs, v.findViewById(R.id.rules_container))
        notificationRules = NotificationRulesDelegate(prefs)
        languageRouting = LanguageRoutingDelegate(requireContext(), prefs, c)

        // Collapsible sections
        setupCollapsible(v, R.id.label_word_rules, R.id.section_word_rules, "Word replacements")
        setupCollapsible(v, R.id.label_notif_rules, R.id.section_notif_rules, "Notification behavior")
        setupCollapsible(v, R.id.label_lang_profiles, R.id.section_lang_profiles, "Language & translation")

        // Delegate setup
        wordRules.setup(v)
        notificationRules.setup(v)
        languageRouting.setup(v)
    }

    override fun onDestroyView() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroyView()
    }

    private fun setupCollapsible(v: View, labelId: Int, sectionId: Int, name: String) {
        val label = v.findViewById<TextView>(labelId)
        val section = v.findViewById<LinearLayout>(sectionId)
        label.setOnClickListener {
            val visible = section.visibility == View.VISIBLE
            val parent = section.parent as? ViewGroup
            if (parent != null) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 250 })
            }
            section.visibility = if (visible) View.GONE else View.VISIBLE
            label.text = "${if (visible) "▸" else "▾"} $name"
        }
    }
}

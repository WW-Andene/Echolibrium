package com.echolibrium.kyokan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

/**
 * Thin host fragment for the Rules tab (M20: decomposed into delegates).
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class RulesFragment : Fragment() {
    private val c by lazy { requireContext().container }
    private val repo by lazy { c.repo }

    private lateinit var wordRules: WordRulesDelegate
    private lateinit var notificationRules: NotificationRulesDelegate
    private lateinit var languageRouting: LanguageRoutingDelegate

    private val repoListener: (String) -> Unit = listener@{ key ->
        if ((key == "voice_profiles" || key == "active_profile_id") && isAdded) {
            val v = view ?: return@listener
            languageRouting.refreshProfileSpinners(v)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_rules, container, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        repo.addChangeListener(repoListener)

        wordRules = WordRulesDelegate(requireContext(), repo, v.findViewById(R.id.rules_container))
        notificationRules = NotificationRulesDelegate(repo)
        languageRouting = LanguageRoutingDelegate(requireContext(), repo, c)

        CollapsibleSectionHelper.setup(v, R.id.label_word_rules, R.id.section_word_rules, "Word replacements")
        CollapsibleSectionHelper.setup(v, R.id.label_notif_rules, R.id.section_notif_rules, "Notification behavior")
        CollapsibleSectionHelper.setup(v, R.id.label_lang_profiles, R.id.section_lang_profiles, "Language & translation")

        wordRules.setup(v)
        notificationRules.setup(v)
        languageRouting.setup(v)
    }

    override fun onDestroyView() {
        repo.removeChangeListener(repoListener)
        super.onDestroyView()
    }

}

package com.echolibrium.kyokan

import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

/**
 * L-04: Shared collapsible section toggle — extracted from RulesFragment and ProfilesFragment.
 * G-04: Sets initial text state explicitly and animates arrow rotation on toggle.
 */
object CollapsibleSectionHelper {

    fun setup(root: View, labelId: Int, sectionId: Int, title: String) {
        val label = root.findViewById<TextView>(labelId)
        val section = root.findViewById<View>(sectionId)
        // G-04: Set initial state explicitly — sections start collapsed (GONE in XML)
        val initiallyExpanded = section.visibility == View.VISIBLE
        label.text = "${if (initiallyExpanded) "\u25BE" else "\u25B8"} $title"
        label.contentDescription = if (initiallyExpanded) "$title, expanded. Tap to collapse."
            else "$title, collapsed. Tap to expand."
        label.setOnClickListener {
            val expanded = section.visibility == View.VISIBLE
            val parent = section.parent as? ViewGroup
            if (parent != null && AnimationUtil.areAnimationsEnabled(root.context)) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 250 })
                // G-04: Subtle pulse on the label to draw attention to state change
                ObjectAnimator.ofFloat(label, "alpha", 1f, 0.5f, 1f).apply {
                    duration = 300
                    start()
                }
            }
            section.visibility = if (expanded) View.GONE else View.VISIBLE
            label.text = "${if (expanded) "\u25B8" else "\u25BE"} $title"
            label.contentDescription = if (expanded) "$title, collapsed. Tap to expand."
                else "$title, expanded. Tap to collapse."
        }
    }
}

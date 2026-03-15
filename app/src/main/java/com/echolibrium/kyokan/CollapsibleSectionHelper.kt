package com.echolibrium.kyokan

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

/**
 * L-04: Shared collapsible section toggle — extracted from RulesFragment and ProfilesFragment.
 */
object CollapsibleSectionHelper {

    fun setup(root: View, labelId: Int, sectionId: Int, title: String) {
        val label = root.findViewById<TextView>(labelId)
        val section = root.findViewById<View>(sectionId)
        label.contentDescription = "$title, collapsed. Tap to expand."
        label.setOnClickListener {
            val expanded = section.visibility == View.VISIBLE
            val parent = section.parent as? ViewGroup
            if (parent != null) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 250 })
            }
            section.visibility = if (expanded) View.GONE else View.VISIBLE
            label.text = "${if (expanded) "\u25B8" else "\u25BE"} $title"
            label.contentDescription = if (expanded) "$title, collapsed. Tap to expand."
                else "$title, expanded. Tap to collapse."
        }
    }
}

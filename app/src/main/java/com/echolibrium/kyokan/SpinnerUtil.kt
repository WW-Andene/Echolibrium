package com.echolibrium.kyokan

import android.view.View
import android.widget.AdapterView
import android.widget.Spinner

/**
 * L23: Reusable spinner setup helper — skips the initial programmatic selection fire.
 */
fun Spinner.onItemSelectedSkipFirst(onSelected: (position: Int) -> Unit) {
    var initialized = false
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (!initialized) { initialized = true; return }
            onSelected(position)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
}

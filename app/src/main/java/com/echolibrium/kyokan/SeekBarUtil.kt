package com.echolibrium.kyokan

import android.widget.SeekBar

/**
 * Shared SeekBar change listener that only fires on user-initiated changes.
 * Replaces duplicated boilerplate in HomeFragment, RulesFragment, and ProfilesFragment.
 */
fun onSeekBarChange(onChange: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
    object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

package com.echolibrium.kyokan

import android.content.SharedPreferences
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/**
 * Delegate handling notification behavior rules (M20: decomposed from RulesFragment).
 */
class NotificationRulesDelegate(private val prefs: SharedPreferences) {

    fun setup(v: View) {
        val switchReadOnce = v.findViewById<SwitchCompat>(R.id.switch_read_once)
        switchReadOnce.isChecked = prefs.getBoolean("notif_read_once", true)
        switchReadOnce.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_read_once", checked).apply()
        }

        val switchSkipSwiped = v.findViewById<SwitchCompat>(R.id.switch_skip_swiped)
        switchSkipSwiped.isChecked = prefs.getBoolean("notif_skip_swiped", true)
        switchSkipSwiped.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_skip_swiped", checked).apply()
        }

        val switchStopOnSwipe = v.findViewById<SwitchCompat>(R.id.switch_stop_on_swipe)
        switchStopOnSwipe.isChecked = prefs.getBoolean("notif_stop_on_swipe", false)
        switchStopOnSwipe.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_stop_on_swipe", checked).apply()
        }

        val switchReadOngoing = v.findViewById<SwitchCompat>(R.id.switch_read_ongoing)
        switchReadOngoing.isChecked = prefs.getBoolean("notif_read_ongoing", false)
        switchReadOngoing.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_read_ongoing", checked).apply()
        }

        val txtCooldown = v.findViewById<TextView>(R.id.txt_cooldown)
        val seekCooldown = v.findViewById<SeekBar>(R.id.seek_cooldown)
        val cooldown = prefs.getInt("notif_cooldown", 3)
        seekCooldown.progress = cooldown
        txtCooldown.text = v.context.getString(R.string.cooldown_label, cooldown)
        seekCooldown.setOnSeekBarChangeListener(onSeekBarChange { value ->
            prefs.edit().putInt("notif_cooldown", value).apply()
            txtCooldown.text = v.context.getString(R.string.cooldown_label, value)
        })

        // L1: min=1 to prevent visual/logical mismatch (0 would be selectable but meaningless)
        val txtMaxQueue = v.findViewById<TextView>(R.id.txt_max_queue)
        val seekMaxQueue = v.findViewById<SeekBar>(R.id.seek_max_queue)
        seekMaxQueue.min = 1
        val maxQueue = prefs.getInt("notif_max_queue", 10).coerceAtLeast(1)
        seekMaxQueue.progress = maxQueue
        txtMaxQueue.text = v.context.getString(R.string.max_queue_label, maxQueue)
        seekMaxQueue.setOnSeekBarChangeListener(onSeekBarChange { value ->
            prefs.edit().putInt("notif_max_queue", value).apply()
            txtMaxQueue.text = v.context.getString(R.string.max_queue_label, value)
        })
    }
}

package com.echolibrium.kyokan

import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/**
 * Delegate handling notification behavior rules (M20: decomposed from RulesFragment).
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class NotificationRulesDelegate(private val repo: SettingsRepository) {

    fun setup(v: View) {
        val switchReadOnce = v.findViewById<SwitchCompat>(R.id.switch_read_once)
        switchReadOnce.isChecked = repo.getBoolean("notif_read_once", true)
        switchReadOnce.setOnCheckedChangeListener { _, checked ->
            repo.putBoolean("notif_read_once", checked)
        }

        val switchSkipSwiped = v.findViewById<SwitchCompat>(R.id.switch_skip_swiped)
        switchSkipSwiped.isChecked = repo.getBoolean("notif_skip_swiped", true)
        switchSkipSwiped.setOnCheckedChangeListener { _, checked ->
            repo.putBoolean("notif_skip_swiped", checked)
        }

        val switchStopOnSwipe = v.findViewById<SwitchCompat>(R.id.switch_stop_on_swipe)
        switchStopOnSwipe.isChecked = repo.getBoolean("notif_stop_on_swipe", false)
        switchStopOnSwipe.setOnCheckedChangeListener { _, checked ->
            repo.putBoolean("notif_stop_on_swipe", checked)
        }

        val switchReadOngoing = v.findViewById<SwitchCompat>(R.id.switch_read_ongoing)
        switchReadOngoing.isChecked = repo.getBoolean("notif_read_ongoing", false)
        switchReadOngoing.setOnCheckedChangeListener { _, checked ->
            repo.putBoolean("notif_read_ongoing", checked)
        }

        val txtCooldown = v.findViewById<TextView>(R.id.txt_cooldown)
        val seekCooldown = v.findViewById<SeekBar>(R.id.seek_cooldown)
        val cooldown = repo.getInt("notif_cooldown", 3)
        seekCooldown.progress = cooldown
        txtCooldown.text = v.context.getString(R.string.cooldown_label, cooldown)
        seekCooldown.setOnSeekBarChangeListener(onSeekBarChange { value ->
            repo.putInt("notif_cooldown", value)
            txtCooldown.text = v.context.getString(R.string.cooldown_label, value)
        })

        val txtMaxQueue = v.findViewById<TextView>(R.id.txt_max_queue)
        val seekMaxQueue = v.findViewById<SeekBar>(R.id.seek_max_queue)
        seekMaxQueue.min = 1
        val maxQueue = repo.getInt("notif_max_queue", 10).coerceAtLeast(1)
        seekMaxQueue.progress = maxQueue
        txtMaxQueue.text = v.context.getString(R.string.max_queue_label, maxQueue)
        seekMaxQueue.setOnSeekBarChangeListener(onSeekBarChange { value ->
            repo.putInt("notif_max_queue", value)
            txtMaxQueue.text = v.context.getString(R.string.max_queue_label, value)
        })
    }
}

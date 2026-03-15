package com.echolibrium.kyokan

import android.content.Context
import android.provider.Settings

/**
 * G-01: Respects system animation scale setting for accessibility.
 * Users with motion sensitivities disable animations in system settings.
 */
object AnimationUtil {

    fun areAnimationsEnabled(context: Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale > 0f
    }
}

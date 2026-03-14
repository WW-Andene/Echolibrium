package com.echolibrium.kyokan

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // NotificationListenerService auto-binds via manifest declaration.
            // Start the foreground service to ensure the TTS pipeline survives
            // aggressive OEM battery management after boot.
            Log.d("BootReceiver", "Boot completed — starting TTS alive service")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    TtsAliveService.start(context)
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    Log.w("BootReceiver", "FGS not allowed at boot on SDK 31+", e)
                }
            } else {
                TtsAliveService.start(context)
            }
        }
    }
}

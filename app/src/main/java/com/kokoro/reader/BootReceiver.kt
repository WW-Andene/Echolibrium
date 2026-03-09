package com.kokoro.reader

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.TextUtils
import android.util.Log

/**
 * Triggered on device boot.
 * Requests that the NotificationListenerService rebinds itself if
 * notification access was previously granted. This ensures the service
 * restarts after a reboot without needing to re-grant permissions.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed — checking notification listener status")

        // Check if our listener is in the enabled list
        val cn = ComponentName(context, NotificationReaderService::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""

        if (!TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())) {
            Log.d(TAG, "Notification access granted — requesting service rebind")
            try {
                NotificationListenerService.requestRebind(cn)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request rebind", e)
            }
        } else {
            Log.w(TAG, "Notification access not granted — service will not start")
        }
    }
}

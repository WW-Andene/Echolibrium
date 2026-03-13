package com.kokoro.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Foreground service that keeps the TTS pipeline alive when the app is backgrounded.
 *
 * Without this, OEMs (Xiaomi, Samsung, etc.) aggressively kill the process,
 * which tears down NotificationReaderService and stops reading notifications.
 *
 * This service:
 *  - Posts a persistent notification (required for foreground services)
 *  - Holds a partial wake lock so the CPU stays on for synthesis
 *  - Survives screen-off and app-switch scenarios
 */
class TtsAliveService : Service() {

    companion object {
        private const val TAG = "TtsAliveService"
        private const val CHANNEL_ID = "tts_alive"
        private const val NOTIFICATION_ID = 1001

        fun start(ctx: Context) {
            val intent = Intent(ctx, TtsAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TtsAliveService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        Log.d(TAG, "TTS alive service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // restart if killed
    }

    override fun onDestroy() {
        releaseWakeLock()
        Log.d(TAG, "TTS alive service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notification Reader Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps notification reading active in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Kyōkan")
                .setContentText("Reading notifications")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Kyōkan")
                .setContentText("Reading notifications")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KokoroReader::TtsAlive"
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}

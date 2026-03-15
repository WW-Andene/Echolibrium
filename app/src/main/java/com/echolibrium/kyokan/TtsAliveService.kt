package com.echolibrium.kyokan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat

/**
 * Foreground service that keeps the TTS pipeline alive when the app is backgrounded.
 *
 * Without this, OEMs (Xiaomi, Samsung, etc.) aggressively kill the process,
 * which tears down NotificationReaderService and stops reading notifications.
 *
 * Uses mediaPlayback FGS type — the correct type for TTS audio output.
 * No permanent wake lock: the mediaPlayback FGS provides sufficient process
 * priority, and AudioPipeline holds a scoped wake lock only during synthesis.
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

    // F-06: Periodically check for synthesis errors and update notification
    private val handler = Handler(Looper.getMainLooper())
    private var lastShownError: String? = null
    private val errorCheckRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val pipeline = try { container.audioPipeline } catch (_: Exception) { null }
            val error = pipeline?.lastError
            if (error != lastShownError) {
                lastShownError = error
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(error))
                // Clear error after showing for 30s
                if (error != null) {
                    handler.postDelayed({ pipeline?.clearError() }, 30_000)
                }
            }
            handler.postDelayed(this, 5_000)
        }
    }
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        isRunning = true
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        handler.postDelayed(errorCheckRunnable, 5_000)
        Log.d(TAG, "TTS alive service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "TTS alive service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(error: String? = null): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // F-06: Show last synthesis error in notification text
        val contentText = if (error != null) "\u26a0 $error" else getString(R.string.notif_text)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_voice)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_voice)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        }
    }
}

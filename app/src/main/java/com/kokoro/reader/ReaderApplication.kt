package com.kokoro.reader

import android.app.Application
import android.util.Log

/**
 * Custom Application class that installs a global uncaught exception handler.
 *
 * This prevents silent crashes from background threads (e.g. native library errors,
 * OutOfMemoryError during model loading) from killing the app without any log output.
 * The handler logs the error and then delegates to the default handler so Android
 * still shows the standard crash dialog.
 */
class ReaderApplication : Application() {

    companion object {
        private const val TAG = "ReaderApplication"
    }

    override fun onCreate() {
        super.onCreate()
        installUncaughtExceptionHandler()
    }

    private fun installUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread '${thread.name}'", throwable)
            // For daemon/background threads, absorb the crash instead of killing the app.
            // These threads (e.g. AudioPipeline-loop, SherpaEngine-warmup, PiperPreload-*)
            // are isolated workers — their failure should not take down the UI.
            if (thread.isDaemon) {
                Log.w(TAG, "Daemon thread '${thread.name}' crashed — absorbing to keep app alive")
                return@setDefaultUncaughtExceptionHandler
            }
            // For the main thread and non-daemon threads, delegate to the default handler
            // so Android shows the standard crash dialog.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

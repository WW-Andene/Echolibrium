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
            // Delegate to the default handler (shows crash dialog, kills process)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

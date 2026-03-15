package com.echolibrium.kyokan

import android.app.Application

/**
 * Custom Application subclass — creates the DI container on startup (M29).
 */
class KyokanApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

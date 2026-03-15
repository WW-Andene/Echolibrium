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
        // Bug 5: Eagerly trigger Room migration on a background thread.
        // AppContainer.repo is lazy — first access runs the SP→Room migration.
        // By touching it here on a background thread, migration runs in parallel
        // with Activity/Fragment creation and is typically done before any
        // fragment calls repo.getProfiles(). If a fragment reads before migration
        // completes, Room's WAL journal ensures it sees a consistent (possibly
        // pre-migration) state, and the ViewModel observer picks up the change
        // once migration inserts complete.
        Thread({
            try {
                container.repo  // triggers lazy init + migration
            } catch (e: Exception) {
                android.util.Log.e("KyokanApp", "Early repo init failed", e)
            }
        }, "RepoMigration").start()
    }
}

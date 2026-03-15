package com.echolibrium.kyokan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager

/**
 * ViewModel for HomeFragment (M28).
 *
 * Manages setup state and listening configuration.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(app)
    private val c = app.container

    private val _listeningEnabled = MutableLiveData(prefs.getBoolean("listening_enabled", false))
    val listeningEnabled: LiveData<Boolean> = _listeningEnabled

    fun setListeningEnabled(enabled: Boolean) {
        _listeningEnabled.value = enabled
        prefs.edit().putBoolean("listening_enabled", enabled).apply()
    }

    fun startListening() {
        c.voiceCommandListener.start(getApplication())
    }

    fun stopListening() {
        c.voiceCommandListener.stop()
    }

    fun isVoiceReady(): Boolean {
        val ctx = getApplication<Application>()
        return c.voiceDownloadManager.isModelReady(ctx) ||
            VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER).any {
                c.piperDownloadManager.isVoiceReady(ctx, it.id)
            } ||
            c.cloudTtsEngine.isEnabled()
    }
}

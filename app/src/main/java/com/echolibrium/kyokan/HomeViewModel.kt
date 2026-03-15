package com.echolibrium.kyokan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * ViewModel for HomeFragment (M28).
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val c = app.container
    private val repo = c.repo

    private val _listeningEnabled = MutableLiveData(repo.getBoolean("listening_enabled", false))
    val listeningEnabled: LiveData<Boolean> = _listeningEnabled

    fun setListeningEnabled(enabled: Boolean) {
        _listeningEnabled.value = enabled
        repo.putBoolean("listening_enabled", enabled)
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

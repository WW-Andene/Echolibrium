package com.echolibrium.kyokan

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager

/**
 * ViewModel for ProfilesFragment (M28).
 *
 * Manages voice profiles, active profile selection, and voice grid state.
 * Survives configuration changes and decouples business logic from the fragment.
 */
class ProfilesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(app)
    private val c = app.container

    private val _profiles = MutableLiveData<List<VoiceProfile>>()
    val profiles: LiveData<List<VoiceProfile>> = _profiles

    private val _activeProfileId = MutableLiveData<String>()
    val activeProfileId: LiveData<String> = _activeProfileId

    private val _currentProfile = MutableLiveData<VoiceProfile>()
    val currentProfile: LiveData<VoiceProfile> = _currentProfile

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "voice_profiles" -> loadProfiles()
            "active_profile_id" -> _activeProfileId.value = prefs.getString("active_profile_id", "")
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        loadProfiles()
    }

    fun loadProfiles() {
        val loaded = VoiceProfile.loadAll(prefs).toMutableList()
        if (loaded.isEmpty()) loaded.add(VoiceProfile())
        _profiles.value = loaded

        val activeId = prefs.getString("active_profile_id", "") ?: ""
        _activeProfileId.value = activeId

        val profile = loaded.find { it.id == activeId } ?: loaded[0]
        _currentProfile.value = profile
    }

    fun setActiveProfile(id: String) {
        _activeProfileId.value = id
        prefs.edit().putString("active_profile_id", id).apply()
        val profile = _profiles.value?.find { it.id == id }
        if (profile != null) _currentProfile.value = profile
    }

    fun updateCurrentProfile(profile: VoiceProfile) {
        _currentProfile.value = profile
    }

    fun saveProfile(profile: VoiceProfile) {
        val list = _profiles.value?.toMutableList() ?: mutableListOf()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        VoiceProfile.saveAll(list, prefs)
        _profiles.value = list
        if (profile.id == _activeProfileId.value) _currentProfile.value = profile
    }

    fun createProfile(name: String): VoiceProfile {
        val profile = VoiceProfile(name = name)
        val list = _profiles.value?.toMutableList() ?: mutableListOf()
        list.add(profile)
        VoiceProfile.saveAll(list, prefs)
        _profiles.value = list
        setActiveProfile(profile.id)
        return profile
    }

    fun deleteCurrentProfile(): Boolean {
        val list = _profiles.value?.toMutableList() ?: return false
        if (list.size <= 1) return false
        val current = _currentProfile.value ?: return false
        list.removeAll { it.id == current.id }
        VoiceProfile.saveAll(list, prefs)
        _profiles.value = list
        setActiveProfile(list[0].id)
        return true
    }

    fun renameProfile(profileId: String, newName: String) {
        val list = _profiles.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == profileId }
        if (idx < 0) return
        val updated = list[idx].copy(name = newName)
        list[idx] = updated
        VoiceProfile.saveAll(list, prefs)
        _profiles.value = list
        if (profileId == _currentProfile.value?.id) _currentProfile.value = updated
    }

    // Download triggers
    fun startKokoroDownload() {
        c.voiceDownloadManager.downloadModel(getApplication())
    }

    fun startPiperDownload(voiceId: String) {
        c.piperDownloadManager.downloadVoice(getApplication(), voiceId)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}

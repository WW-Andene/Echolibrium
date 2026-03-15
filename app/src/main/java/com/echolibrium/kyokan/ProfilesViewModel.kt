package com.echolibrium.kyokan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * ViewModel for ProfilesFragment (M28).
 *
 * I-07: Uses SettingsRepository instead of direct SharedPreferences access.
 */
class ProfilesViewModel(app: Application) : AndroidViewModel(app) {

    private val c = app.container
    private val repo = c.repo

    private val _profiles = MutableLiveData<List<VoiceProfile>>()
    val profiles: LiveData<List<VoiceProfile>> = _profiles

    private val _activeProfileId = MutableLiveData<String>()
    val activeProfileId: LiveData<String> = _activeProfileId

    private val _currentProfile = MutableLiveData<VoiceProfile>()
    val currentProfile: LiveData<VoiceProfile> = _currentProfile

    private val repoListener: (String) -> Unit = { key ->
        when (key) {
            "voice_profiles" -> loadProfiles()
            "active_profile_id" -> _activeProfileId.value = repo.activeProfileId
        }
    }

    init {
        repo.addChangeListener(repoListener)
        loadProfiles()
    }

    fun loadProfiles() {
        val loaded = repo.getProfiles().toMutableList()
        if (loaded.isEmpty()) loaded.add(VoiceProfile())
        _profiles.value = loaded

        val activeId = repo.activeProfileId
        _activeProfileId.value = activeId

        val profile = loaded.find { it.id == activeId } ?: loaded[0]
        _currentProfile.value = profile
    }

    fun setActiveProfile(id: String) {
        _activeProfileId.value = id
        repo.activeProfileId = id
        _profiles.value?.find { it.id == id }?.let { _currentProfile.value = it }
    }

    fun updateCurrentProfile(profile: VoiceProfile) {
        _currentProfile.value = profile
    }

    fun saveProfile(profile: VoiceProfile) {
        val list = _profiles.value?.toMutableList() ?: mutableListOf()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        repo.saveProfiles(list)
        _profiles.value = list
        if (profile.id == _activeProfileId.value) _currentProfile.value = profile
    }

    fun createProfile(name: String): VoiceProfile {
        val profile = VoiceProfile(name = name)
        val list = _profiles.value?.toMutableList() ?: mutableListOf()
        list.add(profile)
        repo.saveProfiles(list)
        _profiles.value = list
        setActiveProfile(profile.id)
        return profile
    }

    fun deleteCurrentProfile(): Boolean {
        val list = _profiles.value?.toMutableList() ?: return false
        if (list.size <= 1) return false
        val current = _currentProfile.value ?: return false
        list.removeAll { it.id == current.id }
        repo.saveProfiles(list)
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
        repo.saveProfiles(list)
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
        repo.removeChangeListener(repoListener)
    }
}

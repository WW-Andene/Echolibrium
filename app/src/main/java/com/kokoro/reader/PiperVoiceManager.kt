package com.kokoro.reader

import android.content.Context

/**
 * Manages Piper TTS voice availability.
 *
 * Voices can be in two locations:
 *   • Bundled — in APK assets/piper-models/, loaded via AssetManager
 *   • Downloaded — in filesDir/piper-models/, loaded via file path
 *
 * This object provides state queries for the UI and engine.
 */
object PiperVoiceManager {

    enum class VoiceState { NOT_AVAILABLE, DOWNLOADING, READY }

    /** Callback for UI refresh when download state changes */
    @Volatile var downloadCallback: ((String, VoiceState) -> Unit)? = null

    /** True if the voice is bundled in the APK */
    fun isBundled(voiceId: String): Boolean =
        PiperVoiceCatalog.byId(voiceId)?.bundled == true

    /** True if the voice is ready to use (either bundled or downloaded) */
    fun isVoiceReady(ctx: Context, voiceId: String): Boolean {
        val voice = PiperVoiceCatalog.byId(voiceId) ?: return false
        if (voice.bundled) return true
        return VoiceDownloadManager.isDownloaded(ctx, voiceId)
    }

    fun getVoiceState(ctx: Context, voiceId: String): VoiceState {
        if (isVoiceReady(ctx, voiceId)) return VoiceState.READY
        if (VoiceDownloadManager.isDownloading(voiceId)) return VoiceState.DOWNLOADING
        return VoiceState.NOT_AVAILABLE
    }

    /** All voice IDs that are currently usable (bundled + downloaded) */
    fun getAvailableVoiceIds(ctx: Context): Set<String> {
        return PiperVoiceCatalog.ALL
            .filter { it.bundled || VoiceDownloadManager.isDownloaded(ctx, it.id) }
            .map { it.id }
            .toSet()
    }
}

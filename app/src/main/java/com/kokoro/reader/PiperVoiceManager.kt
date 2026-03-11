package com.kokoro.reader

import android.content.Context
import java.io.File

/**
 * Manages Piper TTS voice model references.
 *
 * All voices are bundled in the APK's assets/piper-models/ and loaded
 * directly via AssetManager by SherpaEngine — no extraction needed.
 *
 * This object provides state queries and path helpers for the UI.
 */
object PiperVoiceManager {

    private const val TAG = "PiperVoiceManager"

    enum class VoiceState { NOT_AVAILABLE, READY, ERROR }

    /** Callback for UI refresh (kept for compatibility) */
    @Volatile var downloadCallback: ((String, VoiceState) -> Unit)? = null

    /** All voices are bundled in the APK — always returns true for catalog voices */
    fun isBundled(voiceId: String): Boolean = true

    // ── State queries ─────────────────────────────────────────────────────────

    /**
     * All bundled voices are always ready — loaded directly from assets.
     * Returns true for any voice in the catalog.
     */
    fun isVoiceReady(ctx: Context, voiceId: String): Boolean {
        return PiperVoiceCatalog.byId(voiceId) != null
    }

    fun getVoiceState(ctx: Context, voiceId: String): VoiceState {
        if (isVoiceReady(ctx, voiceId)) return VoiceState.READY
        return VoiceState.NOT_AVAILABLE
    }

    /** All catalog voice IDs (no filesystem scan needed) */
    fun getAvailableVoiceIds(ctx: Context): Set<String> {
        return PiperVoiceCatalog.ALL.map { it.id }.toSet()
    }
}

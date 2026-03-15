package com.echolibrium.kyokan

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Manual dependency injection container (M29).
 *
 * Holds all previously-singleton instances. Created once in KyokanApp.onCreate()
 * and accessed via KyokanApp.container throughout the app.
 *
 * I-07 / O-01: All data access goes through [repo] (SettingsRepository).
 * [prefs] is still available for components that need raw SharedPreferences
 * during gradual migration, but prefer [repo] for all new code.
 */
class AppContainer(private val appContext: Context) {

    /** I-07: Single SharedPreferences access point — prefer repo for typed access. */
    val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(appContext)
    }

    /** O-01: Room database for structured data. */
    val database: KyokanDatabase by lazy {
        KyokanDatabase.create(appContext)
    }

    /** I-07 / O-01: Typed repository — single entry point for all data access. */
    val repo: SettingsRepository by lazy {
        SettingsRepository(database, prefs)
    }

    val cloudTtsEngine: CloudTtsEngine = CloudTtsEngine()

    val voiceDownloadManager: VoiceDownloadManager = VoiceDownloadManager()

    val piperDownloadManager: PiperDownloadManager = PiperDownloadManager()

    // Heavy objects: native ONNX runtime, audio thread, ML Kit — lazy until first use
    val sherpaEngine: SherpaEngine by lazy { SherpaEngine(voiceDownloadManager, piperDownloadManager) }

    val audioPipeline: AudioPipeline by lazy { AudioPipeline(cloudTtsEngine, sherpaEngine) }

    val notificationTranslator: NotificationTranslator by lazy { NotificationTranslator() }

    val voiceCommandHandler: VoiceCommandHandler = VoiceCommandHandler()

    val voiceCommandListener: VoiceCommandListener by lazy { VoiceCommandListener(voiceCommandHandler) }
}

/** Extension to retrieve the container from any Context. */
val Context.container: AppContainer
    get() = (applicationContext as KyokanApp).container

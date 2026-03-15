package com.echolibrium.kyokan

import android.content.Context

/**
 * Manual dependency injection container (M29).
 *
 * Holds all previously-singleton instances. Created once in KyokanApp.onCreate()
 * and accessed via KyokanApp.container throughout the app.
 *
 * Stateless utilities (DownloadUtil, SecureKeyStore, CrashLogger) remain as objects.
 */
class AppContainer(private val appContext: Context) {

    val cloudTtsEngine: CloudTtsEngine = CloudTtsEngine()

    val voiceDownloadManager: VoiceDownloadManager = VoiceDownloadManager()

    val piperDownloadManager: PiperDownloadManager = PiperDownloadManager()

    val sherpaEngine: SherpaEngine = SherpaEngine(voiceDownloadManager, piperDownloadManager)

    val audioPipeline: AudioPipeline = AudioPipeline(cloudTtsEngine, sherpaEngine)

    val notificationTranslator: NotificationTranslator = NotificationTranslator()

    val voiceCommandHandler: VoiceCommandHandler = VoiceCommandHandler()

    val voiceCommandListener: VoiceCommandListener = VoiceCommandListener(voiceCommandHandler)
}

/** Extension to retrieve the container from any Context. */
val Context.container: AppContainer
    get() = (applicationContext as KyokanApp).container

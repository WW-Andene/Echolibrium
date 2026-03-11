package com.kokoro.reader

import android.content.Context
import java.io.File

/**
 * Legacy model path manager.
 *
 * Previously handled extracting models from assets to internal storage.
 * Now models are loaded directly from assets via AssetManager in SherpaEngine.
 *
 * This object is kept only for backward compatibility — it provides path helpers
 * that may be referenced elsewhere. No extraction happens anymore.
 */
object VoiceDownloadManager {

    const val MODEL_NAME = "kokoro-multi-lang-v1_0"

    enum class State { NOT_EXTRACTED, EXTRACTING, READY, ERROR }

    @Volatile var state: State = State.READY

    fun getSherpaDir(ctx: Context): File = File(ctx.filesDir, "sherpa").also { it.mkdirs() }
    fun getModelDir(ctx: Context): File = File(getSherpaDir(ctx), MODEL_NAME)

    /** Models are loaded directly from assets now — always considered ready */
    fun isModelReady(ctx: Context): Boolean = true
}

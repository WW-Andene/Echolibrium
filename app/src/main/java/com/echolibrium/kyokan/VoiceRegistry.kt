package com.echolibrium.kyokan

import android.content.Context

/**
 * Unified voice registry abstracting KokoroVoices + PiperVoices.
 *
 * Provides a single API to query all available voices across engines,
 * check download state, and route to correct synthesizer.
 *
 * Separation of concerns:
 *   - VoiceRegistry  — all available voices, download state, capabilities
 *   - VoiceSynthesizer — VoiceIdentity + text + EmotionBlend → raw PCM
 *   - VoiceProcessor — raw PCM + DspChain + UtteranceContext → processed PCM
 *   - AudioPipeline  — queue and play
 */
object VoiceRegistry {

    /** Engine type for routing */
    enum class Engine { KOKORO, PIPER }

    /** Unified voice entry with engine-agnostic metadata */
    data class VoiceEntry(
        val id: String,
        val displayName: String,
        val engine: Engine,
        val gender: String,
        val language: String,
        val nationality: String,
        val sampleRate: Int,
        /** Native emotion token support (Kokoro has style vectors, Piper has scales) */
        val supportsStyleVectors: Boolean = false,
        val supportsGenerativeScales: Boolean = false
    )

    private val kokoroEntries: List<VoiceEntry> = KokoroVoices.ALL.map { v ->
        VoiceEntry(
            id = v.id,
            displayName = v.displayName,
            engine = Engine.KOKORO,
            gender = v.gender,
            language = v.language,
            nationality = v.nationality,
            sampleRate = 24000,
            supportsStyleVectors = true
        )
    }

    private val piperEntries: List<VoiceEntry> = PiperVoices.ALL.map { v ->
        VoiceEntry(
            id = v.id,
            displayName = v.displayName,
            engine = Engine.PIPER,
            gender = v.gender,
            language = v.language,
            nationality = v.nationality,
            sampleRate = 22050,
            supportsGenerativeScales = true
        )
    }

    /** All registered voices across all engines */
    val ALL: List<VoiceEntry> = kokoroEntries + piperEntries

    fun byId(id: String): VoiceEntry? = ALL.find { it.id == id }

    fun engineFor(voiceId: String): Engine? = byId(voiceId)?.engine

    fun isKokoro(voiceId: String): Boolean = engineFor(voiceId) == Engine.KOKORO
    fun isPiper(voiceId: String): Boolean = engineFor(voiceId) == Engine.PIPER

    /** Check if a voice is ready for synthesis (model downloaded) */
    fun isReady(ctx: Context, voiceId: String): Boolean {
        val entry = byId(voiceId) ?: return false
        return when (entry.engine) {
            Engine.KOKORO -> VoiceDownloadManager.isModelReady(ctx)
            Engine.PIPER -> {
                // Piper voices are ready if their model directory exists
                val voiceDir = java.io.File(ctx.filesDir, "sherpa/piper/$voiceId")
                voiceDir.exists() && java.io.File(voiceDir, "model.onnx").exists()
            }
        }
    }

    /** Get native sample rate for a voice */
    fun sampleRate(voiceId: String): Int = byId(voiceId)?.sampleRate ?: 24000

    // ── Filtering ──────────────────────────────────────────────────────────

    fun byEngine(engine: Engine): List<VoiceEntry> = ALL.filter { it.engine == engine }
    fun byGender(gender: String): List<VoiceEntry> =
        if (gender == "All") ALL else ALL.filter { it.gender == gender }
    fun byLanguage(language: String): List<VoiceEntry> =
        if (language == "All") ALL else ALL.filter { it.language == language }

    fun languages(): List<String> = listOf("All") + ALL.map { it.language }.distinct().sorted()
    fun genders(): List<String> = listOf("All", "Female", "Male")
    fun engines(): List<String> = listOf("All", "Kokoro", "Piper")
}

/**
 * Stateless voice synthesizer — routes to correct engine based on VoiceRegistry.
 *
 * VoiceIdentity + text → raw PCM (no DSP applied).
 */
object VoiceSynthesizer {

    data class SynthResult(
        val pcm: FloatArray,
        val sampleRate: Int
    )

    /**
     * Synthesize text using the specified voice identity.
     * Routes to Kokoro or Piper engine automatically.
     *
     * @param ctx Android context for model loading
     * @param identity Voice identity from VoiceProfile
     * @param text Text to synthesize (already transformed)
     * @param modulated Modulation parameters for pre-synthesis control
     * @param signal Signal context for style sculpting
     * @return Raw PCM or null if synthesis fails
     */
    fun synthesize(
        ctx: Context,
        identity: VoiceIdentity,
        text: String,
        modulated: ModulatedVoice,
        signal: SignalMap
    ): SynthResult? {
        val engine = VoiceRegistry.engineFor(identity.voiceName) ?: return null
        return when (engine) {
            VoiceRegistry.Engine.KOKORO -> synthesizeKokoro(ctx, identity, text, modulated)
            VoiceRegistry.Engine.PIPER -> synthesizePiper(ctx, identity, text, modulated)
        }
    }

    private fun synthesizeKokoro(
        ctx: Context, identity: VoiceIdentity, text: String, modulated: ModulatedVoice
    ): SynthResult? {
        if (!SherpaEngine.initialize(ctx)) return null
        val voice = KokoroVoices.byId(identity.voiceName) ?: KokoroVoices.default()
        val result = SherpaEngine.synthesize(
            text = text, sid = voice.sid, speed = modulated.speed
        ) ?: return null
        return SynthResult(result.first, result.second)
    }

    private fun synthesizePiper(
        ctx: Context, identity: VoiceIdentity, text: String, modulated: ModulatedVoice
    ): SynthResult? {
        if (!SherpaEngine.initPiper(ctx, identity.voiceName)) return null
        val result = SherpaEngine.synthesizePiper(
            voiceId = identity.voiceName, text = text, speed = modulated.speed
        ) ?: return null
        return SynthResult(result.first, result.second)
    }
}

/**
 * Voice processor — applies DSP chain to raw PCM.
 *
 * Separates synthesis from post-processing, enabling:
 *   - DSP chain testing with synthetic PCM
 *   - Engine swaps without touching the processing chain
 *   - Per-profile DSP chain configuration
 */
object VoiceProcessor {

    /**
     * Process raw PCM through a DSP chain.
     *
     * @param pcm Raw PCM from synthesizer
     * @param chain DspChain configured for this voice/profile
     * @param ctx Utterance context carrying landmarks, modulation, signal
     * @return Processed PCM ready for playback
     */
    fun process(
        pcm: FloatArray,
        chain: DspChain,
        ctx: UtteranceContext
    ): FloatArray {
        if (pcm.isEmpty()) return pcm
        return chain.process(pcm, ctx)
    }
}

package com.echolibrium.kyokan

import android.util.Log

/**
 * YatagamiSynthesizer — Unified pipeline with pre-synthesis emotional control.
 *
 * Replaces the direct SherpaEngine.synthesize(text, sid, speed) call in AudioPipeline
 * with a richer path that sculpts style vectors (Kokoro) or maps scales (Piper)
 * before synthesis, so the model GENERATES emotional audio rather than having
 * effects painted on after.
 *
 * Fallback cascade:
 *   1. Kokoro + StyleSculptor (direct ORT, full emotional control)
 *   2. Piper + ScaleMapper (direct ORT, generative parameter control)
 *   3. null → signals AudioPipeline to use existing SherpaEngine path
 *
 * INTEGRATION POINT: AudioPipeline.processItem(), lines 112-118.
 * Currently:
 *   val result = if (PiperVoices.isPiperVoice(voiceId)) {
 *       synthesizeWithPiper(ctx, voiceId, processed, item.modulated.speed)
 *   } else {
 *       synthesizeWithKokoro(ctx, voiceId, processed, item.modulated.speed)
 *   }
 *
 * With Yatagami:
 *   val yatagamiResult = yatagamiSynthesizer?.synthesize(tokenIds, item.modulated, item.signal, item.profile)
 *   val result = if (yatagamiResult != null) {
 *       Pair(yatagamiResult.pcm, yatagamiResult.sampleRate)
 *   } else {
 *       // existing SherpaEngine path (unchanged fallback)
 *       ...
 *   }
 *
 * THREADING: Runs on AudioPipeline's single synthesis thread.
 * All methods are synchronous and non-reentrant (same as SherpaEngine).
 *
 * Adapted to use:
 *   - SherpaEngine as object singleton (not class instance)
 *   - KokoroVoices for voice index resolution
 *   - Actual ModulatedVoice field names
 */
class YatagamiSynthesizer(
    private val directOrt: DirectOrtEngine,
) {

    companion object {
        private const val TAG = "YatagamiSynthesizer"
    }

    // ─── Configuration ──────────────────────────────────────────────────

    /** Whether to try direct ORT before falling back to sherpa */
    var directOrtEnabled: Boolean = true

    // Pre-loaded secondary palette for emotional voice blending
    private var secondaryPalette: StyleSculptor.VoicePalette? = null
    private var secondaryVoiceName: String? = null

    // ─── Main Synthesis Entry Point ─────────────────────────────────────

    /**
     * Synthesize speech with full emotional control.
     *
     * @param tokenIds Phoneme/token IDs (from tokenizer — Phase 4 of integration)
     * @param modulated Emotional modulation from VoiceModulator
     * @param signal Signal map from SignalExtractor
     * @param profile Active voice profile (uses voiceName to resolve Kokoro sid)
     * @return SynthResult with PCM data and metadata, or null to fall back to SherpaEngine
     */
    fun synthesize(
        tokenIds: LongArray,
        modulated: ModulatedVoice,
        signal: SignalMap,
        profile: VoiceProfile,
    ): SynthResult? {

        // Path 1: Kokoro with style sculpting
        if (directOrtEnabled && directOrt.isKokoroReady && !PiperVoices.isPiperVoice(profile.voiceName)) {
            val result = synthesizeKokoroSculpted(tokenIds, modulated, signal, profile)
            if (result != null) return result
            Log.w(TAG, "Kokoro sculpted path failed, trying next")
        }

        // Path 2: Piper with scale mapping
        if (directOrtEnabled && directOrt.isPiperReady && PiperVoices.isPiperVoice(profile.voiceName)) {
            val result = synthesizePiperMapped(tokenIds, modulated, signal)
            if (result != null) return result
            Log.w(TAG, "Piper mapped path failed, trying next")
        }

        // Path 3: Return null — AudioPipeline will use existing SherpaEngine path
        Log.d(TAG, "Falling back to SherpaEngine (speed-only control)")
        return null
    }

    // ─── Kokoro Path ────────────────────────────────────────────────────

    private fun synthesizeKokoroSculpted(
        tokenIds: LongArray,
        modulated: ModulatedVoice,
        signal: SignalMap,
        profile: VoiceProfile,
    ): SynthResult? {
        return try {
            // Resolve voice index from KokoroVoices catalog
            val voiceIndex = directOrt.resolveVoiceIndex(profile.voiceName)

            // 1. Get base style vector for current voice + utterance length
            val baseStyle = directOrt.getKokoroStyle(voiceIndex, tokenIds.size)
                ?: return null

            // 2. Sculpt the style vector based on emotional context
            val sculptedStyle = StyleSculptor.sculpt(
                baseStyle = baseStyle,
                modulated = modulated,
                signal = signal,
                secondaryPalette = secondaryPalette,
                numTokens = tokenIds.size,
            )

            // 3. Run inference with the sculpted style
            val (pcm, sampleRate) = directOrt.synthesizeKokoro(
                inputIds = tokenIds,
                sculptedStyle = sculptedStyle,
                speed = modulated.speed,
            ) ?: return null

            SynthResult(
                pcm = pcm,
                sampleRate = sampleRate,
                engine = EngineType.KOKORO_SCULPTED,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Kokoro sculpted synthesis error: ${e.message}", e)
            null
        }
    }

    // ─── Piper Path ─────────────────────────────────────────────────────

    private fun synthesizePiperMapped(
        tokenIds: LongArray,
        modulated: ModulatedVoice,
        signal: SignalMap,
    ): SynthResult? {
        return try {
            // 1. Map emotional parameters to Piper's generative scales
            val scales = ScaleMapper.mapScales(modulated, signal)

            // 2. Run inference with mapped scales
            // NOTE: Piper uses different phoneme IDs than Kokoro.
            // In practice, you need two tokenization paths — one per engine.
            val (pcm, sampleRate) = directOrt.synthesizePiper(
                phonemeIds = tokenIds,
                scales = scales,
                speakerId = -1,
            ) ?: return null

            SynthResult(
                pcm = pcm,
                sampleRate = sampleRate,
                engine = EngineType.PIPER_MAPPED,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Piper mapped synthesis error: ${e.message}", e)
            null
        }
    }

    // ─── Voice Management ───────────────────────────────────────────────

    /**
     * Set up the secondary voice palette for emotional blending.
     *
     * Example: primary voice = af_bella (warm, clear)
     *          secondary = af_sky (warm, breathy)
     * → emotional intensity will blend toward breathier delivery at synthesis level.
     *
     * @param voiceName KokoroVoice.id (e.g. "af_sky")
     */
    fun setSecondaryVoice(voiceName: String) {
        val voiceIndex = directOrt.resolveVoiceIndex(voiceName)
        val allData = directOrt.getAllVoicesData() ?: return
        secondaryPalette = StyleSculptor.loadPalette(
            name = voiceName,
            allVoicesData = allData,
            voiceIndex = voiceIndex,
            numTokenLengths = directOrt.getTokensPerVoice(),
        )
        secondaryVoiceName = voiceName
        Log.i(TAG, "Secondary voice set to $voiceName (sid=$voiceIndex) for emotional blending")
    }

    // ─── Data Types ─────────────────────────────────────────────────────

    data class SynthResult(
        val pcm: FloatArray,
        val sampleRate: Int,
        val engine: EngineType,
    )

    enum class EngineType {
        KOKORO_SCULPTED,  // Direct ORT + StyleSculptor
        PIPER_MAPPED,     // Direct ORT + ScaleMapper
        SHERPA_KOKORO,    // Existing SherpaEngine.synthesize()
        SHERPA_PIPER,     // Existing SherpaEngine.synthesizePiper()
    }
}

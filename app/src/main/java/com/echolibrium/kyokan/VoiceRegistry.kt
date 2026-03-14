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
    enum class Engine { KOKORO, PIPER, CLOUD }

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

    // ── Cloud TTS voices (DeepInfra: Orpheus, Chatterbox, Qwen3-TTS) ────────

    /** Cloud engine sub-type stored in the voice ID prefix */
    enum class CloudEngine { ORPHEUS, CHATTERBOX, QWEN3_TTS }

    data class CloudVoice(
        val id: String,
        val displayName: String,
        val cloudEngine: CloudEngine,
        val gender: String,
        val language: String,
        val nationality: String,
        val apiVoiceName: String   // name sent to DeepInfra API
    ) {
        val genderIcon get() = if (gender == "Female") "♀" else "♂"
        val genderColor get() = if (gender == "Female") 0xFFff88cc.toInt() else 0xFF88ccff.toInt()
    }

    val CLOUD_VOICES = listOf(
        // Orpheus voices (English only, high fidelity + emotion tags)
        CloudVoice("cloud_orpheus_tara",  "Tara",  CloudEngine.ORPHEUS, "Female", "English", "American", "tara"),
        CloudVoice("cloud_orpheus_leah",  "Leah",  CloudEngine.ORPHEUS, "Female", "English", "American", "leah"),
        CloudVoice("cloud_orpheus_jess",  "Jess",  CloudEngine.ORPHEUS, "Female", "English", "American", "jess"),
        CloudVoice("cloud_orpheus_mia",   "Mia",   CloudEngine.ORPHEUS, "Female", "English", "American", "mia"),
        CloudVoice("cloud_orpheus_zoe",   "Zoe",   CloudEngine.ORPHEUS, "Female", "English", "American", "zoe"),
        CloudVoice("cloud_orpheus_leo",   "Leo",   CloudEngine.ORPHEUS, "Male",   "English", "American", "leo"),
        CloudVoice("cloud_orpheus_dan",   "Dan",   CloudEngine.ORPHEUS, "Male",   "English", "American", "dan"),
        CloudVoice("cloud_orpheus_zac",   "Zac",   CloudEngine.ORPHEUS, "Male",   "English", "American", "zac"),
        // Chatterbox (multilingual, default voice)
        CloudVoice("cloud_chatterbox_default", "Chatterbox", CloudEngine.CHATTERBOX, "Female", "Multilingual", "Cloud", "default"),
        // Qwen3-TTS voices (multilingual, voice instructions)
        CloudVoice("cloud_qwen3_vivian",  "Vivian",  CloudEngine.QWEN3_TTS, "Female", "Multilingual", "Cloud", "Vivian"),
        CloudVoice("cloud_qwen3_serena",  "Serena",  CloudEngine.QWEN3_TTS, "Female", "Multilingual", "Cloud", "Serena"),
        CloudVoice("cloud_qwen3_dylan",   "Dylan",   CloudEngine.QWEN3_TTS, "Male",   "Multilingual", "Cloud", "Dylan"),
        CloudVoice("cloud_qwen3_eric",    "Eric",    CloudEngine.QWEN3_TTS, "Male",   "Multilingual", "Cloud", "Eric"),
        CloudVoice("cloud_qwen3_ryan",    "Ryan",    CloudEngine.QWEN3_TTS, "Male",   "Multilingual", "Cloud", "Ryan"),
        CloudVoice("cloud_qwen3_aiden",   "Aiden",   CloudEngine.QWEN3_TTS, "Male",   "Multilingual", "Cloud", "Aiden"),
    )

    private val cloudEntries: List<VoiceEntry> = CLOUD_VOICES.map { v ->
        VoiceEntry(
            id = v.id,
            displayName = v.displayName,
            engine = Engine.CLOUD,
            gender = v.gender,
            language = v.language,
            nationality = v.nationality,
            sampleRate = 24000
        )
    }

    fun cloudVoiceById(id: String): CloudVoice? = CLOUD_VOICES.find { it.id == id }
    fun isCloud(voiceId: String): Boolean = engineFor(voiceId) == Engine.CLOUD

    /** All registered voices across all engines */
    val ALL: List<VoiceEntry> = kokoroEntries + piperEntries + cloudEntries

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
            Engine.CLOUD -> CloudTtsEngine.isEnabled()
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
    fun engines(): List<String> = listOf("All", "Kokoro", "Piper", "Cloud")

    // ── User-facing voices (Cloud only — Kokoro/Piper are invisible fallbacks) ──

    /** Voices shown to users in the voice picker. Only cloud voices are user-selectable. */
    fun userFacingVoices(): List<VoiceEntry> = cloudEntries

    /** User-facing engine filter options (no Kokoro/Piper exposed) */
    fun userFacingEngines(): List<String> = listOf("All", "Orpheus", "Chatterbox", "Qwen3-TTS")

    /** User-facing languages from cloud voices only */
    fun userFacingLanguages(): List<String> = listOf("All") + cloudEntries.map { it.language }.distinct().sorted()

    /** Check if a voice is a local offline fallback (not user-selectable) */
    fun isOfflineFallback(voiceId: String): Boolean {
        val engine = engineFor(voiceId) ?: return false
        return engine == Engine.KOKORO || engine == Engine.PIPER
    }
}


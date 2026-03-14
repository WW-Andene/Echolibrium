package com.echolibrium.kyokan

/**
 * Piper TTS voice definition and catalog.
 *
 * Each Piper voice is a single VITS model (.onnx) that also requires
 * shared assets: tokens.txt + espeak-ng-data/ for phonemization.
 *
 * Voices with bundles (vits-piper-*.tar.bz2) are downloaded as a single archive.
 * Voices without bundles are assembled from individual .onnx + shared assets.
 *
 * Storage: filesDir/sherpa/piper/{voiceId}/{voiceId}.onnx, tokens.txt, espeak-ng-data/
 */
data class PiperVoice(
    val id: String,           // e.g. "en_US-lessac-medium"
    val name: String,         // e.g. "lessac"
    val displayName: String,  // e.g. "Lessac"
    val gender: String,       // Female | Male | Unknown
    val language: String,     // e.g. "English (US)"
    val nationality: String,  // e.g. "American"
    val locale: String,       // e.g. "en_US"
    val quality: String,      // low | medium | high
    val langCode: String      // e.g. "en" — ISO 639-1 for HuggingFace path
) {
    val genderIcon get() = when (gender) {
        "Female" -> "♀"
        "Male"   -> "♂"
        else     -> "◆"
    }
    val genderColor get() = when (gender) {
        "Female" -> 0xFFff88cc.toInt()
        "Male"   -> 0xFF88ccff.toInt()
        else     -> 0xFFaaaaaa.toInt()
    }
    val flagEmoji get() = when (locale) {
        "en_US" -> "🇺🇸"
        "en_GB" -> "🇬🇧"
        "fr_FR" -> "🇫🇷"
        else    -> "🌐"
    }

    /** Size estimate in MB for UI display */
    val sizeMb get() = when (quality) {
        "low"    -> 16
        "medium" -> 40
        "high"   -> 80
        else     -> 40
    }
}

object PiperVoices {

    private const val RELEASE_BASE = "https://github.com/WW-Andene/Echolibrium/releases/download/tts-assets-v1"
    // Centralized HuggingFace URL (C3: avoid duplication across methods)
    private const val HUGGINGFACE_BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0"

    private fun piper(
        name: String, gender: String, language: String, nationality: String,
        locale: String, quality: String
    ): PiperVoice {
        val id = "${locale}-${name}-${quality}"
        val langCode = locale.substringBefore("_").lowercase()
        return PiperVoice(
            id = id,
            name = name,
            displayName = name.split("_").joinToString(" ") {
                it.replaceFirstChar { c -> c.uppercase() }
            },
            gender = gender, language = language, nationality = nationality,
            locale = locale, quality = quality, langCode = langCode
        )
    }

    // ── English (US) ─────────────────────────────────────────────────────────
    private val EN_US = listOf(
        piper("lessac",     "Female",  "English (US)", "American", "en_US", "medium"),
        piper("ljspeech",   "Female",  "English (US)", "American", "en_US", "medium"),
        piper("kristin",    "Female",  "English (US)", "American", "en_US", "medium"),
        piper("amy",        "Female",  "English (US)", "American", "en_US", "low"),
        piper("kathleen",   "Female",  "English (US)", "American", "en_US", "low"),
        piper("hfc_female", "Female",  "English (US)", "American", "en_US", "medium"),
        piper("ryan",       "Male",    "English (US)", "American", "en_US", "medium"),
        piper("joe",        "Male",    "English (US)", "American", "en_US", "medium"),
        piper("bryce",      "Male",    "English (US)", "American", "en_US", "medium"),
        piper("danny",      "Male",    "English (US)", "American", "en_US", "low"),
        piper("john",       "Male",    "English (US)", "American", "en_US", "medium"),
        piper("norman",     "Male",    "English (US)", "American", "en_US", "medium"),
        piper("hfc_male",   "Male",    "English (US)", "American", "en_US", "medium"),
        piper("kusal",      "Male",    "English (US)", "American", "en_US", "medium"),
        piper("arctic",     "Unknown", "English (US)", "American", "en_US", "medium"),
        piper("l2arctic",   "Unknown", "English (US)", "American", "en_US", "medium"),
        piper("libritts",   "Unknown", "English (US)", "American", "en_US", "high"),
        piper("libritts_r", "Unknown", "English (US)", "American", "en_US", "medium"),
    )

    // ── English (UK) ─────────────────────────────────────────────────────────
    private val EN_GB = listOf(
        piper("alba",                    "Female", "English (UK)", "British", "en_GB", "medium"),
        piper("cori",                    "Female", "English (UK)", "British", "en_GB", "medium"),
        piper("jenny_dioco",             "Female", "English (UK)", "British", "en_GB", "medium"),
        piper("southern_english_female", "Female", "English (UK)", "British", "en_GB", "low"),
        piper("southern_english_female", "Female", "English (UK)", "British", "en_GB", "medium"),
        piper("alan",                    "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("northern_english_male",   "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("aru",                     "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("semaine",                 "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("vctk",                    "Unknown","English (UK)", "British", "en_GB", "medium"),
    )

    // ── French (FR) ──────────────────────────────────────────────────────────
    private val FR_FR = listOf(
        piper("siwis",  "Female", "French", "French", "fr_FR", "medium"),
        piper("siwis",  "Female", "French", "French", "fr_FR", "low"),
        piper("tom",    "Male",   "French", "French", "fr_FR", "medium"),
        piper("gilles", "Male",   "French", "French", "fr_FR", "low"),
        piper("upmc",   "Male",   "French", "French", "fr_FR", "medium"),
    )

    val ALL: List<PiperVoice> = EN_US + EN_GB + FR_FR

    fun byId(id: String): PiperVoice? = ALL.find { it.id == id }
    fun default(): PiperVoice = ALL.first()

    fun byGender(g: String) = if (g == "All") ALL else ALL.filter { it.gender == g }
    fun byLanguage(l: String) = if (l == "All") ALL else ALL.filter { it.language == l }
    fun languages() = listOf("All") + ALL.map { it.language }.distinct().sorted()
    fun genders() = listOf("All", "Female", "Male")

    /** True if this voice ID belongs to a Piper voice (not Kokoro) */
    fun isPiperVoice(voiceId: String): Boolean = byId(voiceId) != null

    // ── Voices that have pre-built vits-piper bundles ────────────────────────
    private val BUNDLED_VOICES = setOf(
        "en_GB-alan-medium", "en_GB-alba-medium", "en_GB-aru-medium",
        "en_GB-cori-medium", "en_GB-jenny_dioco-medium",
        "en_GB-northern_english_male-medium", "en_GB-semaine-medium",
        "en_GB-southern_english_female-low", "en_GB-southern_english_female-medium",
    )

    fun hasBundledArchive(voiceId: String): Boolean = voiceId in BUNDLED_VOICES

    /** Download URL for a bundled vits-piper tar.bz2 archive */
    fun bundleUrl(voiceId: String): String =
        "$RELEASE_BASE/vits-piper-$voiceId.tar.bz2"

    /**
     * Download URL for a raw .onnx model from rhasspy/piper on HuggingFace.
     * Path: {langCode}/{locale}/{name}/{quality}/{id}.onnx
     */
    fun onnxUrl(voice: PiperVoice): String =
        "$HUGGINGFACE_BASE/${voice.langCode}/${voice.locale}/${voice.name}/${voice.quality}/${voice.id}.onnx"

    /**
     * Download URL for the voice's own .onnx.json config (contains tokens, phoneme map, etc.)
     */
    fun onnxJsonUrl(voice: PiperVoice): String =
        "$HUGGINGFACE_BASE/${voice.langCode}/${voice.locale}/${voice.name}/${voice.quality}/${voice.id}.onnx.json"

    /** Download URL for shared espeak-ng-data archive from our release */
    fun espeakDataUrl(): String = "$RELEASE_BASE/espeak-ng-data.tar.bz2"

    /** The directory name inside the tar.bz2 archive */
    fun archiveDirName(voiceId: String): String = "vits-piper-$voiceId"
}

package com.kokoro.reader

/**
 * Catalog of all Piper TTS voices.
 *
 * Voices are split into two tiers:
 *   • BUNDLED — shipped inside the APK (assets/piper-models/), ready instantly
 *   • DOWNLOADABLE — fetched on demand from GitHub Releases to app internal storage
 *
 * Voice data sourced from:
 *   https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/VOICES.md
 */
data class PiperVoice(
    val id: String,           // e.g. "en_US-lessac-medium"
    val name: String,         // e.g. "lessac"
    val displayName: String,  // e.g. "Lessac"
    val gender: String,       // Female | Male | Unknown
    val language: String,     // e.g. "English (US)" | "French"
    val nationality: String,  // e.g. "American" | "French"
    val locale: String,       // e.g. "en_US" | "fr_FR"
    val quality: String,      // e.g. "medium" | "low" | "high"
    val bundled: Boolean      // true = in APK assets, false = download from GitHub
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
}

object PiperVoiceCatalog {

    /**
     * GitHub Releases base URL for downloadable voice models.
     * Each voice is a single .onnx file uploaded as a release asset.
     * Tag: "tts-assets-v1" — all TTS assets live in one release.
     */
    const val GITHUB_RELEASE_BASE = "https://github.com/WW-Andene/Echolibrium/releases/download/tts-assets-v1"

    /** IDs of voices bundled in the APK. Keep in sync with download-models.sh BUNDLED_VOICES. */
    private val BUNDLED_IDS = setOf(
        "en_US-lessac-medium",
        "en_US-ryan-medium",
        "en_US-amy-medium",
        "en_US-joe-medium",
        "en_GB-alba-medium",
        "en_GB-alan-medium",
        "fr_FR-siwis-medium",
        "fr_FR-tom-medium"
    )

    private fun piper(
        name: String, gender: String, language: String, nationality: String,
        locale: String, quality: String
    ): PiperVoice {
        val id = "$locale-$name-$quality"
        return PiperVoice(
            id = id,
            name = name,
            displayName = name.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
            gender = gender, language = language, nationality = nationality,
            locale = locale, quality = quality,
            bundled = id in BUNDLED_IDS
        )
    }

    // ── English (US) ──────────────────────────────────────────────────────────
    val EN_US: List<PiperVoice> = listOf(
        piper("amy",          "Female",  "English (US)", "American", "en_US", "low"),
        piper("amy",          "Female",  "English (US)", "American", "en_US", "medium"),
        piper("arctic",       "Unknown", "English (US)", "American", "en_US", "medium"),
        piper("bryce",        "Male",    "English (US)", "American", "en_US", "medium"),
        piper("danny",        "Male",    "English (US)", "American", "en_US", "low"),
        piper("hfc_female",   "Female",  "English (US)", "American", "en_US", "medium"),
        piper("hfc_male",     "Male",    "English (US)", "American", "en_US", "medium"),
        piper("joe",          "Male",    "English (US)", "American", "en_US", "medium"),
        piper("john",         "Male",    "English (US)", "American", "en_US", "medium"),
        piper("kathleen",     "Female",  "English (US)", "American", "en_US", "low"),
        piper("kristin",      "Female",  "English (US)", "American", "en_US", "medium"),
        piper("kusal",        "Male",    "English (US)", "American", "en_US", "medium"),
        piper("l2arctic",     "Unknown", "English (US)", "American", "en_US", "medium"),
        piper("lessac",       "Female",  "English (US)", "American", "en_US", "low"),
        piper("lessac",       "Female",  "English (US)", "American", "en_US", "medium"),
        piper("lessac",       "Female",  "English (US)", "American", "en_US", "high"),
        piper("libritts",     "Unknown", "English (US)", "American", "en_US", "high"),
        piper("libritts_r",   "Unknown", "English (US)", "American", "en_US", "medium"),
        piper("ljspeech",     "Female",  "English (US)", "American", "en_US", "medium"),
        piper("ljspeech",     "Female",  "English (US)", "American", "en_US", "high"),
        piper("norman",       "Male",    "English (US)", "American", "en_US", "medium"),
        piper("reza_ibrahim", "Male",    "English (US)", "American", "en_US", "medium"),
        piper("ryan",         "Male",    "English (US)", "American", "en_US", "low"),
        piper("ryan",         "Male",    "English (US)", "American", "en_US", "medium"),
        piper("ryan",         "Male",    "English (US)", "American", "en_US", "high"),
        piper("sam",          "Male",    "English (US)", "American", "en_US", "medium"),
    )

    // ── English (GB) ──────────────────────────────────────────────────────────
    val EN_GB: List<PiperVoice> = listOf(
        piper("alan",                    "Male",    "English (UK)", "British", "en_GB", "low"),
        piper("alan",                    "Male",    "English (UK)", "British", "en_GB", "medium"),
        piper("alba",                    "Female",  "English (UK)", "British", "en_GB", "medium"),
        piper("aru",                     "Male",    "English (UK)", "British", "en_GB", "medium"),
        piper("cori",                    "Female",  "English (UK)", "British", "en_GB", "medium"),
        piper("cori",                    "Female",  "English (UK)", "British", "en_GB", "high"),
        piper("jenny_dioco",             "Female",  "English (UK)", "British", "en_GB", "medium"),
        piper("northern_english_male",   "Male",    "English (UK)", "British", "en_GB", "medium"),
        piper("semaine",                 "Unknown", "English (UK)", "British", "en_GB", "medium"),
        piper("southern_english_female", "Female",  "English (UK)", "British", "en_GB", "low"),
        piper("vctk",                    "Unknown", "English (UK)", "British", "en_GB", "medium"),
    )

    // ── French (France) ───────────────────────────────────────────────────────
    val FR_FR: List<PiperVoice> = listOf(
        piper("gilles",   "Male",    "French", "French", "fr_FR", "low"),
        piper("mls",      "Unknown", "French", "French", "fr_FR", "medium"),
        piper("mls_1840", "Unknown", "French", "French", "fr_FR", "low"),
        piper("siwis",    "Female",  "French", "French", "fr_FR", "low"),
        piper("siwis",    "Female",  "French", "French", "fr_FR", "medium"),
        piper("tom",      "Male",    "French", "French", "fr_FR", "medium"),
        piper("upmc",     "Unknown", "French", "French", "fr_FR", "medium"),
    )

    /** All Piper voices (en_US + en_GB + fr_FR) */
    val ALL: List<PiperVoice> = EN_US + EN_GB + FR_FR

    /** Unique language labels across all Piper voices */
    fun languages() = ALL.map { it.language }.distinct().sorted()

    /** All genders present in the catalog */
    fun genders() = listOf("All", "Female", "Male")

    /** Filter by gender */
    fun byGender(g: String) = if (g == "All") ALL else ALL.filter { it.gender == g }

    /** Filter by language label */
    fun byLanguage(l: String) = if (l == "All") ALL else ALL.filter { it.language == l }

    /** Find a Piper voice by its full ID */
    fun byId(id: String): PiperVoice? = ALL.find { it.id == id }

    /** Download URL for a voice model (.onnx) from GitHub Releases */
    fun downloadUrl(voiceId: String): String = "$GITHUB_RELEASE_BASE/$voiceId.onnx"
}

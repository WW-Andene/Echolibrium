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
    val quality: String       // low | medium | high
) {
    val genderIcon get() = when (gender) {
        "Female" -> "♀"
        "Male"   -> "♂"
        else     -> "◆"
    }
    @Deprecated("Unused — views use AppColors.genderColor() instead")
    val genderColor get() = when (gender) {
        "Female" -> 0xFFd4a0b8.toInt()
        "Male"   -> 0xFF88aad4.toInt()
        else     -> 0xFFb0a4c0.toInt()
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

    private fun piper(
        name: String, gender: String, language: String, nationality: String,
        locale: String, quality: String
    ): PiperVoice {
        val id = "${locale}-${name}-${quality}"
        return PiperVoice(
            id = id,
            name = name,
            displayName = name.split("_").joinToString(" ") {
                it.replaceFirstChar { c -> c.uppercase() }
            },
            gender = gender, language = language, nationality = nationality,
            locale = locale, quality = quality
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

    // All catalog voices have bundles on our GitHub release — always use bundled download
    fun hasBundledArchive(voiceId: String): Boolean = byId(voiceId) != null

    /** Download URL for a bundled vits-piper tar.bz2 archive from our release */
    fun bundleUrl(voiceId: String): String =
        "$RELEASE_BASE/vits-piper-$voiceId.tar.bz2"

    /** The directory name inside the tar.bz2 archive */
    fun archiveDirName(voiceId: String): String = "vits-piper-$voiceId"
}

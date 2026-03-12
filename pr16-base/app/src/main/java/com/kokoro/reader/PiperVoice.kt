package com.kokoro.reader

/**
 * Piper TTS voice definition and catalog.
 *
 * Each Piper voice is a single VITS model (.onnx) that also requires
 * shared assets: tokens.txt + espeak-ng-data/ for phonemization.
 *
 * Voice .onnx files are downloaded on-demand from the repo's tts-assets-v1
 * GitHub release. Shared assets (tokens + espeak-ng-data) are downloaded
 * once and reused across all voices.
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

    private const val REPO = "WW-Andene/Echolibrium"
    private const val RELEASE_TAG = "tts-assets-v1"
    private const val BASE_URL = "https://github.com/$REPO/releases/download/$RELEASE_TAG"

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

    // ── English (US) ─────────────────────────────────────────────────────
    private val EN_US = listOf(
        piper("amy",            "Female", "English (US)", "American", "en_US", "low"),
        piper("amy",            "Female", "English (US)", "American", "en_US", "medium"),
        piper("arctic",         "Male",   "English (US)", "American", "en_US", "medium"),
        piper("bryce",          "Male",   "English (US)", "American", "en_US", "medium"),
        piper("danny",          "Male",   "English (US)", "American", "en_US", "low"),
        piper("hfc_female",     "Female", "English (US)", "American", "en_US", "medium"),
        piper("hfc_male",       "Male",   "English (US)", "American", "en_US", "medium"),
        piper("joe",            "Male",   "English (US)", "American", "en_US", "medium"),
        piper("john",           "Male",   "English (US)", "American", "en_US", "medium"),
        piper("kathleen",       "Female", "English (US)", "American", "en_US", "low"),
        piper("kristin",        "Female", "English (US)", "American", "en_US", "medium"),
        piper("kusal",          "Male",   "English (US)", "American", "en_US", "medium"),
        piper("l2arctic",       "Male",   "English (US)", "American", "en_US", "medium"),
        piper("lessac",         "Female", "English (US)", "American", "en_US", "low"),
        piper("lessac",         "Female", "English (US)", "American", "en_US", "medium"),
        piper("lessac",         "Female", "English (US)", "American", "en_US", "high"),
        piper("libritts",       "Male",   "English (US)", "American", "en_US", "high"),
        piper("libritts_r",     "Male",   "English (US)", "American", "en_US", "medium"),
        piper("ljspeech",       "Female", "English (US)", "American", "en_US", "medium"),
        piper("ljspeech",       "Female", "English (US)", "American", "en_US", "high"),
        piper("norman",         "Male",   "English (US)", "American", "en_US", "medium"),
        piper("reza_ibrahim",   "Male",   "English (US)", "American", "en_US", "medium"),
        piper("ryan",           "Male",   "English (US)", "American", "en_US", "low"),
        piper("ryan",           "Male",   "English (US)", "American", "en_US", "medium"),
        piper("ryan",           "Male",   "English (US)", "American", "en_US", "high"),
        piper("sam",            "Male",   "English (US)", "American", "en_US", "medium"),
    )

    // ── English (UK) ─────────────────────────────────────────────────────
    private val EN_GB = listOf(
        piper("alan",                     "Male",   "English (UK)", "British", "en_GB", "low"),
        piper("alan",                     "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("alba",                     "Female", "English (UK)", "British", "en_GB", "medium"),
        piper("aru",                      "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("cori",                     "Female", "English (UK)", "British", "en_GB", "medium"),
        piper("cori",                     "Female", "English (UK)", "British", "en_GB", "high"),
        piper("jenny_dioco",              "Female", "English (UK)", "British", "en_GB", "medium"),
        piper("northern_english_male",    "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("semaine",                  "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("southern_english_female",  "Female", "English (UK)", "British", "en_GB", "low"),
        piper("vctk",                     "Female", "English (UK)", "British", "en_GB", "medium"),
    )

    // ── French ───────────────────────────────────────────────────────────
    private val FR_FR = listOf(
        piper("gilles",   "Male",   "French", "French", "fr_FR", "low"),
        piper("mls",      "Male",   "French", "French", "fr_FR", "medium"),
        piper("mls_1840", "Male",   "French", "French", "fr_FR", "low"),
        piper("siwis",    "Female", "French", "French", "fr_FR", "low"),
        piper("siwis",    "Female", "French", "French", "fr_FR", "medium"),
        piper("tom",      "Male",   "French", "French", "fr_FR", "medium"),
        piper("upmc",     "Male",   "French", "French", "fr_FR", "medium"),
    )

    val ALL: List<PiperVoice> = EN_US + EN_GB + FR_FR

    fun byId(id: String): PiperVoice? = ALL.find { it.id == id }
    fun default(): PiperVoice = ALL.first { it.id == "en_US-lessac-medium" }

    fun byGender(g: String) = if (g == "All") ALL else ALL.filter { it.gender == g }
    fun byLanguage(l: String) = if (l == "All") ALL else ALL.filter { it.language == l }
    fun languages() = listOf("All") + ALL.map { it.language }.distinct().sorted()
    fun genders() = listOf("All", "Female", "Male")

    /** True if this voice ID belongs to a Piper voice (not Kokoro) */
    fun isPiperVoice(voiceId: String): Boolean = byId(voiceId) != null

    /** Download URL for a voice .onnx from the repo's tts-assets-v1 release */
    fun downloadUrl(voiceId: String): String = "$BASE_URL/$voiceId.onnx"

    /** Download URL for the shared tokens.txt */
    fun tokensUrl(): String = "$BASE_URL/piper-tokens.txt"

    /** Download URL for the shared espeak-ng-data archive (k2-fsa direct) */
    fun espeakUrl(): String =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"
}

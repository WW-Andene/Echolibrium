package com.kokoro.reader

/**
 * Catalog of Piper TTS voices available for download.
 *
 * Voice data sourced from:
 *   https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/VOICES.md
 *
 * Each voice requires two files (.onnx + .onnx.json) downloaded from HuggingFace.
 * The catalog is stored in assets/piper_voices/{locale}/voices.json and also
 * defined here as a compile-time constant for UI display without asset loading.
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
    val modelUrl: String,     // HuggingFace direct download URL for .onnx
    val configUrl: String     // HuggingFace direct download URL for .onnx.json
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
        "fr_FR" -> "🇫🇷"
        else    -> "🌐"
    }
}

object PiperVoiceCatalog {

    private const val HF_BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0"

    private fun piper(
        name: String, gender: String, language: String, nationality: String,
        locale: String, quality: String
    ): PiperVoice {
        val id = "$locale-$name-$quality"
        val dir = "$HF_BASE/${locale.substringBefore("_").lowercase()}/$locale/$name/$quality"
        return PiperVoice(
            id = id,
            name = name,
            displayName = name.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
            gender = gender, language = language, nationality = nationality,
            locale = locale, quality = quality,
            modelUrl  = "$dir/$id.onnx?download=true",
            configUrl = "$dir/$id.onnx.json?download=true"
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

    /** All Piper voices (en_US + fr_FR) */
    val ALL: List<PiperVoice> = EN_US + FR_FR

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
}

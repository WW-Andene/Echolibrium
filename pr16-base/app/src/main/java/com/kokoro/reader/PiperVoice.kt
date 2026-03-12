package com.kokoro.reader

/**
 * Piper TTS voice definition and catalog.
 *
 * Each Piper voice is a single VITS model (.onnx) that also requires
 * shared assets: tokens.txt + espeak-ng-data/ for phonemization.
 *
 * Voice packages are downloaded from k2-fsa/sherpa-onnx GitHub releases
 * as tar.bz2 archives, each self-contained with model + shared assets.
 *
 * Storage: filesDir/sherpa/piper/{voiceId}/model.onnx, tokens.txt, espeak-ng-data/
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

    // ── English (US) — curated selection ────────────────────────────────────
    private val EN_US = listOf(
        piper("lessac",  "Female", "English (US)", "American", "en_US", "medium"),
        piper("amy",     "Female", "English (US)", "American", "en_US", "medium"),
        piper("kristin", "Female", "English (US)", "American", "en_US", "medium"),
        piper("ryan",    "Male",   "English (US)", "American", "en_US", "medium"),
        piper("joe",     "Male",   "English (US)", "American", "en_US", "medium"),
        piper("bryce",   "Male",   "English (US)", "American", "en_US", "medium"),
    )

    // ── English (UK) ────────────────────────────────────────────────────────
    private val EN_GB = listOf(
        piper("alba",  "Female", "English (UK)", "British", "en_GB", "medium"),
        piper("alan",  "Male",   "English (UK)", "British", "en_GB", "medium"),
        piper("cori",  "Female", "English (UK)", "British", "en_GB", "medium"),
    )

    val ALL: List<PiperVoice> = EN_US + EN_GB

    fun byId(id: String): PiperVoice? = ALL.find { it.id == id }
    fun default(): PiperVoice = ALL.first()

    fun byGender(g: String) = if (g == "All") ALL else ALL.filter { it.gender == g }
    fun byLanguage(l: String) = if (l == "All") ALL else ALL.filter { it.language == l }
    fun languages() = listOf("All") + ALL.map { it.language }.distinct().sorted()
    fun genders() = listOf("All", "Female", "Male")

    /** True if this voice ID belongs to a Piper voice (not Kokoro) */
    fun isPiperVoice(voiceId: String): Boolean = byId(voiceId) != null

    /**
     * Download URL for a Piper voice tar.bz2 from k2-fsa/sherpa-onnx releases.
     * Pattern: vits-piper-{locale}-{name}-{quality}.tar.bz2
     */
    fun downloadUrl(voiceId: String): String {
        return "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-$voiceId.tar.bz2"
    }

    /** The directory name inside the tar.bz2 archive */
    fun archiveDirName(voiceId: String): String = "vits-piper-$voiceId"
}

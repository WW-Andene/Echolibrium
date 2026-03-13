package com.echolibrium.kyokan

/**
 * Catalog of voices bundled in the Kokoro en-v0_19 model.
 * sid = speaker ID passed to OfflineTts.generate()
 * Naming convention: af_ = American Female, am_ = American Male,
 *                    bf_ = British Female, bm_ = British Male
 */
data class KokoroVoice(
    val id: String,          // e.g. "af_heart"
    val sid: Int,            // speaker index in voices.bin
    val displayName: String, // e.g. "Heart"
    val gender: String,      // Female | Male
    val language: String,    // English (US) | English (UK)
    val nationality: String  // American | British
) {
    val genderIcon get() = if (gender == "Female") "♀" else "♂"
    val genderColor get() = if (gender == "Female") 0xFFff88cc.toInt() else 0xFF88ccff.toInt()
    val flagEmoji get() = if (nationality == "American") "🇺🇸" else "🇬🇧"
}

object KokoroVoices {

    // ── Full voice catalog for kokoro-en-v0_19 ────────────────────────────────
    val ALL = listOf(
        KokoroVoice("af_heart",    0,  "Heart",    "Female", "English (US)", "American"),
        KokoroVoice("af_bella",    1,  "Bella",    "Female", "English (US)", "American"),
        KokoroVoice("af_nicole",   2,  "Nicole",   "Female", "English (US)", "American"),
        KokoroVoice("af_sarah",    3,  "Sarah",    "Female", "English (US)", "American"),
        KokoroVoice("af_sky",      4,  "Sky",      "Female", "English (US)", "American"),
        KokoroVoice("am_adam",     5,  "Adam",     "Male",   "English (US)", "American"),
        KokoroVoice("am_michael",  6,  "Michael",  "Male",   "English (US)", "American"),
        KokoroVoice("bf_emma",     7,  "Emma",     "Female", "English (UK)", "British"),
        KokoroVoice("bf_isabella", 8,  "Isabella", "Female", "English (UK)", "British"),
        KokoroVoice("bm_george",   9,  "George",   "Male",   "English (UK)", "British"),
        KokoroVoice("bm_lewis",    10, "Lewis",    "Male",   "English (UK)", "British"),
    )

    fun byId(id: String): KokoroVoice? = ALL.find { it.id == id }
    fun default(): KokoroVoice = ALL.first()

    // Filter helpers
    fun byGender(g: String) = if (g == "All") ALL else ALL.filter { it.gender == g }
    fun byLanguage(l: String) = if (l == "All") ALL else ALL.filter { it.language == l }
    fun languages() = listOf("All") + ALL.map { it.language }.distinct().sorted()
    fun genders()   = listOf("All", "Female", "Male")
}

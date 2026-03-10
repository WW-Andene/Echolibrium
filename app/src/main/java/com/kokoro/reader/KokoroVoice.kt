package com.kokoro.reader

/**
 * Catalog of voices bundled in the Kokoro multi-lang-v1_0 model.
 * sid = speaker ID passed to OfflineTts.generate()
 * Naming convention: af_ = American Female, am_ = American Male,
 *                    bf_ = British Female, bm_ = British Male,
 *                    ef_ = Spanish Female, em_ = Spanish Male
 */
data class KokoroVoice(
    val id: String,          // e.g. "af_heart"
    val sid: Int,            // speaker index in voices.bin
    val displayName: String, // e.g. "Heart"
    val gender: String,      // Female | Male
    val language: String,    // English (US) | English (UK) | Spanish | etc.
    val nationality: String  // American | British | Spanish | etc.
) {
    val genderIcon get() = if (gender == "Female") "♀" else "♂"
    val genderColor get() = if (gender == "Female") 0xFFff88cc.toInt() else 0xFF88ccff.toInt()
    val flagEmoji get() = when (nationality) {
        "American" -> "🇺🇸"
        "British"  -> "🇬🇧"
        "Spanish"  -> "🇪🇸"
        else       -> "🌐"
    }
}

object KokoroVoices {

    // ── Full voice catalog for kokoro-multi-lang-v1_0 (53 speakers) ───────────
    // Speaker IDs match the order in voices.bin
    val ALL = listOf(
        // ── American Female (af_) ─────────────────────────────────────────────
        KokoroVoice("af_alloy",    0,  "Alloy",    "Female", "English (US)", "American"),
        KokoroVoice("af_aoede",    1,  "Aoede",    "Female", "English (US)", "American"),
        KokoroVoice("af_bella",    2,  "Bella",    "Female", "English (US)", "American"),
        KokoroVoice("af_heart",    3,  "Heart",    "Female", "English (US)", "American"),
        KokoroVoice("af_jessica",  4,  "Jessica",  "Female", "English (US)", "American"),
        KokoroVoice("af_kore",     5,  "Kore",     "Female", "English (US)", "American"),
        KokoroVoice("af_nicole",   6,  "Nicole",   "Female", "English (US)", "American"),
        KokoroVoice("af_nova",     7,  "Nova",     "Female", "English (US)", "American"),
        KokoroVoice("af_river",    8,  "River",    "Female", "English (US)", "American"),
        KokoroVoice("af_sarah",    9,  "Sarah",    "Female", "English (US)", "American"),
        KokoroVoice("af_sky",      10, "Sky",      "Female", "English (US)", "American"),

        // ── American Male (am_) ───────────────────────────────────────────────
        KokoroVoice("am_adam",     11, "Adam",     "Male",   "English (US)", "American"),
        KokoroVoice("am_echo",     12, "Echo",     "Male",   "English (US)", "American"),
        KokoroVoice("am_eric",     13, "Eric",     "Male",   "English (US)", "American"),
        KokoroVoice("am_fenrir",   14, "Fenrir",   "Male",   "English (US)", "American"),
        KokoroVoice("am_liam",     15, "Liam",     "Male",   "English (US)", "American"),
        KokoroVoice("am_michael",  16, "Michael",  "Male",   "English (US)", "American"),
        KokoroVoice("am_onyx",     17, "Onyx",     "Male",   "English (US)", "American"),
        KokoroVoice("am_puck",     18, "Puck",     "Male",   "English (US)", "American"),
        KokoroVoice("am_santa",    19, "Santa",    "Male",   "English (US)", "American"),

        // ── British Female (bf_) ──────────────────────────────────────────────
        KokoroVoice("bf_alice",    20, "Alice",    "Female", "English (UK)", "British"),
        KokoroVoice("bf_emma",     21, "Emma",     "Female", "English (UK)", "British"),
        KokoroVoice("bf_isabella", 22, "Isabella", "Female", "English (UK)", "British"),
        KokoroVoice("bf_lily",     23, "Lily",     "Female", "English (UK)", "British"),

        // ── British Male (bm_) ────────────────────────────────────────────────
        KokoroVoice("bm_daniel",   24, "Daniel",   "Male",   "English (UK)", "British"),
        KokoroVoice("bm_fable",    25, "Fable",    "Male",   "English (UK)", "British"),
        KokoroVoice("bm_george",   26, "George",   "Male",   "English (UK)", "British"),
        KokoroVoice("bm_lewis",    27, "Lewis",    "Male",   "English (UK)", "British"),

        // ── Spanish Female (ef_) ──────────────────────────────────────────────
        KokoroVoice("ef_dora",     28, "Dora",     "Female", "Spanish",      "Spanish"),

        // ── Spanish Male (em_) ────────────────────────────────────────────────
        KokoroVoice("em_alex",     29, "Alex",     "Male",   "Spanish",      "Spanish"),
    )

    fun byId(id: String): KokoroVoice? = ALL.find { it.id == id }
    fun default(): KokoroVoice = ALL.find { it.id == "af_heart" } ?: ALL.first()

    // Filter helpers
    fun byGender(g: String) = if (g == "All") ALL else ALL.filter { it.gender == g }
    fun byLanguage(l: String) = if (l == "All") ALL else ALL.filter { it.language == l }

    /** All language labels from both Kokoro and Piper catalogs */
    fun languages(): List<String> {
        val kokoroLangs = ALL.map { it.language }.distinct()
        val piperLangs = PiperVoiceCatalog.languages()
        return listOf("All") + (kokoroLangs + piperLangs).distinct().sorted()
    }

    fun genders()   = listOf("All", "Female", "Male")
}

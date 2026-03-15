package com.echolibrium.kyokan

import android.content.Context

/**
 * Unified voice registry: Kokoro (offline) + Piper (offline) + Orpheus (cloud).
 */
object VoiceRegistry {

    enum class Engine { KOKORO, PIPER, CLOUD }

    data class VoiceEntry(
        val id: String,
        val displayName: String,
        val engine: Engine,
        val gender: String,
        val language: String,
        val nationality: String,
        val sampleRate: Int
    )

    private val kokoroEntries: List<VoiceEntry> = KokoroVoices.ALL.map { v ->
        VoiceEntry(
            id = v.id, displayName = v.displayName, engine = Engine.KOKORO,
            gender = v.gender, language = v.language, nationality = v.nationality,
            sampleRate = 24000
        )
    }

    private val piperEntries: List<VoiceEntry> = PiperVoices.ALL.map { v ->
        VoiceEntry(
            id = v.id, displayName = v.displayName, engine = Engine.PIPER,
            gender = v.gender, language = v.language, nationality = v.nationality,
            sampleRate = 22050
        )
    }

    // ── Cloud voices (Orpheus only) ─────────────────────────────────────────

    data class CloudVoice(
        val id: String,
        val displayName: String,
        val gender: String,
        val language: String,
        val nationality: String,
        val apiVoiceName: String
    ) {
        val genderIcon get() = if (gender == "Female") "♀" else "♂"
        val genderColor get() = if (gender == "Female") 0xFFd4a0b8.toInt() else 0xFF88aad4.toInt()
    }

    val CLOUD_VOICES = listOf(
        CloudVoice("cloud_orpheus_tara",  "Tara",  "Female", "English", "American", "tara"),
        CloudVoice("cloud_orpheus_leah",  "Leah",  "Female", "English", "American", "leah"),
        CloudVoice("cloud_orpheus_jess",  "Jess",  "Female", "English", "American", "jess"),
        CloudVoice("cloud_orpheus_mia",   "Mia",   "Female", "English", "American", "mia"),
        CloudVoice("cloud_orpheus_zoe",   "Zoe",   "Female", "English", "American", "zoe"),
        CloudVoice("cloud_orpheus_leo",   "Leo",   "Male",   "English", "American", "leo"),
        CloudVoice("cloud_orpheus_dan",   "Dan",   "Male",   "English", "American", "dan"),
        CloudVoice("cloud_orpheus_zac",   "Zac",   "Male",   "English", "American", "zac"),
    )

    val cloudEntries: List<VoiceEntry> = CLOUD_VOICES.map { v ->
        VoiceEntry(
            id = v.id, displayName = v.displayName, engine = Engine.CLOUD,
            gender = v.gender, language = v.language, nationality = v.nationality,
            sampleRate = 24000
        )
    }

    fun cloudVoiceById(id: String): CloudVoice? = CLOUD_VOICES.find { it.id == id }
    fun isCloud(voiceId: String): Boolean = engineFor(voiceId) == Engine.CLOUD

    val ALL: List<VoiceEntry> = kokoroEntries + piperEntries + cloudEntries

    fun byId(id: String): VoiceEntry? = ALL.find { it.id == id }
    fun engineFor(voiceId: String): Engine? = byId(voiceId)?.engine
    fun isKokoro(voiceId: String): Boolean = engineFor(voiceId) == Engine.KOKORO
    fun isPiper(voiceId: String): Boolean = engineFor(voiceId) == Engine.PIPER

    fun isReady(ctx: Context, voiceId: String): Boolean {
        val c = ctx.container
        val entry = byId(voiceId) ?: return false
        return when (entry.engine) {
            Engine.KOKORO -> c.voiceDownloadManager.isModelReady(ctx)
            Engine.PIPER -> c.piperDownloadManager.isVoiceReady(ctx, voiceId)
            Engine.CLOUD -> c.cloudTtsEngine.isEnabled()
        }
    }

    fun sampleRate(voiceId: String): Int = byId(voiceId)?.sampleRate ?: 24000

    fun byEngine(engine: Engine): List<VoiceEntry> = ALL.filter { it.engine == engine }
    fun genders(): List<String> = listOf("All", "Female", "Male")
    fun languages(): List<String> = listOf("All") + ALL.map { it.language }.distinct().sorted()
    fun engines(): List<String> = listOf("All", "Kokoro", "Piper", "Orpheus")
}

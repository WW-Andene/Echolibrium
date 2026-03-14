package com.echolibrium.kyokan

import org.json.JSONArray
import org.json.JSONObject

// ── Voice classification — handles Kokoro naming + generic SherpaTTS voices ──
// SherpaTTS voice names can be:
//   Kokoro:  af_heart, am_adam, bf_emma, bm_george  → prefix encodes lang+gender
//   Generic: en-us-amy-medium, fr-fr-gilles-low, de_DE-thorsten-high
//   Or:      just a plain name like "default" or "en-US"
data class VoiceInfo(
    val name: String,
    val gender: String,     // Female | Male | Unknown
    val language: String,   // English (US) | English (UK) | French | German | etc.
    val nationality: String // American | British | French | etc. — for filter display
) {
    val shortName: String get() = when {
        // Kokoro: af_heart → "Heart", am_adam → "Adam"
        name.length > 3 && name[2] == '_' -> name.drop(3).replaceFirstChar { it.uppercase() }
        // Generic hyphen format: en-us-amy-medium → "Amy"
        name.contains("-") -> name.split("-").firstOrNull { it.length > 2 && it[0].isLetter() && it.all { c -> c.isLetter() } && it !in setOf("en","fr","de","es","it","pt","nl","ru","zh","ja","ko","ar","hi","us","gb","uk","au","ca") }
            ?.replaceFirstChar { it.uppercase() } ?: name.split("-").getOrNull(2)?.replaceFirstChar { it.uppercase() } ?: name
        else -> name.replaceFirstChar { it.uppercase() }
    }

    companion object {
        fun from(voiceName: String): VoiceInfo {
            val n = voiceName.lowercase().trim()

            // ── Kokoro prefix format: af_ am_ bf_ bm_ ────────────────────────
            if (n.length > 3 && n[2] == '_') {
                val prefix = n.take(2)
                val gender = when {
                    prefix == "af" || prefix == "bf" -> "Female"
                    prefix == "am" || prefix == "bm" -> "Male"
                    else -> "Unknown"
                }
                val (language, nationality) = when {
                    prefix == "af" || prefix == "am" -> Pair("English (US)", "American")
                    prefix == "bf" || prefix == "bm" -> Pair("English (UK)", "British")
                    else -> Pair("Unknown", "Unknown")
                }
                return VoiceInfo(voiceName, gender, language, nationality)
            }

            // ── Generic hyphen format: en-us-amy-medium ───────────────────────
            val parts = n.split("-", "_")
            val langCode = parts.getOrNull(0) ?: ""
            val regionCode = parts.getOrNull(1) ?: ""

            val language = when (langCode) {
                "en" -> when (regionCode) {
                    "us", "american" -> "English (US)"
                    "gb", "uk", "british" -> "English (UK)"
                    "au", "australian" -> "English (AU)"
                    "ca" -> "English (CA)"
                    else -> "English"
                }
                "fr" -> "French"
                "de" -> "German"
                "es" -> "Spanish"
                "it" -> "Italian"
                "pt" -> "Portuguese"
                "nl" -> "Dutch"
                "ru" -> "Russian"
                "zh" -> "Chinese"
                "ja" -> "Japanese"
                "ko" -> "Korean"
                "ar" -> "Arabic"
                "hi" -> "Hindi"
                else -> if (langCode.length == 2 || langCode.length == 3) langCode.uppercase() else "Unknown"
            }

            val nationality = when {
                language.startsWith("English") -> when {
                    regionCode in setOf("us", "american") -> "American"
                    regionCode in setOf("gb", "uk", "british") -> "British"
                    regionCode in setOf("au", "australian") -> "Australian"
                    language == "English (US)" -> "American"
                    language == "English (UK)" -> "British"
                    else -> "English"
                }
                else -> language
            }

            // Gender detection from common names in voice IDs
            val namePart = n.replace(langCode, "").replace(regionCode, "")
            val gender = when {
                namePart.contains(Regex("female|woman|girl|femme|frau|mujer|donna")) -> "Female"
                namePart.contains(Regex("male|man|boy|homme|mann|hombre|uomo")) -> "Male"
                namePart.contains(Regex("amy|emma|anna|lisa|sara|claire|linda|mary|jenny|sonia|ava|nova|aria")) -> "Female"
                namePart.contains(Regex("adam|james|ryan|john|alex|david|mark|george|thomas|harry|alan|thor|gilles")) -> "Male"
                else -> "Unknown"
            }

            return VoiceInfo(voiceName, gender, language, nationality)
        }
    }
}

// ── Personality sensitivity coefficients ──────────────────────────────────────
data class PersonalitySensitivity(
    val distressSensitivity: Float = 1.0f,   // 0.0 (numb) to 2.0 (hypersensitive)
    val warmthSensitivity:   Float = 1.0f,
    val fakeSensitivity:     Float = 1.0f,
    val moodVelocity:        Float = 1.0f,   // how fast mood shifts
    val moodDecayRate:       Float = 0.08f,  // per minute
    val rangeReactivity:     Float = 1.0f,   // pitch range multiplier
    val speedReactivity:     Float = 1.0f
) {
    fun toJson() = JSONObject().apply {
        put("distressSensitivity", distressSensitivity)
        put("warmthSensitivity", warmthSensitivity)
        put("fakeSensitivity", fakeSensitivity)
        put("moodVelocity", moodVelocity)
        put("moodDecayRate", moodDecayRate)
        put("rangeReactivity", rangeReactivity)
        put("speedReactivity", speedReactivity)
    }

    companion object {
        fun fromJson(j: JSONObject) = PersonalitySensitivity(
            distressSensitivity = j.optDouble("distressSensitivity", 1.0).toFloat(),
            warmthSensitivity   = j.optDouble("warmthSensitivity", 1.0).toFloat(),
            fakeSensitivity     = j.optDouble("fakeSensitivity", 1.0).toFloat(),
            moodVelocity        = j.optDouble("moodVelocity", 1.0).toFloat(),
            moodDecayRate       = j.optDouble("moodDecayRate", 0.08).toFloat(),
            rangeReactivity     = j.optDouble("rangeReactivity", 1.0).toFloat(),
            speedReactivity     = j.optDouble("speedReactivity", 1.0).toFloat()
        )
    }
}

// ── Gimmick model ─────────────────────────────────────────────────────────────
data class GimmickConfig(
    val type: String,
    val frequency: Int,
    val position: String = "RANDOM"
) {
    fun toJson() = JSONObject().apply {
        put("type", type); put("frequency", frequency); put("position", position)
    }

    fun toTransform() = VoiceTransform.Gimmick(
        type, "always", frequency,
        VoiceTransform.GimmickPosition.valueOf(position)
    )

    companion object {
        fun fromJson(j: JSONObject) = GimmickConfig(
            j.optString("type", "sigh"),
            j.optInt("frequency", 0),
            j.optString("position", "RANDOM")
        )
    }
}

// ── Voice Identity — the "who": engine, model, base pitch/speed ──────────────
data class VoiceIdentity(
    val voiceName: String = "",
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val voiceAlias: String = "",
    val translateTo: String = ""
) {
    fun toJson(j: JSONObject) {
        j.put("voiceName", voiceName); j.put("pitch", pitch); j.put("speed", speed)
        j.put("voiceAlias", voiceAlias); j.put("translateTo", translateTo)
    }

    companion object {
        fun fromJson(j: JSONObject) = VoiceIdentity(
            voiceName = j.optString("voiceName", ""),
            pitch = j.optDouble("pitch", 1.0).toFloat(),
            speed = j.optDouble("speed", 1.0).toFloat(),
            voiceAlias = j.optString("voiceAlias", ""),
            translateTo = j.optString("translateTo", "")
        )
    }
}

// ── Expression Map — the "how they react": DSP parameter mappings ────────────
data class ExpressionMap(
    val breathIntensity: Int = 0,
    val breathCurvePosition: Float = 0f,
    val breathPause: Int = 0,
    val stutterIntensity: Int = 0,
    val stutterPosition: Float = 0f,
    val stutterFrequency: Int = 0,
    val stutterPause: Int = 30,
    val intonationIntensity: Int = 0,
    val intonationVariation: Float = 0.5f
) {
    fun toJson(j: JSONObject) {
        j.put("breathIntensity", breathIntensity)
        j.put("breathCurvePosition", breathCurvePosition)
        j.put("breathPause", breathPause)
        j.put("stutterIntensity", stutterIntensity)
        j.put("stutterPosition", stutterPosition)
        j.put("stutterFrequency", stutterFrequency)
        j.put("stutterPause", stutterPause)
        j.put("intonationIntensity", intonationIntensity)
        j.put("intonationVariation", intonationVariation)
    }

    companion object {
        fun fromJson(j: JSONObject) = ExpressionMap(
            breathIntensity = j.optInt("breathIntensity", 0),
            breathCurvePosition = j.optDouble("breathCurvePosition", 0.0).toFloat(),
            breathPause = j.optInt("breathPause", 0),
            stutterIntensity = j.optInt("stutterIntensity", 0),
            stutterPosition = j.optDouble("stutterPosition", 0.0).toFloat(),
            stutterFrequency = j.optInt("stutterFrequency", 0),
            stutterPause = j.optInt("stutterPause", 30),
            intonationIntensity = j.optInt("intonationIntensity", 0),
            intonationVariation = j.optDouble("intonationVariation", 0.5).toFloat()
        )
    }
}

// ── Gimmick Set — the "personality texture": transform chain config ──────────
data class GimmickSet(
    val gimmicks: List<GimmickConfig> = emptyList(),
    val commentaryPools: List<CommentaryPool> = emptyList()
) {
    fun toJson(j: JSONObject) {
        val ga = JSONArray(); gimmicks.forEach { ga.put(it.toJson()) }; j.put("gimmicks", ga)
        val ca = JSONArray(); commentaryPools.forEach { ca.put(it.toJson()) }; j.put("commentaryPools", ca)
    }

    companion object {
        fun fromJson(j: JSONObject) = GimmickSet(
            gimmicks = j.optJSONArray("gimmicks")?.let { arr ->
                (0 until arr.length()).map { GimmickConfig.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
            commentaryPools = j.optJSONArray("commentaryPools")?.let { arr ->
                (0 until arr.length()).map { CommentaryPool.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList()
        )
    }
}

// ── Voice Profile — named container composing Identity + Expression + Gimmicks ──
// Backward-compatible: flat property access (profile.pitch) still works via delegation.
// JSON serialization stays flat for SharedPreferences backward compat.
data class VoiceProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Profile",
    val emoji: String = "🎙️",

    // Composed sub-types — can be swapped independently (same voice, different expression)
    val identity: VoiceIdentity = VoiceIdentity(),
    val expression: ExpressionMap = ExpressionMap(),
    val gimmickSet: GimmickSet = GimmickSet(),

    // Personality sensitivity
    val sensitivity: PersonalitySensitivity = PersonalitySensitivity(),

    // ── Backward-compat constructor overrides ────────────────────────────

    // These let existing code do: VoiceProfile(pitch = 1.2f, breathIntensity = 30)
    // They delegate to the composed sub-types.
    val voiceName: String = identity.voiceName,
    val pitch: Float = identity.pitch,
    val speed: Float = identity.speed,
    val voiceAlias: String = identity.voiceAlias,
    val translateTo: String = identity.translateTo,
    val breathIntensity: Int = expression.breathIntensity,
    val breathCurvePosition: Float = expression.breathCurvePosition,
    val breathPause: Int = expression.breathPause,
    val stutterIntensity: Int = expression.stutterIntensity,
    val stutterPosition: Float = expression.stutterPosition,
    val stutterFrequency: Int = expression.stutterFrequency,
    val stutterPause: Int = expression.stutterPause,
    val intonationIntensity: Int = expression.intonationIntensity,
    val intonationVariation: Float = expression.intonationVariation,
    val gimmicks: List<GimmickConfig> = gimmickSet.gimmicks,
    val commentaryPools: List<CommentaryPool> = gimmickSet.commentaryPools
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("emoji", emoji)
        // Serialize flat (backward-compat with existing SharedPreferences data)
        put("voiceName", voiceName); put("pitch", pitch); put("speed", speed)
        put("breathIntensity", breathIntensity)
        put("breathCurvePosition", breathCurvePosition)
        put("breathPause", breathPause)
        put("stutterIntensity", stutterIntensity)
        put("stutterPosition", stutterPosition)
        put("stutterFrequency", stutterFrequency)
        put("stutterPause", stutterPause)
        put("intonationIntensity", intonationIntensity)
        put("intonationVariation", intonationVariation)
        put("sensitivity", sensitivity.toJson())
        val ga = JSONArray(); gimmicks.forEach { ga.put(it.toJson()) }; put("gimmicks", ga)
        val ca = JSONArray(); commentaryPools.forEach { ca.put(it.toJson()) }; put("commentaryPools", ca)
        put("voiceAlias", voiceAlias)
        put("translateTo", translateTo)
    }

    companion object {
        fun fromJson(j: JSONObject): VoiceProfile {
            // Parse sub-types from flat JSON
            val ident = VoiceIdentity.fromJson(j)
            val expr = ExpressionMap.fromJson(j)
            val gimmick = GimmickSet.fromJson(j)
            val sens = j.optJSONObject("sensitivity")?.let { PersonalitySensitivity.fromJson(it) } ?: PersonalitySensitivity()

            return VoiceProfile(
                id = j.optString("id", java.util.UUID.randomUUID().toString()),
                name = j.optString("name", "Profile"),
                emoji = j.optString("emoji", "🎙️"),
                identity = ident,
                expression = expr,
                gimmickSet = gimmick,
                sensitivity = sens
            )
        }

        // ── Personality presets ───────────────────────────────────────────────
        // Helper: build pre+post pools with optional condition
        private fun pools(
            pre: List<String> = emptyList(), preFreq: Int = 40,
            post: List<String> = emptyList(), postFreq: Int = 30,
            condition: CommentaryCondition = CommentaryCondition("always")
        ) = listOf(
            CommentaryPool(position = "pre",  condition = condition, lines = pre,  frequency = preFreq),
            CommentaryPool(position = "post", condition = condition, lines = post, frequency = postFreq)
        ).filter { it.lines.isNotEmpty() }

        val PRESETS = listOf(
            VoiceProfile(name = "Natural", emoji = "😐",
                pitch = 1.0f, speed = 1.0f),

            VoiceProfile(name = "Excited", emoji = "🎉",
                pitch = 1.45f, speed = 1.4f,
                intonationIntensity = 70, intonationVariation = 0.85f,
                stutterIntensity = 20, stutterFrequency = 15, stutterPosition = 0.0f,
                gimmicks = listOf(GimmickConfig("woah", 40, "START"), GimmickConfig("laugh", 30, "END")),
                commentaryPools = pools(
                    pre = listOf("Oh my god listen to this!", "You are NOT gonna believe it—", "Okay this is huge—"), preFreq = 50,
                    post = listOf("I KNOW RIGHT?!", "Can you believe it?!", "Wild!"), postFreq = 40
                ) + pools(
                    pre = listOf("Ooh a message!"), preFreq = 60,
                    condition = CommentaryCondition("intent_request")
                ) + pools(
                    pre = listOf("Someone's asking you something!"), preFreq = 55,
                    condition = CommentaryCondition("time_morning")
                )),

            VoiceProfile(name = "Bored", emoji = "😒",
                pitch = 0.85f, speed = 0.75f,
                intonationIntensity = 10, intonationVariation = 0.1f,
                gimmicks = listOf(GimmickConfig("yawn", 50, "START"), GimmickConfig("hmm", 35, "END")),
                commentaryPools = pools(
                    pre = listOf("...this again.", "Oh look, another one.", "Sure."), preFreq = 45,
                    post = listOf("Whatever.", "Cool.", "Not like I care."), postFreq = 40
                ) + pools(
                    pre = listOf("Oh great, the phone again."), preFreq = 70,
                    condition = CommentaryCondition("flooded")
                ) + pools(
                    pre = listOf("Who texts at this hour."), preFreq = 60,
                    condition = CommentaryCondition("time_night")
                )),

            VoiceProfile(name = "Depressed", emoji = "😔",
                pitch = 0.72f, speed = 0.65f,
                breathIntensity = 30, breathCurvePosition = 1.0f, breathPause = 20,
                intonationIntensity = 5, intonationVariation = 0.05f,
                gimmicks = listOf(GimmickConfig("sigh", 70, "START"), GimmickConfig("hmm", 30, "END")),
                commentaryPools = pools(
                    pre = listOf("Sure, why not.", "Not like anything matters anyway.", "Here we go..."), preFreq = 55,
                    post = listOf("...figures.", "Of course.", "At least someone's happy."), postFreq = 35
                ) + pools(
                    pre = listOf("Even at night they won't let me rest."), preFreq = 65,
                    condition = CommentaryCondition("time_night")
                ) + pools(
                    post = listOf("I'll deal with it tomorrow. Or never."), postFreq = 50,
                    condition = CommentaryCondition("flooded")
                )),

            VoiceProfile(name = "Flirty", emoji = "😏",
                pitch = 1.25f, speed = 0.88f,
                breathIntensity = 35, breathCurvePosition = 0.5f, breathPause = 30,
                intonationIntensity = 55, intonationVariation = 0.7f,
                gimmicks = listOf(GimmickConfig("giggle", 45, "END"), GimmickConfig("sigh", 25, "MID"), GimmickConfig("mmm", 30, "START")),
                commentaryPools = pools(
                    pre = listOf("Ooh, someone's thinking of you~", "Well well well~"), preFreq = 50,
                    post = listOf("How charming.", "You're popular today~"), postFreq = 45
                ) + pools(
                    pre = listOf("A message~ how intimate~"), preFreq = 60,
                    condition = CommentaryCondition("source_personal")
                ) + pools(
                    pre = listOf("Late night message~ spicy~"), preFreq = 70,
                    condition = CommentaryCondition("time_night")
                ) + pools(
                    post = listOf("Someone's curious about you~"), postFreq = 55,
                    condition = CommentaryCondition("intent_request")
                )),

            VoiceProfile(name = "Gentle", emoji = "🌸",
                pitch = 1.12f, speed = 0.82f,
                breathIntensity = 25, breathCurvePosition = 0.4f, breathPause = 40,
                intonationIntensity = 30, intonationVariation = 0.4f,
                gimmicks = listOf(GimmickConfig("mmm", 25, "START"), GimmickConfig("aww", 20, "END")),
                commentaryPools = pools(
                    pre = listOf("Someone reached out to you.", "A message for you."), preFreq = 40,
                    post = listOf("Take your time with that.", "How thoughtful."), postFreq = 35
                ) + pools(
                    pre = listOf("Someone's thinking of you this late."), preFreq = 45,
                    condition = CommentaryCondition("time_night")
                ) + pools(
                    post = listOf("They seem to need something."), postFreq = 40,
                    condition = CommentaryCondition("intent_request")
                )),

            VoiceProfile(name = "Happy", emoji = "😄",
                pitch = 1.35f, speed = 1.15f,
                intonationIntensity = 60, intonationVariation = 0.75f,
                gimmicks = listOf(GimmickConfig("laugh", 35, "END"), GimmickConfig("woah", 20, "START"), GimmickConfig("aww", 20, "RANDOM")),
                commentaryPools = pools(
                    pre = listOf("Oh yay, a notification!", "Ooh ooh ooh—"), preFreq = 45,
                    post = listOf("Love it!", "This made my day!", "Woohoo!"), postFreq = 40
                ) + pools(
                    pre = listOf("Good morning sunshine!"), preFreq = 70,
                    condition = CommentaryCondition("time_morning")
                ) + pools(
                    post = listOf("So many messages, everyone loves you!"), postFreq = 50,
                    condition = CommentaryCondition("flooded")
                )),

            VoiceProfile(name = "Hangry", emoji = "😤",
                pitch = 1.05f, speed = 1.25f,
                intonationIntensity = 65, intonationVariation = 0.6f,
                gimmicks = listOf(GimmickConfig("ugh", 55, "START"), GimmickConfig("tsk", 40, "END"), GimmickConfig("huh", 35, "MID")),
                commentaryPools = pools(
                    pre = listOf("Seriously?", "NOW what?", "Can I not have ONE minute?"), preFreq = 60,
                    post = listOf("FINE.", "Unbelievable.", "...I need food."), postFreq = 50
                ) + pools(
                    pre = listOf("Again?! That's like the tenth one!"), preFreq = 80,
                    condition = CommentaryCondition("flooded")
                ) + pools(
                    pre = listOf("Who texts at this hour. Who DOES that."), preFreq = 75,
                    condition = CommentaryCondition("time_night")
                )),

            VoiceProfile(name = "Nervous", emoji = "😰",
                pitch = 1.2f, speed = 1.1f,
                stutterIntensity = 45, stutterFrequency = 40, stutterPosition = 0.0f, stutterPause = 60,
                gimmicks = listOf(GimmickConfig("huh", 30, "MID"), GimmickConfig("hmm", 25, "RANDOM")),
                commentaryPools = pools(
                    pre = listOf("Oh no, what now—", "Is this bad? This might be bad.", "Don't panic—"), preFreq = 55,
                    post = listOf("...okay that's fine. Fine.", "Should I be worried?"), postFreq = 50
                ) + pools(
                    pre = listOf("Why is the email app notifying me?!"), preFreq = 65,
                    condition = CommentaryCondition("source_personal")
                ) + pools(
                    pre = listOf("A question? What question? What did I do?"), preFreq = 60,
                    condition = CommentaryCondition("intent_request")
                )),

            VoiceProfile(name = "Whispery", emoji = "🤫",
                pitch = 1.1f, speed = 0.72f,
                breathIntensity = 65, breathCurvePosition = 0.55f, breathPause = 50,
                gimmicks = listOf(GimmickConfig("sigh", 20, "START")),
                commentaryPools = pools(
                    pre = listOf("Psst...", "Hey, listen—", "Just between us—"), preFreq = 45,
                    post = listOf("...don't tell anyone.", "Just thought you should know."), postFreq = 35
                ) + pools(
                    pre = listOf("Shh... everyone's asleep."), preFreq = 70,
                    condition = CommentaryCondition("time_night")
                )),

            VoiceProfile(name = "Robot", emoji = "🤖",
                pitch = 0.5f, speed = 0.78f,
                intonationIntensity = 0,
                commentaryPools = pools(
                    pre = listOf("Incoming transmission.", "Notification received.", "Alert."), preFreq = 60,
                    post = listOf("End of message.", "Transmission complete.", "Awaiting response."), postFreq = 50
                ) + pools(
                    pre = listOf("High priority alert detected."), preFreq = 70,
                    condition = CommentaryCondition("urgency_real")
                ) + pools(
                    pre = listOf("Query incoming."), preFreq = 65,
                    condition = CommentaryCondition("intent_request")
                )),

            VoiceProfile(name = "Drunk", emoji = "🥴",
                pitch = 0.88f, speed = 0.82f,
                stutterIntensity = 35, stutterFrequency = 45, stutterPosition = 0.4f,
                intonationIntensity = 55, intonationVariation = 0.95f,
                gimmicks = listOf(GimmickConfig("huh", 40, "RANDOM"), GimmickConfig("hmm", 30, "MID"), GimmickConfig("laugh", 25, "END")),
                commentaryPools = pools(
                    pre = listOf("Okay okay okay—", "Wait, wait, listen—", "DUDE."), preFreq = 55,
                    post = listOf("I love you, man.", "...what were we talking about?", "Anyway—"), postFreq = 50
                ) + pools(
                    pre = listOf("Who's texting at this hour, we're all out!"), preFreq = 75,
                    condition = CommentaryCondition("time_night")
                ) + pools(
                    post = listOf("That's SO many messages bro."), postFreq = 65,
                    condition = CommentaryCondition("flooded")
                )),

            VoiceProfile(name = "Elder", emoji = "🧓",
                pitch = 0.78f, speed = 0.7f,
                breathIntensity = 22, breathCurvePosition = 1.0f, breathPause = 15,
                gimmicks = listOf(GimmickConfig("hmm", 40, "START"), GimmickConfig("yawn", 20, "END")),
                commentaryPools = pools(
                    pre = listOf("Now let me see here...", "Mm, what's this now.", "Oh my, a message."), preFreq = 50,
                    post = listOf("How about that.", "Well I never.", "Kids these days."), postFreq = 45
                ) + pools(
                    pre = listOf("Who sends messages at this hour? No manners."), preFreq = 65,
                    condition = CommentaryCondition("time_night")
                ) + pools(
                    post = listOf("So many messages. Back in my day we called."), postFreq = 55,
                    condition = CommentaryCondition("flooded")
                )),

            VoiceProfile(name = "Child", emoji = "🧒",
                pitch = 1.85f, speed = 1.2f,
                intonationIntensity = 45, intonationVariation = 0.7f,
                gimmicks = listOf(GimmickConfig("woah", 35, "START"), GimmickConfig("giggle", 40, "END")),
                commentaryPools = pools(
                    pre = listOf("OOOOH!", "Look look look!", "Is it a present?!"), preFreq = 55,
                    post = listOf("So cool!", "Again again!", "Do it again!"), postFreq = 50
                ) + pools(
                    pre = listOf("You got SO many messages today!"), preFreq = 70,
                    condition = CommentaryCondition("flooded")
                )),

            VoiceProfile(name = "Dramatic", emoji = "🎭",
                pitch = 1.0f, speed = 0.82f,
                intonationIntensity = 90, intonationVariation = 0.95f,
                gimmicks = listOf(GimmickConfig("gasp", 50, "START"), GimmickConfig("sigh", 35, "END")),
                commentaryPools = pools(
                    pre = listOf("Brace yourself.", "This... changes everything.", "Gather round."), preFreq = 65,
                    post = listOf("The audacity.", "History will remember this day.", "...and scene."), postFreq = 55
                ) + pools(
                    pre = listOf("In the dead of night... a message arrives."), preFreq = 80,
                    condition = CommentaryCondition("time_night")
                ) + pools(
                    pre = listOf("They have QUESTIONS. Dark questions."), preFreq = 70,
                    condition = CommentaryCondition("intent_request")
                )),

            VoiceProfile(name = "Sarcastic", emoji = "🙄",
                pitch = 1.15f, speed = 0.9f,
                intonationIntensity = 70, intonationVariation = 0.8f,
                gimmicks = listOf(GimmickConfig("hmm", 50, "START"), GimmickConfig("tsk", 40, "END"), GimmickConfig("huh", 35, "MID")),
                commentaryPools = pools(
                    pre = listOf("Oh wow, shocking.", "Oh great, can't wait.", "Let me guess—"), preFreq = 60,
                    post = listOf("Truly groundbreaking.", "Color me surprised.", "Riveting stuff."), postFreq = 55
                ) + pools(
                    pre = listOf("Oh yes, message me at night. Brilliant timing."), preFreq = 75,
                    condition = CommentaryCondition("time_night")
                ) + pools(
                    post = listOf("Yes, another one. Because why not."), postFreq = 70,
                    condition = CommentaryCondition("flooded")
                ) + pools(
                    post = listOf("A question. How original."), postFreq = 60,
                    condition = CommentaryCondition("intent_request")
                )),
        )

        fun saveAll(profiles: List<VoiceProfile>, prefs: android.content.SharedPreferences) {
            val arr = JSONArray(); profiles.forEach { arr.put(it.toJson()) }
            prefs.edit().putString("voice_profiles", arr.toString()).apply()
        }

        fun loadAll(prefs: android.content.SharedPreferences): MutableList<VoiceProfile> {
            val json = prefs.getString("voice_profiles", null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.toMutableList()
            } catch (e: Exception) { mutableListOf() }
        }
    }
}

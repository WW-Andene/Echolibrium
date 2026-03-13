package com.echolibrium.kyokan

import android.content.Context
import android.util.Log
import java.io.File

/**
 * PhonemeTokenizer — Converts text to phoneme token IDs for DirectOrtEngine.
 *
 * Pipeline:  text → G2P (grapheme-to-phoneme) → IPA string → token IDs (LongArray)
 *
 * Kokoro models expect IPA phoneme sequences as input. sherpa-onnx uses eSpeak-NG
 * internally for this, but doesn't expose the phonemization API. This tokenizer
 * provides a pure-Kotlin alternative with:
 *
 *   1. Runtime tokens.txt parsing (phoneme symbol → integer ID)
 *   2. Rule-based English G2P for common text patterns
 *   3. Fallback: returns null for text it can't confidently phonemize
 *      → AudioPipeline falls through to SherpaEngine
 *
 * THREADING: Initialized once, then read-only. Safe to call from any thread.
 */
class PhonemeTokenizer private constructor(
    private val symbolToId: Map<String, Int>,
    private val maxSymbolLen: Int,
) {

    companion object {
        private const val TAG = "PhonemeTokenizer"

        // Pad token — Kokoro uses 0 as pad
        private const val PAD_ID = 0
        // Space token — Kokoro maps ' ' to 16
        private const val SPACE_ID = 16

        /**
         * Load tokenizer from a tokens.txt file.
         * Format: one line per token, either "symbol id" or "id symbol".
         *
         * @return PhonemeTokenizer or null if loading fails
         */
        fun load(tokensFile: File): PhonemeTokenizer? {
            if (!tokensFile.exists()) {
                Log.w(TAG, "tokens.txt not found: $tokensFile")
                return null
            }
            return try {
                val map = mutableMapOf<String, Int>()
                var maxLen = 1

                tokensFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach

                    // Try "symbol id" format first (Kokoro standard)
                    val parts = trimmed.split(" ", "\t")
                    if (parts.size >= 2) {
                        val symbol = parts[0]
                        val id = parts.last().toIntOrNull()
                        if (id != null && symbol.isNotEmpty()) {
                            map[symbol] = id
                            if (symbol.length > maxLen) maxLen = symbol.length
                        }
                    }
                }

                if (map.isEmpty()) {
                    Log.w(TAG, "No tokens loaded from $tokensFile")
                    return null
                }

                Log.i(TAG, "Loaded ${map.size} tokens (max symbol len: $maxLen)")
                PhonemeTokenizer(map, maxLen)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load tokens.txt", e)
                null
            }
        }

        /**
         * Load from Kokoro model directory.
         */
        fun loadKokoro(ctx: Context): PhonemeTokenizer? {
            val modelDir = VoiceDownloadManager.getModelDir(ctx)
            return load(File(modelDir, "tokens.txt"))
        }

        /**
         * Load from Piper voice directory.
         */
        fun loadPiper(ctx: Context, voiceId: String): PhonemeTokenizer? {
            val voiceDir = PiperDownloadManager.getVoiceDir(ctx, voiceId)
            return load(File(voiceDir, "tokens.txt"))
        }
    }

    val vocabularySize: Int get() = symbolToId.size

    // ─── Tokenization ────────────────────────────────────────────────

    /**
     * Convert text to token IDs for Kokoro synthesis.
     *
     * @param text Raw English text (notification content)
     * @return LongArray of token IDs, or null if G2P fails
     */
    fun tokenize(text: String): LongArray? {
        val cleaned = cleanText(text)
        if (cleaned.isBlank()) return null

        val ipa = textToIpa(cleaned) ?: return null
        if (ipa.isBlank()) return null

        val ids = ipaToTokenIds(ipa)
        if (ids.isEmpty()) return null

        // Sanity check: if most tokens mapped to PAD, G2P probably failed
        val padCount = ids.count { it == PAD_ID.toLong() }
        if (padCount > ids.size * 0.4) {
            Log.w(TAG, "Too many unmapped tokens ($padCount/${ids.size}), falling back")
            return null
        }

        return ids
    }

    /**
     * Convert an IPA phoneme string to token IDs.
     * Uses greedy longest-match to handle multi-character IPA symbols.
     */
    fun ipaToTokenIds(ipa: String): LongArray {
        val ids = mutableListOf<Long>()
        var i = 0

        while (i < ipa.length) {
            // Greedy longest match
            var matched = false
            val end = minOf(i + maxSymbolLen, ipa.length)

            for (len in (end - i) downTo 1) {
                val symbol = ipa.substring(i, i + len)
                val id = symbolToId[symbol]
                if (id != null) {
                    ids.add(id.toLong())
                    i += len
                    matched = true
                    break
                }
            }

            if (!matched) {
                // Unknown symbol — skip it
                i++
            }
        }

        return ids.toLongArray()
    }

    // ─── Text Cleaning ───────────────────────────────────────────────

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("https?://\\S+"), " ")     // strip URLs
            .replace(Regex("[#@]\\w+"), " ")           // strip hashtags/mentions
            .replace(Regex("[<>{}\\[\\]|\\\\^~`]"), " ") // strip markup chars
            .replace(Regex("\\s+"), " ")               // normalize whitespace
            .trim()
    }

    // ─── Grapheme-to-Phoneme (English) ───────────────────────────────

    /**
     * Convert English text to IPA phoneme string.
     *
     * Uses a rule-based approach with:
     *   1. Common word dictionary (~120 high-frequency words)
     *   2. Letter-to-phoneme rules for unknown words
     *   3. Basic punctuation handling
     *
     * Returns null for text that's too complex or non-English.
     */
    private fun textToIpa(text: String): String? {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null

        val result = StringBuilder()

        for ((idx, word) in words.withIndex()) {
            if (idx > 0) result.append(' ')

            // Handle punctuation-only tokens
            if (word.all { it in ".,!?;:—…\"()" }) {
                result.append(word)
                continue
            }

            // Strip trailing punctuation, keep it
            val (core, trailing) = splitTrailingPunctuation(word)
            if (core.isEmpty()) {
                result.append(trailing)
                continue
            }

            // Try dictionary first
            val lower = core.lowercase()
            val dictIpa = WORD_DICT[lower]
            if (dictIpa != null) {
                result.append(dictIpa)
            } else if (isNumber(core)) {
                // Number → spell out
                val numberIpa = numberToIpa(core)
                if (numberIpa != null) result.append(numberIpa)
                else result.append(rulesG2P(core))
            } else {
                result.append(rulesG2P(lower))
            }

            if (trailing.isNotEmpty()) result.append(trailing)
        }

        val ipa = result.toString()
        return ipa.ifBlank { null }
    }

    private fun splitTrailingPunctuation(word: String): Pair<String, String> {
        var i = word.length
        while (i > 0 && word[i - 1] in ".,!?;:—…\"()") i--
        return Pair(word.substring(0, i), word.substring(i))
    }

    private fun isNumber(s: String): Boolean =
        s.all { it.isDigit() || it == '.' || it == ',' || it == ':' }

    /**
     * Rule-based G2P for unknown English words.
     * Applies common English pronunciation rules in sequence.
     */
    private fun rulesG2P(word: String): String {
        val sb = StringBuilder()
        sb.append('ˈ')  // default primary stress on first syllable

        var i = 0
        val w = word.lowercase()
        val len = w.length

        while (i < len) {
            val remaining = w.substring(i)
            var matched = false

            // Try multi-character rules first (longest match)
            for ((pattern, phoneme) in LETTER_RULES) {
                if (remaining.startsWith(pattern)) {
                    sb.append(phoneme)
                    i += pattern.length
                    matched = true
                    break
                }
            }

            if (!matched) {
                // Single character fallback
                val ch = w[i]
                val phoneme = SINGLE_LETTER[ch]
                if (phoneme != null) {
                    sb.append(phoneme)
                } else if (ch.isLetter()) {
                    sb.append(ch) // pass through unknown letters
                }
                i++
            }
        }

        return sb.toString()
    }

    // ─── Number Handling ─────────────────────────────────────────────

    private fun numberToIpa(num: String): String? {
        val clean = num.replace(",", "")

        // Time format (3:30)
        if (':' in clean) {
            val parts = clean.split(':')
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                val hIpa = smallNumberToIpa(h) ?: return null
                val mIpa = if (m == 0) "" else " ${smallNumberToIpa(m) ?: return null}"
                return "$hIpa$mIpa"
            }
        }

        val n = clean.toIntOrNull() ?: return null
        return smallNumberToIpa(n)
    }

    private fun smallNumberToIpa(n: Int): String? {
        if (n < 0 || n > 9999) return null
        return NUMBERS[n] ?: run {
            // Compose from parts
            when {
                n in 10..19 -> NUMBERS[n]
                n in 20..99 -> {
                    val tens = (n / 10) * 10
                    val ones = n % 10
                    val tensIpa = NUMBERS[tens] ?: return null
                    if (ones == 0) tensIpa
                    else "$tensIpa ${NUMBERS[ones] ?: return null}"
                }
                n in 100..999 -> {
                    val hundreds = n / 100
                    val rest = n % 100
                    val hIpa = "${NUMBERS[hundreds] ?: return null} ˈhʌndɹɪd"
                    if (rest == 0) hIpa
                    else "$hIpa ${smallNumberToIpa(rest) ?: return null}"
                }
                n in 1000..9999 -> {
                    val thousands = n / 1000
                    val rest = n % 1000
                    val tIpa = "${NUMBERS[thousands] ?: return null} ˈθaʊzənd"
                    if (rest == 0) tIpa
                    else "$tIpa ${smallNumberToIpa(rest) ?: return null}"
                }
                else -> null
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Static data — loaded once, shared across all instances
// ═══════════════════════════════════════════════════════════════════

// Multi-character English letter patterns → IPA phonemes
// Ordered longest-first for greedy matching
private val LETTER_RULES: List<Pair<String, String>> = listOf(
    // Vowel digraphs and patterns
    "ough" to "ʌf",
    "tion" to "ʃən",
    "sion" to "ʒən",
    "ious" to "iəs",
    "eous" to "iəs",
    "ight" to "aɪt",
    "ould" to "ʊd",
    "ment" to "mənt",
    "ness" to "nəs",
    "able" to "eɪbəl",
    "ible" to "ɪbəl",
    "ture" to "tʃɚ",
    "sure" to "ʃɚ",
    "ough" to "oʊ",
    "ous" to "əs",
    "ing" to "ɪŋ",
    "ong" to "ɔŋ",
    "ung" to "ʌŋ",
    "ang" to "æŋ",
    "ank" to "æŋk",
    "ink" to "ɪŋk",
    "unk" to "ʌŋk",
    "nk" to "ŋk",
    "ng" to "ŋ",
    "th" to "θ",
    "sh" to "ʃ",
    "ch" to "tʃ",
    "ph" to "f",
    "wh" to "w",
    "ck" to "k",
    "gh" to "",      // silent in most positions
    "wr" to "ɹ",
    "kn" to "n",
    "gn" to "n",
    "qu" to "kw",
    "ee" to "iː",
    "ea" to "iː",
    "oo" to "uː",
    "ou" to "aʊ",
    "ow" to "oʊ",
    "oi" to "ɔɪ",
    "oy" to "ɔɪ",
    "ai" to "eɪ",
    "ay" to "eɪ",
    "ei" to "eɪ",
    "ey" to "iː",
    "ie" to "iː",
    "ue" to "uː",
    "aw" to "ɔː",
    "au" to "ɔː",
    "er" to "ɚ",
    "ir" to "ɚ",
    "ur" to "ɚ",
    "or" to "ɔɹ",
    "ar" to "ɑɹ",
    "al" to "ɔl",
    "ll" to "l",
    "ss" to "s",
    "ff" to "f",
    "tt" to "t",
    "dd" to "d",
    "pp" to "p",
    "bb" to "b",
    "mm" to "m",
    "nn" to "n",
    "rr" to "ɹ",
    "ge" to "dʒ",
)

// Single letter → IPA phoneme (fallback)
private val SINGLE_LETTER: Map<Char, String> = mapOf(
    'a' to "æ",
    'b' to "b",
    'c' to "k",
    'd' to "d",
    'e' to "ɛ",
    'f' to "f",
    'g' to "ɡ",
    'h' to "h",
    'i' to "ɪ",
    'j' to "dʒ",
    'k' to "k",
    'l' to "l",
    'm' to "m",
    'n' to "n",
    'o' to "ɑ",
    'p' to "p",
    'q' to "k",
    'r' to "ɹ",
    's' to "s",
    't' to "t",
    'u' to "ʌ",
    'v' to "v",
    'w' to "w",
    'x' to "ks",
    'y' to "j",
    'z' to "z",
)

// High-frequency English word → IPA pronunciation dictionary
// Covers the most common words in notification text
private val WORD_DICT: Map<String, String> = mapOf(
    // Articles & determiners
    "a" to "ə",
    "an" to "ən",
    "the" to "ðə",
    "this" to "ðɪs",
    "that" to "ðæt",
    "these" to "ðiːz",
    "those" to "ðoʊz",
    "some" to "sʌm",
    "any" to "ˈɛni",
    "all" to "ɔːl",
    "each" to "iːtʃ",
    "every" to "ˈɛvɹi",
    "no" to "noʊ",
    "not" to "nɑt",

    // Pronouns
    "i" to "aɪ",
    "me" to "miː",
    "my" to "maɪ",
    "you" to "juː",
    "your" to "jɔɹ",
    "he" to "hiː",
    "him" to "hɪm",
    "his" to "hɪz",
    "she" to "ʃiː",
    "her" to "hɚ",
    "it" to "ɪt",
    "its" to "ɪts",
    "we" to "wiː",
    "us" to "ʌs",
    "our" to "aʊɚ",
    "they" to "ðeɪ",
    "them" to "ðɛm",
    "their" to "ðɛɹ",

    // Common verbs
    "is" to "ɪz",
    "are" to "ɑɹ",
    "was" to "wɑz",
    "were" to "wɚ",
    "be" to "biː",
    "been" to "bɪn",
    "being" to "ˈbiːɪŋ",
    "have" to "hæv",
    "has" to "hæz",
    "had" to "hæd",
    "do" to "duː",
    "does" to "dʌz",
    "did" to "dɪd",
    "will" to "wɪl",
    "would" to "wʊd",
    "can" to "kæn",
    "could" to "kʊd",
    "should" to "ʃʊd",
    "may" to "meɪ",
    "might" to "maɪt",
    "must" to "mʌst",
    "get" to "ɡɛt",
    "got" to "ɡɑt",
    "go" to "ɡoʊ",
    "going" to "ˈɡoʊɪŋ",
    "gone" to "ɡɔn",
    "come" to "kʌm",
    "coming" to "ˈkʌmɪŋ",
    "came" to "keɪm",
    "make" to "meɪk",
    "made" to "meɪd",
    "take" to "teɪk",
    "took" to "tʊk",
    "give" to "ɡɪv",
    "gave" to "ɡeɪv",
    "say" to "seɪ",
    "said" to "sɛd",
    "see" to "siː",
    "saw" to "sɔː",
    "know" to "noʊ",
    "knew" to "nuː",
    "think" to "θɪŋk",
    "want" to "wɑnt",
    "need" to "niːd",
    "like" to "laɪk",
    "use" to "juːz",
    "find" to "faɪnd",
    "tell" to "tɛl",
    "ask" to "æsk",
    "try" to "tɹaɪ",
    "call" to "kɔːl",
    "keep" to "kiːp",
    "let" to "lɛt",
    "put" to "pʊt",
    "set" to "sɛt",
    "run" to "ɹʌn",
    "read" to "ɹiːd",
    "send" to "sɛnd",
    "sent" to "sɛnt",
    "open" to "ˈoʊpən",
    "close" to "kloʊz",
    "start" to "stɑɹt",
    "stop" to "stɑp",
    "turn" to "tɚn",
    "move" to "muːv",
    "play" to "pleɪ",
    "pay" to "peɪ",
    "help" to "hɛlp",
    "show" to "ʃoʊ",
    "add" to "æd",
    "change" to "tʃeɪndʒ",
    "follow" to "ˈfɑloʊ",
    "leave" to "liːv",
    "left" to "lɛft",
    "work" to "wɚk",
    "look" to "lʊk",

    // Prepositions & conjunctions
    "in" to "ɪn",
    "on" to "ɑn",
    "at" to "æt",
    "to" to "tuː",
    "for" to "fɔɹ",
    "of" to "ʌv",
    "with" to "wɪθ",
    "from" to "fɹʌm",
    "by" to "baɪ",
    "about" to "əˈbaʊt",
    "up" to "ʌp",
    "out" to "aʊt",
    "off" to "ɔf",
    "down" to "daʊn",
    "over" to "ˈoʊvɚ",
    "into" to "ˈɪntuː",
    "after" to "ˈæftɚ",
    "before" to "bɪˈfɔɹ",
    "between" to "bɪˈtwiːn",
    "under" to "ˈʌndɚ",
    "and" to "ænd",
    "but" to "bʌt",
    "or" to "ɔɹ",
    "if" to "ɪf",
    "so" to "soʊ",
    "just" to "dʒʌst",
    "also" to "ˈɔːlsoʊ",
    "than" to "ðæn",
    "then" to "ðɛn",
    "now" to "naʊ",

    // Notification-specific words
    "new" to "nuː",
    "message" to "ˈmɛsɪdʒ",
    "messages" to "ˈmɛsɪdʒɪz",
    "email" to "ˈiːmeɪl",
    "notification" to "ˌnoʊtɪfɪˈkeɪʃən",
    "update" to "ˈʌpdeɪt",
    "alert" to "əˈlɚt",
    "reminder" to "ɹɪˈmaɪndɚ",
    "reply" to "ɹɪˈplaɪ",
    "replied" to "ɹɪˈplaɪd",
    "shared" to "ʃɛɹd",
    "photo" to "ˈfoʊtoʊ",
    "photos" to "ˈfoʊtoʊz",
    "video" to "ˈvɪdioʊ",
    "missed" to "mɪst",
    "received" to "ɹɪˈsiːvd",
    "download" to "ˈdaʊnloʊd",
    "complete" to "kəmˈpliːt",
    "error" to "ˈɛɹɚ",
    "warning" to "ˈwɔɹnɪŋ",
    "battery" to "ˈbætɚi",
    "charging" to "ˈtʃɑɹdʒɪŋ",
    "connected" to "kəˈnɛktɪd",
    "disconnected" to "dɪskəˈnɛktɪd",
    "available" to "əˈveɪləbəl",
    "delivered" to "dɪˈlɪvɚd",
    "payment" to "ˈpeɪmənt",
    "order" to "ˈɔɹdɚ",
    "today" to "tʊˈdeɪ",
    "tomorrow" to "tʊˈmɑɹoʊ",
    "yesterday" to "ˈjɛstɚdeɪ",
    "meeting" to "ˈmiːtɪŋ",
    "time" to "taɪm",
    "here" to "hɪɹ",
    "there" to "ðɛɹ",
    "where" to "wɛɹ",
    "when" to "wɛn",
    "what" to "wɑt",
    "who" to "huː",
    "how" to "haʊ",
    "why" to "waɪ",
    "yes" to "jɛs",
    "ok" to "ˌoʊˈkeɪ",
    "okay" to "ˌoʊˈkeɪ",
    "please" to "pliːz",
    "thanks" to "θæŋks",
    "thank" to "θæŋk",
    "sorry" to "ˈsɑɹi",
    "hello" to "həˈloʊ",
    "hi" to "haɪ",
    "hey" to "heɪ",
    "good" to "ɡʊd",
    "great" to "ɡɹeɪt",
    "nice" to "naɪs",
    "right" to "ɹaɪt",
    "back" to "bæk",
    "much" to "mʌtʃ",
    "very" to "ˈvɛɹi",
    "more" to "mɔɹ",
    "most" to "moʊst",
    "other" to "ˈʌðɚ",
    "same" to "seɪm",
    "only" to "ˈoʊnli",
    "still" to "stɪl",
    "already" to "ɔːlˈɹɛdi",
    "again" to "əˈɡɛn",
    "never" to "ˈnɛvɚ",
    "always" to "ˈɔːlweɪz",

    // Common nouns
    "people" to "ˈpiːpəl",
    "person" to "ˈpɚsən",
    "thing" to "θɪŋ",
    "things" to "θɪŋz",
    "way" to "weɪ",
    "day" to "deɪ",
    "night" to "naɪt",
    "week" to "wiːk",
    "year" to "jɪɹ",
    "home" to "hoʊm",
    "phone" to "foʊn",
    "app" to "æp",
    "file" to "faɪl",
    "data" to "ˈdeɪtə",
    "name" to "neɪm",
    "number" to "ˈnʌmbɚ",
    "place" to "pleɪs",
    "world" to "wɚld",
    "part" to "pɑɹt",

    // Adjectives
    "big" to "bɪɡ",
    "small" to "smɔːl",
    "long" to "lɔŋ",
    "old" to "oʊld",
    "first" to "fɚst",
    "last" to "læst",
    "next" to "nɛkst",
    "few" to "fjuː",
    "able" to "ˈeɪbəl",
    "free" to "fɹiː",
    "sure" to "ʃʊɹ",
    "full" to "fʊl",
    "ready" to "ˈɹɛdi",
    "real" to "ɹiːəl",
    "important" to "ɪmˈpɔɹtənt",
    "different" to "ˈdɪfɹənt",
    "possible" to "ˈpɑsɪbəl",
)

// Numbers 0-19 + tens
private val NUMBERS: Map<Int, String> = mapOf(
    0 to "ˈzɪɹoʊ",
    1 to "wʌn",
    2 to "tuː",
    3 to "θɹiː",
    4 to "fɔɹ",
    5 to "faɪv",
    6 to "sɪks",
    7 to "ˈsɛvən",
    8 to "eɪt",
    9 to "naɪn",
    10 to "tɛn",
    11 to "ɪˈlɛvən",
    12 to "twɛlv",
    13 to "ˌθɚˈtiːn",
    14 to "ˌfɔɹˈtiːn",
    15 to "ˌfɪfˈtiːn",
    16 to "ˌsɪksˈtiːn",
    17 to "ˌsɛvənˈtiːn",
    18 to "ˌeɪˈtiːn",
    19 to "ˌnaɪnˈtiːn",
    20 to "ˈtwɛnti",
    30 to "ˈθɚti",
    40 to "ˈfɔɹti",
    50 to "ˈfɪfti",
    60 to "ˈsɪksti",
    70 to "ˈsɛvənti",
    80 to "ˈeɪti",
    90 to "ˈnaɪnti",
)

package com.kokoro.reader

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * On-device translation using ML Kit. Downloads language models on demand (~30MB each),
 * then works fully offline. Thread-safe singleton.
 */
object NotificationTranslator {

    private const val TAG = "Translator"
    private const val TRANSLATE_TIMEOUT_MS = 5000L

    // Cache translators by "source→target" key to avoid recreating
    private val translators = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()

    // Track which models are ready (downloaded)
    private val readyModels = mutableSetOf<String>()

    /** Supported languages: code → display name */
    val LANGUAGES = linkedMapOf(
        "" to "Off (no translation)",
        "en" to "English",
        "fr" to "French",
        "es" to "Spanish",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "nl" to "Dutch",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "ar" to "Arabic",
        "hi" to "Hindi"
    )

    /**
     * Translate text synchronously (blocks up to 5s).
     * Returns original text if translation fails or is not needed.
     */
    fun translate(text: String, sourceLang: String, targetLang: String): String {
        if (targetLang.isBlank() || sourceLang == targetLang) return text
        if (text.isBlank()) return text

        val srcCode = toMlKitLang(sourceLang) ?: return text
        val tgtCode = toMlKitLang(targetLang) ?: return text

        val key = "$srcCode→$tgtCode"
        val translator = synchronized(translators) {
            translators.getOrPut(key) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(srcCode)
                    .setTargetLanguage(tgtCode)
                    .build()
                Translation.getClient(options)
            }
        }

        // Ensure model is downloaded
        if (key !in readyModels) {
            val latch = CountDownLatch(1)
            var downloaded = false
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { downloaded = true; latch.countDown() }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Model download failed for $key", e)
                    latch.countDown()
                }
            if (!latch.await(TRANSLATE_TIMEOUT_MS, TimeUnit.MILLISECONDS) || !downloaded) {
                Log.w(TAG, "Model not ready for $key, returning original text")
                return text
            }
            readyModels.add(key)
        }

        // Translate synchronously
        val latch = CountDownLatch(1)
        var result = text
        translator.translate(text)
            .addOnSuccessListener { translated ->
                result = translated
                latch.countDown()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Translation failed for $key", e)
                latch.countDown()
            }
        latch.await(TRANSLATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return result
    }

    /**
     * Pre-download a translation model pair in the background.
     * Call this when the user selects a translation target in profile settings.
     */
    fun ensureModel(sourceLang: String, targetLang: String, onResult: (Boolean) -> Unit = {}) {
        val srcCode = toMlKitLang(sourceLang) ?: run { onResult(false); return }
        val tgtCode = toMlKitLang(targetLang) ?: run { onResult(false); return }
        val key = "$srcCode→$tgtCode"
        if (key in readyModels) { onResult(true); return }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(srcCode)
            .setTargetLanguage(tgtCode)
            .build()
        val translator = Translation.getClient(options)
        synchronized(translators) { translators[key] = translator }

        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { readyModels.add(key); onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /** Map our language codes to ML Kit's TranslateLanguage codes */
    private fun toMlKitLang(code: String): String? = when (code.lowercase().take(2)) {
        "en" -> TranslateLanguage.ENGLISH
        "fr" -> TranslateLanguage.FRENCH
        "es" -> TranslateLanguage.SPANISH
        "de" -> TranslateLanguage.GERMAN
        "it" -> TranslateLanguage.ITALIAN
        "pt" -> TranslateLanguage.PORTUGUESE
        "nl" -> TranslateLanguage.DUTCH
        "ru" -> TranslateLanguage.RUSSIAN
        "ja" -> TranslateLanguage.JAPANESE
        "ko" -> TranslateLanguage.KOREAN
        "zh" -> TranslateLanguage.CHINESE
        "ar" -> TranslateLanguage.ARABIC
        "hi" -> TranslateLanguage.HINDI
        else -> null
    }
}

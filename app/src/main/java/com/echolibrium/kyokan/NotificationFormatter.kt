package com.echolibrium.kyokan

/**
 * O-03: Pure functions extracted from NotificationReaderService for testability.
 * No Android dependencies — can be tested in plain JVM unit tests.
 */
object NotificationFormatter {

    /**
     * Build the spoken message from notification parts.
     * Extracted from NotificationReaderService.buildMessage().
     */
    fun buildMessage(
        appName: String,
        title: String,
        text: String,
        mode: String,
        readAppName: Boolean = true
    ): String = when (mode) {
        "app_only"   -> appName
        "title_only" -> "$appName. $title"
        "text_only"  -> text
        else         -> buildString {
            if (readAppName) append("$appName. ")
            if (title.isNotBlank()) append("$title. ")
            if (text.isNotBlank()) append(text)
        }
    }

    /**
     * Check whether the current hour falls within a DND window.
     * Handles overnight windows (e.g. 22:00–08:00).
     * Returns false when start == end (no window).
     */
    fun isDndActiveForHour(hour: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (start > end) hour >= start || hour < end else hour in start until end
    }

    /**
     * Apply find/replace word rules to text (case-insensitive).
     * Extracted from NotificationReaderService.applyWordRules().
     */
    fun applyWordRules(text: String, rules: List<Pair<String, String>>): String {
        if (rules.isEmpty()) return text
        var result = text
        for ((find, replace) in rules) {
            if (find.isNotBlank()) {
                result = result.replace(find, replace, ignoreCase = true)
            }
        }
        return result
    }
}

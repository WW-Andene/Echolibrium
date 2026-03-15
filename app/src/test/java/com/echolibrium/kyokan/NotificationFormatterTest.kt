package com.echolibrium.kyokan

import org.junit.Assert.*
import org.junit.Test

/**
 * O-03: Unit tests for NotificationFormatter — DND logic, message building,
 * and word rule application. Pure JVM tests with no Android dependencies.
 */
class NotificationFormatterTest {

    // ── DND time window tests ───────────────────────────────────────────────

    @Test
    fun `isDndActive overnight window 22-08 active at 23`() {
        assertTrue(NotificationFormatter.isDndActiveForHour(23, 22, 8))
    }

    @Test
    fun `isDndActive overnight window 22-08 active at 0`() {
        assertTrue(NotificationFormatter.isDndActiveForHour(0, 22, 8))
    }

    @Test
    fun `isDndActive overnight window 22-08 active at 7`() {
        assertTrue(NotificationFormatter.isDndActiveForHour(7, 22, 8))
    }

    @Test
    fun `isDndActive overnight window 22-08 inactive at 8`() {
        assertFalse(NotificationFormatter.isDndActiveForHour(8, 22, 8))
    }

    @Test
    fun `isDndActive overnight window 22-08 inactive at 12`() {
        assertFalse(NotificationFormatter.isDndActiveForHour(12, 22, 8))
    }

    @Test
    fun `isDndActive overnight window 22-08 inactive at 21`() {
        assertFalse(NotificationFormatter.isDndActiveForHour(21, 22, 8))
    }

    @Test
    fun `isDndActive daytime window 09-17 active at 12`() {
        assertTrue(NotificationFormatter.isDndActiveForHour(12, 9, 17))
    }

    @Test
    fun `isDndActive daytime window 09-17 inactive at 8`() {
        assertFalse(NotificationFormatter.isDndActiveForHour(8, 9, 17))
    }

    @Test
    fun `isDndActive daytime window 09-17 inactive at 17`() {
        assertFalse(NotificationFormatter.isDndActiveForHour(17, 9, 17))
    }

    @Test
    fun `isDndActive same start and end returns false`() {
        assertFalse(NotificationFormatter.isDndActiveForHour(12, 12, 12))
    }

    @Test
    fun `isDndActive start at boundary`() {
        assertTrue(NotificationFormatter.isDndActiveForHour(22, 22, 8))
    }

    // ── buildMessage tests ──────────────────────────────────────────────────

    @Test
    fun `buildMessage full mode with app name`() {
        val msg = NotificationFormatter.buildMessage("WhatsApp", "John", "Hello!", "full", readAppName = true)
        assertEquals("WhatsApp. John. Hello!", msg)
    }

    @Test
    fun `buildMessage full mode without app name`() {
        val msg = NotificationFormatter.buildMessage("WhatsApp", "John", "Hello!", "full", readAppName = false)
        assertEquals("John. Hello!", msg)
    }

    @Test
    fun `buildMessage app_only mode`() {
        val msg = NotificationFormatter.buildMessage("WhatsApp", "John", "Hello!", "app_only")
        assertEquals("WhatsApp", msg)
    }

    @Test
    fun `buildMessage title_only mode`() {
        val msg = NotificationFormatter.buildMessage("WhatsApp", "John", "Hello!", "title_only")
        assertEquals("WhatsApp. John", msg)
    }

    @Test
    fun `buildMessage text_only mode`() {
        val msg = NotificationFormatter.buildMessage("WhatsApp", "John", "Hello!", "text_only")
        assertEquals("Hello!", msg)
    }

    @Test
    fun `buildMessage full mode blank title omitted`() {
        val msg = NotificationFormatter.buildMessage("App", "", "Message", "full", readAppName = true)
        assertEquals("App. Message", msg)
    }

    @Test
    fun `buildMessage full mode blank text omitted`() {
        val msg = NotificationFormatter.buildMessage("App", "Title", "", "full", readAppName = true)
        assertEquals("App. Title.", msg)
    }

    // ── Word rule application tests ─────────────────────────────────────────

    @Test
    fun `applyWordRules single replacement`() {
        val rules = listOf("lol" to "laughing out loud")
        val result = NotificationFormatter.applyWordRules("He said lol", rules)
        assertEquals("He said laughing out loud", result)
    }

    @Test
    fun `applyWordRules case insensitive`() {
        val rules = listOf("LOL" to "laughing out loud")
        val result = NotificationFormatter.applyWordRules("he said lol", rules)
        assertEquals("he said laughing out loud", result)
    }

    @Test
    fun `applyWordRules multiple rules`() {
        val rules = listOf("btw" to "by the way", "imo" to "in my opinion")
        val result = NotificationFormatter.applyWordRules("btw, imo this is good", rules)
        assertEquals("by the way, in my opinion this is good", result)
    }

    @Test
    fun `applyWordRules empty rules returns original`() {
        val result = NotificationFormatter.applyWordRules("hello world", emptyList())
        assertEquals("hello world", result)
    }

    @Test
    fun `applyWordRules blank find patterns are skipped`() {
        val rules = listOf("" to "should not appear", "hi" to "hello")
        val result = NotificationFormatter.applyWordRules("hi there", rules)
        assertEquals("hello there", result)
    }

    @Test
    fun `applyWordRules replacement to empty string removes text`() {
        val rules = listOf("spam" to "")
        val result = NotificationFormatter.applyWordRules("this is spam content", rules)
        assertEquals("this is  content", result)
    }
}

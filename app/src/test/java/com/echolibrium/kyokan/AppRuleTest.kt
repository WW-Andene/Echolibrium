package com.echolibrium.kyokan

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * O-03: Unit tests for AppRule serialization and validation.
 */
class AppRuleTest {

    @Test
    fun `toJson round-trip preserves all fields`() {
        val rule = AppRule(
            packageName = "com.example.app",
            appLabel = "Example App",
            enabled = false,
            readMode = "title_only",
            profileId = "profile-123",
            forceLocal = true
        )
        val json = rule.toJson()
        val restored = AppRule.fromJson(json)

        assertEquals(rule.packageName, restored.packageName)
        assertEquals(rule.appLabel, restored.appLabel)
        assertEquals(rule.enabled, restored.enabled)
        assertEquals(rule.readMode, restored.readMode)
        assertEquals(rule.profileId, restored.profileId)
        assertEquals(rule.forceLocal, restored.forceLocal)
    }

    @Test
    fun `fromJson handles missing fields with defaults`() {
        val json = JSONObject().apply {
            put("packageName", "com.test")
            put("appLabel", "Test")
        }
        val rule = AppRule.fromJson(json)

        assertEquals("com.test", rule.packageName)
        assertEquals("Test", rule.appLabel)
        assertTrue(rule.enabled)
        assertEquals("full", rule.readMode)
        assertEquals("", rule.profileId)
        assertFalse(rule.forceLocal)
    }

    @Test
    fun `parseJsonArray handles corrupted entries gracefully`() {
        val jsonArray = """[
            {"packageName": "com.a", "appLabel": "A"},
            "broken",
            {"packageName": "com.b", "appLabel": "B"}
        ]"""
        val rules = AppRule.parseJsonArray(jsonArray)

        assertEquals(2, rules.size)
        assertEquals("com.a", rules[0].packageName)
        assertEquals("com.b", rules[1].packageName)
    }

    @Test
    fun `parseJsonArray returns empty list for empty array`() {
        val rules = AppRule.parseJsonArray("[]")
        assertTrue(rules.isEmpty())
    }

    @Test
    fun `toJson includes schema version`() {
        val json = AppRule(packageName = "com.test", appLabel = "Test").toJson()
        assertEquals(1, json.getInt("_v"))
    }

    @Test
    fun `forceLocal defaults to false for old JSON without field`() {
        val json = JSONObject().apply {
            put("packageName", "com.old")
            put("appLabel", "Old App")
            put("enabled", true)
        }
        val rule = AppRule.fromJson(json)
        assertFalse(rule.forceLocal)
    }

    @Test
    fun `all read modes are preserved in round-trip`() {
        val modes = listOf("full", "title_only", "app_only", "text_only", "skip")
        for (mode in modes) {
            val rule = AppRule(packageName = "com.test", appLabel = "Test", readMode = mode)
            val restored = AppRule.fromJson(rule.toJson())
            assertEquals(mode, restored.readMode)
        }
    }
}

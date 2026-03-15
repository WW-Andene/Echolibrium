package com.echolibrium.kyokan

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * O-03: Unit tests for VoiceProfile serialization and validation.
 */
class VoiceProfileTest {

    @Test
    fun `toJson round-trip preserves all fields`() {
        val profile = VoiceProfile(
            id = "test-id-123",
            name = "My Voice",
            emoji = "🎤",
            voiceName = "kokoro_en_af_bella",
            pitch = 1.25f,
            speed = 1.50f
        )
        val json = profile.toJson()
        val restored = VoiceProfile.fromJson(json)

        assertEquals(profile.id, restored.id)
        assertEquals(profile.name, restored.name)
        assertEquals(profile.emoji, restored.emoji)
        assertEquals(profile.voiceName, restored.voiceName)
        assertEquals(profile.pitch, restored.pitch, 0.01f)
        assertEquals(profile.speed, restored.speed, 0.01f)
    }

    @Test
    fun `fromJson handles missing fields with defaults`() {
        val json = JSONObject().apply {
            put("id", "abc")
            put("name", "Test")
        }
        val profile = VoiceProfile.fromJson(json)

        assertEquals("abc", profile.id)
        assertEquals("Test", profile.name)
        assertEquals("🎙️", profile.emoji)
        assertEquals("", profile.voiceName)
        assertEquals(1.0f, profile.pitch, 0.01f)
        assertEquals(1.0f, profile.speed, 0.01f)
    }

    @Test
    fun `fromJson clamps pitch within valid range`() {
        val json = JSONObject().apply {
            put("pitch", 5.0)  // Max is 2.0
        }
        val profile = VoiceProfile.fromJson(json)
        assertEquals(2.0f, profile.pitch, 0.01f)
    }

    @Test
    fun `fromJson clamps speed within valid range`() {
        val json = JSONObject().apply {
            put("speed", 0.1)  // Min is 0.5
        }
        val profile = VoiceProfile.fromJson(json)
        assertEquals(0.5f, profile.speed, 0.01f)
    }

    @Test
    fun `parseJsonArray handles corrupted entries gracefully`() {
        val jsonArray = """[
            {"id": "good-1", "name": "Good"},
            "not-an-object",
            {"id": "good-2", "name": "Also Good"}
        ]"""
        val profiles = VoiceProfile.parseJsonArray(jsonArray)

        // Should salvage 2 valid profiles, skip the corrupted one
        assertEquals(2, profiles.size)
        assertEquals("good-1", profiles[0].id)
        assertEquals("good-2", profiles[1].id)
    }

    @Test
    fun `parseJsonArray returns empty list for empty array`() {
        val profiles = VoiceProfile.parseJsonArray("[]")
        assertTrue(profiles.isEmpty())
    }

    @Test
    fun `default profile has sensible values`() {
        val profile = VoiceProfile()

        assertFalse(profile.id.isBlank())
        assertEquals("New Profile", profile.name)
        assertEquals("🎙️", profile.emoji)
        assertEquals("", profile.voiceName)
        assertEquals(1.0f, profile.pitch, 0.01f)
        assertEquals(1.0f, profile.speed, 0.01f)
    }

    @Test
    fun `toJson includes schema version`() {
        val json = VoiceProfile().toJson()
        assertEquals(1, json.getInt("_v"))
    }

    @Test
    fun `fromJson precision is maintained for pitch and speed`() {
        val json = JSONObject().apply {
            put("pitch", 1.15)
            put("speed", 1.75)
        }
        val profile = VoiceProfile.fromJson(json)
        assertEquals(1.15f, profile.pitch, 0.01f)
        assertEquals(1.75f, profile.speed, 0.01f)
    }
}

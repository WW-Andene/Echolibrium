package com.echolibrium.kyokan

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O-03: Integration tests for SettingsRepository with in-memory Room database.
 * Verifies the full data flow: write → Room → read → correct values.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private lateinit var db: KyokanDatabase
    private lateinit var prefs: SharedPreferences
    private lateinit var repo: SettingsRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KyokanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = ctx.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repo = SettingsRepository(db, prefs)
    }

    @After
    fun tearDown() {
        db.close()
        prefs.edit().clear().commit()
    }

    // ── Voice Profiles ──────────────────────────────────────────────────────

    @Test
    fun saveAndRetrieveProfiles() {
        val profiles = listOf(
            VoiceProfile(id = "p1", name = "Alice", voiceName = "kokoro_en_af"),
            VoiceProfile(id = "p2", name = "Bob", voiceName = "kokoro_en_am")
        )
        repo.saveProfiles(profiles)

        val loaded = repo.getProfiles()
        assertEquals(2, loaded.size)
        assertEquals("Alice", loaded.find { it.id == "p1" }?.name)
        assertEquals("Bob", loaded.find { it.id == "p2" }?.name)
    }

    @Test
    fun saveProfileUpdatesExisting() {
        repo.saveProfile(VoiceProfile(id = "p1", name = "Original"))
        repo.saveProfile(VoiceProfile(id = "p1", name = "Updated"))

        val loaded = repo.getProfiles()
        assertEquals(1, loaded.size)
        assertEquals("Updated", loaded[0].name)
    }

    @Test
    fun deleteProfileRemovesIt() {
        repo.saveProfiles(listOf(
            VoiceProfile(id = "p1", name = "Keep"),
            VoiceProfile(id = "p2", name = "Delete")
        ))
        repo.deleteProfile("p2")

        val loaded = repo.getProfiles()
        assertEquals(1, loaded.size)
        assertEquals("Keep", loaded[0].name)
    }

    // ── App Rules ───────────────────────────────────────────────────────────

    @Test
    fun saveAndRetrieveAppRules() {
        val rules = listOf(
            AppRule(packageName = "com.a", appLabel = "App A"),
            AppRule(packageName = "com.b", appLabel = "App B", enabled = false)
        )
        repo.saveAppRules(rules)

        val loaded = repo.getAppRules()
        assertEquals(2, loaded.size)
        assertTrue(loaded.find { it.packageName == "com.a" }!!.enabled)
        assertFalse(loaded.find { it.packageName == "com.b" }!!.enabled)
    }

    @Test
    fun saveAppRulesReplacesAll() {
        repo.saveAppRules(listOf(AppRule(packageName = "com.old", appLabel = "Old")))
        repo.saveAppRules(listOf(AppRule(packageName = "com.new", appLabel = "New")))

        val loaded = repo.getAppRules()
        assertEquals(1, loaded.size)
        assertEquals("com.new", loaded[0].packageName)
    }

    // ── Word Rules ──────────────────────────────────────────────────────────

    @Test
    fun saveAndRetrieveWordRules() {
        val rules = listOf(
            WordRule(find = "lol", replace = "laughing out loud"),
            WordRule(find = "btw", replace = "by the way")
        )
        repo.saveWordRules(rules)

        val loaded = repo.getWordRules()
        assertEquals(2, loaded.size)
        assertEquals("lol", loaded[0].find)
        assertEquals("by the way", loaded[1].replace)
    }

    // ── Simple Settings ─────────────────────────────────────────────────────

    @Test
    fun stringSettingRoundTrip() {
        repo.putString("test_key", "test_value")
        assertEquals("test_value", repo.getString("test_key"))
    }

    @Test
    fun booleanSettingRoundTrip() {
        repo.putBoolean("test_bool", true)
        assertTrue(repo.getBoolean("test_bool"))
    }

    @Test
    fun intSettingRoundTrip() {
        repo.putInt("test_int", 42)
        assertEquals(42, repo.getInt("test_int"))
    }

    @Test
    fun activeProfileIdProperty() {
        repo.activeProfileId = "profile-abc"
        assertEquals("profile-abc", repo.activeProfileId)
    }

    @Test
    fun defaultValuesReturnedForMissingKeys() {
        assertEquals("fallback", repo.getString("missing", "fallback"))
        assertFalse(repo.getBoolean("missing", false))
        assertEquals(99, repo.getInt("missing", 99))
    }

    // ── Change Listeners ────────────────────────────────────────────────────

    @Test
    fun changeListenerFiresOnProfileSave() {
        val changes = mutableListOf<String>()
        repo.addChangeListener { changes.add(it) }

        repo.saveProfile(VoiceProfile(id = "p1", name = "Test"))

        assertTrue(changes.contains("voice_profiles"))
    }

    @Test
    fun changeListenerFiresOnAppRuleSave() {
        val changes = mutableListOf<String>()
        repo.addChangeListener { changes.add(it) }

        repo.saveAppRules(listOf(AppRule(packageName = "com.a", appLabel = "A")))

        assertTrue(changes.contains("app_rules"))
    }

    @Test
    fun changeListenerFiresOnWordRuleSave() {
        val changes = mutableListOf<String>()
        repo.addChangeListener { changes.add(it) }

        repo.saveWordRules(listOf(WordRule(find = "a", replace = "b")))

        assertTrue(changes.contains("wording_rules"))
    }

    @Test
    fun removedListenerDoesNotFire() {
        val changes = mutableListOf<String>()
        val listener: (String) -> Unit = { changes.add(it) }
        repo.addChangeListener(listener)
        repo.removeChangeListener(listener)

        repo.saveProfile(VoiceProfile(id = "p1", name = "Test"))

        assertTrue(changes.isEmpty())
    }

    // ── Export / Import ─────────────────────────────────────────────────────

    @Test
    fun exportAndImportRoundTrip() {
        repo.saveProfiles(listOf(VoiceProfile(id = "p1", name = "Voice 1")))
        repo.saveAppRules(listOf(AppRule(packageName = "com.a", appLabel = "A")))
        repo.saveWordRules(listOf(WordRule(find = "hi", replace = "hello")))
        repo.activeProfileId = "p1"

        val exported = repo.exportAll()

        // Clear all data
        repo.saveProfiles(emptyList())
        repo.saveAppRules(emptyList())
        repo.saveWordRules(emptyList())

        // Import
        assertTrue(repo.importAll(exported))

        assertEquals(1, repo.getProfiles().size)
        assertEquals("Voice 1", repo.getProfiles()[0].name)
        assertEquals(1, repo.getAppRules().size)
        assertEquals(1, repo.getWordRules().size)
        assertEquals("p1", repo.activeProfileId)
    }
}

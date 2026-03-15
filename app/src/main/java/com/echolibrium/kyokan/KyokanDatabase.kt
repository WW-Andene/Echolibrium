package com.echolibrium.kyokan

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * O-01: Room database for structured data (profiles, app rules, word rules).
 * Simple key-value settings remain in SharedPreferences.
 */
@Database(
    entities = [VoiceProfile::class, AppRule::class, WordRule::class],
    version = 1,
    exportSchema = false
)
abstract class KyokanDatabase : RoomDatabase() {

    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun wordRuleDao(): WordRuleDao

    companion object {
        private const val DB_NAME = "kyokan.db"

        fun create(context: Context): KyokanDatabase =
            Room.databaseBuilder(context.applicationContext, KyokanDatabase::class.java, DB_NAME)
                .allowMainThreadQueries() // Matches existing synchronous SP access pattern
                .build()
    }
}

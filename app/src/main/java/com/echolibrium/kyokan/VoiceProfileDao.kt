package com.echolibrium.kyokan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** O-01: Room DAO for voice profiles. */
@Dao
interface VoiceProfileDao {

    @Query("SELECT * FROM voice_profiles")
    fun getAll(): List<VoiceProfile>

    @Query("SELECT * FROM voice_profiles WHERE id = :id")
    fun getById(id: String): VoiceProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(profiles: List<VoiceProfile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(profile: VoiceProfile)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM voice_profiles")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM voice_profiles")
    fun count(): Int
}

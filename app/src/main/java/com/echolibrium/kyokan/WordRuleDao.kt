package com.echolibrium.kyokan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** O-01: Room DAO for word find/replace rules. */
@Dao
interface WordRuleDao {

    @Query("SELECT * FROM word_rules ORDER BY id ASC")
    fun getAll(): List<WordRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rules: List<WordRule>)

    @Query("DELETE FROM word_rules")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM word_rules")
    fun count(): Int
}

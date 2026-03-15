package com.echolibrium.kyokan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** O-01: Room DAO for app notification rules. */
@Dao
interface AppRuleDao {

    @Query("SELECT * FROM app_rules")
    fun getAll(): List<AppRule>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    fun getByPackage(pkg: String): AppRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rules: List<AppRule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rule: AppRule)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    fun deleteByPackage(pkg: String)

    @Query("DELETE FROM app_rules")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM app_rules")
    fun count(): Int
}

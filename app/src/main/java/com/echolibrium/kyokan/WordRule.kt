package com.echolibrium.kyokan

import androidx.room.Entity
import androidx.room.PrimaryKey

/** O-01: Room entity for word find/replace rules. */
@Entity(tableName = "word_rules")
data class WordRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val find: String,
    val replace: String
)

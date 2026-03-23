package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chore_logs")
data class ChoreLogEntity(
    @PrimaryKey val id: String,
    val taskName: String,
    val doneBy: String,
    val doneAt: Long,
    val note: String?,
)

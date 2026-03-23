package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_tasks")
data class RecurringTaskEntity(
    @PrimaryKey val id: String,
    val taskName: String,
    val frequency: String,
    val assignedTo: String?,
    val lastDoneAt: Long?,
    val nextDueAt: Long,
)

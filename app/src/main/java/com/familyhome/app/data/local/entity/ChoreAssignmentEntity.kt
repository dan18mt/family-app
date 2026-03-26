package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chore_assignments")
data class ChoreAssignmentEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val taskName: String,
    val assignedTo: String,   // user ID
    val assignedBy: String,   // user ID
    val status: String,       // PENDING | ACCEPTED | DECLINED
    val declineReason: String?,
    val assignedAt: Long,
    val respondedAt: Long?,
)

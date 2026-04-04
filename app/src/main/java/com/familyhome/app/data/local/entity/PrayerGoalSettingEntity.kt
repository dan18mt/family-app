package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_goal_settings")
data class PrayerGoalSettingEntity(
    @PrimaryKey val id: String,
    val sunnahKey: String,
    val isEnabled: Boolean,
    val assignedTo: String?,
    val createdBy: String,
    val createdAt: Long,
)

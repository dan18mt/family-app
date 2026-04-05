package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_goal_settings")
data class PrayerGoalSettingEntity(
    @PrimaryKey val id: String,
    val sunnahKey: String,
    val isEnabled: Boolean,
    /**
     * Stored as comma-separated user IDs, or null for "all family".
     * Backward-compatible: existing single-user IDs continue to work as a 1-element list.
     */
    val assignedTo: String?,
    /** Whether the device should fire a daily reminder notification for this goal. */
    val reminderEnabled: Boolean,
    val createdBy: String,
    val createdAt: Long,
)

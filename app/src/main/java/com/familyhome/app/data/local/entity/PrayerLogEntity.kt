package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "prayer_logs",
    indices = [Index(value = ["userId", "sunnahKey", "epochDay"], unique = true)]
)
data class PrayerLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sunnahKey: String,
    val epochDay: Long,
    val completedCount: Int,
    val loggedAt: Long,
)

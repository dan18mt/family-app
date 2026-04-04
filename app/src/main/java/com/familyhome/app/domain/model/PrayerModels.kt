package com.familyhome.app.domain.model

data class PrayerGoalSetting(
    val id: String,
    val sunnahKey: String,
    val isEnabled: Boolean,
    val assignedTo: String?, // null = all family members
    val createdBy: String,
    val createdAt: Long,
) {
    val sunnah: SunnahGoal? get() = SunnahGoal.entries.firstOrNull { it.name == sunnahKey }
}

data class PrayerLog(
    val id: String,
    val userId: String,
    val sunnahKey: String,
    val epochDay: Long, // days since 1970-01-01
    val completedCount: Int,
    val loggedAt: Long,
) {
    val sunnah: SunnahGoal? get() = SunnahGoal.entries.firstOrNull { it.name == sunnahKey }
    val isCompleted: Boolean get() = sunnah?.let { completedCount >= it.dailyTarget } ?: false
}

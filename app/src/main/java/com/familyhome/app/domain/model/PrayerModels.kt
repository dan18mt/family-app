package com.familyhome.app.domain.model

data class PrayerGoalSetting(
    val id: String,
    val sunnahKey: String,
    val isEnabled: Boolean,
    /**
     * null  → assigned to ALL family members.
     * list  → assigned to specific user IDs only.
     * The leader can expand this list but can never assign the same user twice.
     */
    val assignedUserIds: List<String>?,
    /** Whether this device should fire a daily reminder notification. */
    val reminderEnabled: Boolean,
    val createdBy: String,
    val createdAt: Long,
) {
    val sunnah: SunnahGoal? get() = SunnahGoal.entries.firstOrNull { it.name == sunnahKey }

    fun isAssignedTo(userId: String): Boolean =
        assignedUserIds == null || userId in assignedUserIds

    /** True when every member in [allUserIds] is already assigned (or already "all family"). */
    fun isFullyAssigned(allUserIds: List<String>): Boolean =
        assignedUserIds == null || allUserIds.all { it in assignedUserIds }

    /** Members in [allUserIds] not yet individually assigned. Empty when null (all family). */
    fun unassignedFrom(allUserIds: List<String>): List<String> =
        if (assignedUserIds == null) emptyList()
        else allUserIds.filter { it !in assignedUserIds }
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

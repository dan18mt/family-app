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
    /** Non-null if this goal is a regular (daily) sunnah. */
    val sunnah: SunnahGoal? get() = SunnahGoal.entries.firstOrNull { it.name == sunnahKey }

    /** Non-null if this goal is an Islamic-calendar-based sunnah event. */
    val islamicCalendarSunnah: IslamicCalendarSunnah?
        get() = IslamicCalendarSunnah.entries.firstOrNull { it.name == sunnahKey }

    /** True when this goal is an Islamic-calendar event (not a daily habit). */
    val isIslamicCalendarEvent: Boolean get() = islamicCalendarSunnah != null

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

    /**
     * True when this log entry counts as a completed day.
     *
     * For daily [SunnahGoal] entries this compares against [SunnahGoal.dailyTarget].
     * For Islamic-calendar events ([IslamicCalendarSunnah]) the daily target is always 1
     * (you either did the ibadah that day or you didn't).
     */
    val isCompleted: Boolean
        get() {
            SunnahGoal.entries.firstOrNull { it.name == sunnahKey }
                ?.let { return completedCount >= it.dailyTarget }
            IslamicCalendarSunnah.entries.firstOrNull { it.name == sunnahKey }
                ?.let { return completedCount >= 1 }
            return false
        }
}

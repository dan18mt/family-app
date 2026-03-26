package com.familyhome.app.domain.model

data class RecurringTask(
    val id: String,
    val taskName: String,
    val frequency: Frequency,
    /** null means unassigned — anyone can pick it up */
    val assignedTo: String?,
    val lastDoneAt: Long?,
    val nextDueAt: Long,
    /** Specific scheduled date/time for a one-off or the next occurrence (epoch ms). */
    val scheduledAt: Long? = null,
    /** How many minutes before scheduledAt to fire the reminder notification (null = no reminder). */
    val reminderMinutesBefore: Int? = null,
) {
    val isOverdue: Boolean get() = System.currentTimeMillis() > nextDueAt
}

enum class Frequency {
    DAILY,
    WEEKLY,
    CUSTOM;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class ChoreAssignment(
    val id: String,
    val taskId: String,
    val taskName: String,
    val assignedTo: String,  // user ID
    val assignedBy: String,  // user ID
    val status: AssignmentStatus,
    val declineReason: String?,
    val assignedAt: Long,
    val respondedAt: Long?,
)

enum class AssignmentStatus { PENDING, ACCEPTED, DECLINED }

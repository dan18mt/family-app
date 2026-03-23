package com.familyhome.app.domain.model

data class RecurringTask(
    val id: String,
    val taskName: String,
    val frequency: Frequency,
    /** null means unassigned — anyone can pick it up */
    val assignedTo: String?,
    val lastDoneAt: Long?,
    val nextDueAt: Long,
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

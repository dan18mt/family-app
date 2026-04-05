package com.familyhome.app.domain.model

import java.util.UUID

enum class NotificationType {
    JOIN_REQUEST,
    MEMBER_JOINED,
    CHORE_COMPLETED,
    CHORE_ASSIGNED,
    CHORE_REMINDER,
    CHORE_OVERDUE,
    EXPENSE_ADDED,
    LOW_STOCK,
    PRAYER_REMINDER,
    GENERAL,
}

data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    /**
     * Optional stable identifier for the logical event (e.g. expenseId, taskId).
     * Used by [NotificationCenter] to deduplicate: a new notification with the same
     * sourceId replaces any older notification with that sourceId.
     */
    val sourceId: String? = null,
    /** If true the user has silenced this alert indefinitely. */
    val isSilenced: Boolean = false,
    /** If non-null the alert is snoozed until this epoch-ms timestamp. */
    val snoozedUntil: Long? = null,
) {
    /** True when the alert should be shown (not silenced and not currently snoozed). */
    val isActive: Boolean
        get() = !isSilenced && (snoozedUntil == null || System.currentTimeMillis() >= snoozedUntil)
}

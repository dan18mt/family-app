package com.familyhome.app.domain.model

import java.util.UUID

enum class NotificationType {
    JOIN_REQUEST,
    MEMBER_JOINED,
    CHORE_COMPLETED,
    CHORE_ASSIGNED,
    EXPENSE_ADDED,
    GENERAL,
}

data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
)

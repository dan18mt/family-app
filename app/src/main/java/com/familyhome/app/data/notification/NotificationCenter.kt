package com.familyhome.app.data.notification

import com.familyhome.app.domain.model.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory notification store. Survives configuration changes (singleton),
 * but resets on process death — acceptable for local-network event notifications.
 * Newest notifications appear first.
 */
@Singleton
class NotificationCenter @Inject constructor() {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    val unreadCount: Int get() = _notifications.value.count { !it.isRead }

    fun post(notification: AppNotification) {
        _notifications.update { list ->
            // If sourceId is set, remove any earlier notification with the same sourceId
            // before prepending the new one. This prevents duplicates when, e.g., a budget
            // notification is re-sent 10 minutes after the first one.
            val deduplicated = if (notification.sourceId != null) {
                list.filter { it.sourceId != notification.sourceId }
            } else {
                list
            }
            listOf(notification) + deduplicated
        }
    }

    fun markRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
    }

    fun markAllRead() {
        _notifications.update { list -> list.map { it.copy(isRead = true) } }
    }

    /** Snooze an alert: hide it until [untilMillis]. */
    fun snooze(id: String, untilMillis: Long) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(snoozedUntil = untilMillis, isRead = true) else it }
        }
    }

    /** Silence an alert indefinitely. */
    fun silence(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isSilenced = true, isRead = true) else it }
        }
    }

    /** Restore a snoozed or silenced alert so it becomes active again. */
    fun restore(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isSilenced = false, snoozedUntil = null) else it }
        }
    }

    fun clear() {
        _notifications.value = emptyList()
    }
}

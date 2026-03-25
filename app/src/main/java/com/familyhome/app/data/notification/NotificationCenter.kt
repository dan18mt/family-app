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
        _notifications.update { listOf(notification) + it }
    }

    fun markRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
    }

    fun markAllRead() {
        _notifications.update { list -> list.map { it.copy(isRead = true) } }
    }

    fun clear() {
        _notifications.value = emptyList()
    }
}

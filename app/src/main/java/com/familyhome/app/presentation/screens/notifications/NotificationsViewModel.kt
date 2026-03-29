package com.familyhome.app.presentation.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.notification.NotificationCenter
import com.familyhome.app.domain.model.AppNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationCenter: NotificationCenter,
) : ViewModel() {

    val notifications: StateFlow<List<AppNotification>> = notificationCenter.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markRead(id: String)              = notificationCenter.markRead(id)
    fun markAllRead()                     = notificationCenter.markAllRead()
    fun snooze(id: String, until: Long)   = notificationCenter.snooze(id, until)
    fun silence(id: String)               = notificationCenter.silence(id)
    fun restore(id: String)               = notificationCenter.restore(id)
    fun clearAll()                        = notificationCenter.clear()
}

package com.familyhome.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks when each family member last synced (pushed data to the host).
 * Updated by [SyncServer] whenever it receives a push from a member device.
 * Members who synced within [ONLINE_THRESHOLD_MS] are considered online.
 */
@Singleton
class MemberPresenceTracker @Inject constructor() {

    private val _lastSeen = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastSeen: StateFlow<Map<String, Long>> = _lastSeen.asStateFlow()

    fun update(userId: String) {
        _lastSeen.update { it + (userId to System.currentTimeMillis()) }
    }

    fun isOnline(userId: String): Boolean {
        val seen = _lastSeen.value[userId] ?: return false
        return System.currentTimeMillis() - seen < ONLINE_THRESHOLD_MS
    }

    companion object {
        /** Members seen within 2 minutes are considered online. */
        const val ONLINE_THRESHOLD_MS = 2 * 60 * 1_000L
    }
}

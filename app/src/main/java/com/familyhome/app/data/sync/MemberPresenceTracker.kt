package com.familyhome.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks when each family member last synced.
 *
 * On the leader's device: updated by [SyncServer] whenever it receives a push.
 * On member devices: updated by [SyncRepositoryImpl] from the pull response's presenceMap.
 *
 * This enables all devices — not just the leader — to see each other's online status.
 */
@Singleton
class MemberPresenceTracker @Inject constructor() {

    private val _lastSeen = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastSeen: StateFlow<Map<String, Long>> = _lastSeen.asStateFlow()

    /** Mark [userId] as seen right now. */
    fun update(userId: String) {
        _lastSeen.update { it + (userId to System.currentTimeMillis()) }
    }

    /** Apply a timestamp from a remote source (e.g., pull response presenceMap). */
    fun updateWithTimestamp(userId: String, lastSeenAt: Long) {
        _lastSeen.update { current ->
            val existing = current[userId] ?: 0L
            // Only update if the remote timestamp is more recent
            if (lastSeenAt > existing) current + (userId to lastSeenAt) else current
        }
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

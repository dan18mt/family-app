package com.familyhome.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks family-member presence in two ways:
 *
 * 1. **Sync-based** ([lastSeen]): timestamp of last successful sync push/pull.
 *    Updated by [SyncServer] (leader) and [SyncRepositoryImpl] (member).
 *
 * 2. **Network-based** ([networkOnlineUserIds]): users whose device has been
 *    discovered on the local Wi-Fi via mDNS (NSD). This is always up-to-date
 *    without waiting for a sync cycle.
 *
 * [isOnline] returns `true` when either condition holds.
 */
@Singleton
class MemberPresenceTracker @Inject constructor() {

    private val _lastSeen = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastSeen: StateFlow<Map<String, Long>> = _lastSeen.asStateFlow()

    /** User IDs currently visible on the same Wi-Fi network (via NSD discovery). */
    private val _networkOnlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    val networkOnlineUserIds: StateFlow<Set<String>> = _networkOnlineUserIds.asStateFlow()

    /** Mark [userId] as seen right now (sync-based). */
    fun update(userId: String) {
        _lastSeen.update { it + (userId to System.currentTimeMillis()) }
    }

    /** Apply a timestamp from a remote source (e.g., pull response presenceMap). */
    fun updateWithTimestamp(userId: String, lastSeenAt: Long) {
        _lastSeen.update { current ->
            val existing = current[userId] ?: 0L
            if (lastSeenAt > existing) current + (userId to lastSeenAt) else current
        }
    }

    /**
     * Replace the full set of users known to be on the same network right now.
     * Called by [FamilyBackgroundService] whenever NSD discovery changes.
     */
    fun setNetworkOnlineUsers(userIds: Set<String>) {
        _networkOnlineUserIds.value = userIds
    }

    /**
     * Returns `true` when [userId] is online — either discovered on the same
     * Wi-Fi (network-based, real-time) or synced within the last 2 minutes.
     */
    fun isOnline(userId: String): Boolean {
        if (userId in _networkOnlineUserIds.value) return true
        val seen = _lastSeen.value[userId] ?: return false
        return System.currentTimeMillis() - seen < ONLINE_THRESHOLD_MS
    }

    companion object {
        /** Sync-based threshold: members synced within 2 minutes are considered online. */
        const val ONLINE_THRESHOLD_MS = 2 * 60 * 1_000L
    }
}

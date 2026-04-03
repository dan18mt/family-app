package com.familyhome.app.data.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.familyhome.app.domain.model.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory notification store with persistent dismiss / snooze / silence state.
 *
 * Three persisted sets (stored in DataStore, survive process death):
 *  - dismissedSourceIds  — source IDs that were explicitly cleared; never re-posted.
 *  - silencedSourceIds   — source IDs that are silenced; re-applied on every post.
 *  - snoozeEntries       — "sourceId|untilMs" strings; snooze re-applied on every post.
 *
 * Newest notifications appear first.
 */
@Singleton
class NotificationCenter @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dismissedKey = stringSetPreferencesKey("notif_dismissed_source_ids")
    private val silencedKey  = stringSetPreferencesKey("notif_silenced_source_ids")
    private val snoozeKey    = stringSetPreferencesKey("notif_snooze_entries")

    private val _notifications  = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    // In-memory mirrors of persisted sets — loaded once on start-up
    private var dismissedSourceIds: Set<String> = emptySet()
    private var silencedSourceIds:  Set<String> = emptySet()
    /** Map of sourceId → snoozedUntilMs (only entries where snoozedUntil is in the future). */
    private var snoozeMap: Map<String, Long>    = emptyMap()

    val unreadCount: Int get() = _notifications.value.count { !it.isRead }

    init {
        scope.launch {
            val prefs = dataStore.data.first()
            dismissedSourceIds = prefs[dismissedKey] ?: emptySet()
            silencedSourceIds  = prefs[silencedKey]  ?: emptySet()
            snoozeMap = (prefs[snoozeKey] ?: emptySet()).decodeSnoozeMap()
                .filter { (_, until) -> until > System.currentTimeMillis() }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun post(notification: AppNotification) {
        // Skip if the user explicitly dismissed this source
        if (notification.sourceId != null && notification.sourceId in dismissedSourceIds) return

        _notifications.update { list ->
            if (notification.sourceId != null) {
                val existing = list.find { it.sourceId == notification.sourceId }
                val toInsert = when {
                    existing != null && (existing.isSilenced || existing.snoozedUntil != null) ->
                        // Preserve in-session snooze/silence already applied
                        notification.copy(
                            isSilenced   = existing.isSilenced,
                            snoozedUntil = existing.snoozedUntil,
                            isRead       = existing.isRead,
                        )
                    else -> {
                        // Apply persisted state from previous sessions
                        val silenced    = notification.sourceId in silencedSourceIds
                        val snoozedUntil = snoozeMap[notification.sourceId]
                            ?.takeIf { it > System.currentTimeMillis() }
                        notification.copy(
                            isSilenced   = silenced,
                            snoozedUntil = snoozedUntil,
                            isRead       = if (silenced || snoozedUntil != null) true else notification.isRead,
                        )
                    }
                }
                listOf(toInsert) + list.filter { it.sourceId != notification.sourceId }
            } else {
                listOf(notification) + list
            }
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

    /** Snooze an alert until [untilMillis]. Persisted so it survives app restarts. */
    fun snooze(id: String, untilMillis: Long) {
        var sourceId: String? = null
        _notifications.update { list ->
            list.map {
                if (it.id == id) {
                    sourceId = it.sourceId
                    it.copy(snoozedUntil = untilMillis, isRead = true)
                } else it
            }
        }
        sourceId?.let { sid ->
            snoozeMap = snoozeMap + (sid to untilMillis)
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[snoozeKey] = snoozeMap.encodeSnoozeSet()
                }
            }
        }
    }

    /** Silence an alert indefinitely. Persisted so it survives app restarts. */
    fun silence(id: String) {
        var sourceId: String? = null
        _notifications.update { list ->
            list.map {
                if (it.id == id) {
                    sourceId = it.sourceId
                    it.copy(isSilenced = true, isRead = true)
                } else it
            }
        }
        sourceId?.let { sid ->
            silencedSourceIds = silencedSourceIds + sid
            scope.launch {
                dataStore.edit { prefs -> prefs[silencedKey] = silencedSourceIds }
            }
        }
    }

    /** Restore a snoozed or silenced alert. Removes its persisted state. */
    fun restore(id: String) {
        var sourceId: String? = null
        _notifications.update { list ->
            list.map {
                if (it.id == id) {
                    sourceId = it.sourceId
                    it.copy(isSilenced = false, snoozedUntil = null)
                } else it
            }
        }
        sourceId?.let { sid ->
            silencedSourceIds  = silencedSourceIds  - sid
            snoozeMap          = snoozeMap           - sid
            dismissedSourceIds = dismissedSourceIds  - sid   // allow it back if user restores
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[silencedKey]  = silencedSourceIds
                    prefs[snoozeKey]    = snoozeMap.encodeSnoozeSet()
                    prefs[dismissedKey] = dismissedSourceIds
                }
            }
        }
    }

    /**
     * Clear all notifications permanently. Any sourceId that was visible is added to the
     * dismissed set so that sync re-posts never bring them back.
     */
    fun clear() {
        val toAdd = _notifications.value.mapNotNull { it.sourceId }.toSet()
        dismissedSourceIds = dismissedSourceIds + toAdd
        _notifications.value = emptyList()
        scope.launch {
            dataStore.edit { prefs -> prefs[dismissedKey] = dismissedSourceIds }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Map<String, Long>.encodeSnoozeSet(): Set<String> =
        entries.map { (k, v) -> "$k|$v" }.toSet()

    private fun Set<String>.decodeSnoozeMap(): Map<String, Long> =
        mapNotNull { entry ->
            val idx = entry.lastIndexOf('|')
            if (idx < 0) null
            else entry.substring(0, idx) to (entry.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null)
        }.toMap()
}

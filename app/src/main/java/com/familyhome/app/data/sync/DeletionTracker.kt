package com.familyhome.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks IDs of entities permanently deleted by the family leader.
 * Persisted to DataStore so deletions survive app restarts.
 *
 * Used by [SyncServer] and [SyncRepositoryImpl] to prevent re-insertion of
 * deleted entities when member devices push stale data.
 */
@Singleton
class DeletionTracker @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val deletedUserIdsKey             = stringSetPreferencesKey("deleted_user_ids")
    private val deletedPrayerGoalIdsKey       = stringSetPreferencesKey("deleted_prayer_goal_ids")
    private val deletedBudgetIdsKey           = stringSetPreferencesKey("deleted_budget_ids")
    private val deletedRecurringTaskIdsKey    = stringSetPreferencesKey("deleted_recurring_task_ids")

    private val _deletedUserIds            = MutableStateFlow<Set<String>>(emptySet())
    private val _deletedPrayerGoalIds      = MutableStateFlow<Set<String>>(emptySet())
    private val _deletedBudgetIds          = MutableStateFlow<Set<String>>(emptySet())
    private val _deletedRecurringTaskIds   = MutableStateFlow<Set<String>>(emptySet())

    val deletedUserIds: StateFlow<Set<String>> = _deletedUserIds.asStateFlow()

    // Signals when persisted deletions have been loaded from DataStore into memory.
    // Callers that run on startup must await this before reading deletion sets.
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val prefs = dataStore.data.first()
            _deletedUserIds.value          = prefs[deletedUserIdsKey]          ?: emptySet()
            _deletedPrayerGoalIds.value    = prefs[deletedPrayerGoalIdsKey]    ?: emptySet()
            _deletedBudgetIds.value        = prefs[deletedBudgetIdsKey]        ?: emptySet()
            _deletedRecurringTaskIds.value = prefs[deletedRecurringTaskIdsKey] ?: emptySet()
            ready.complete(Unit)
        }
    }

    suspend fun awaitReady() = ready.await()

    // ── Users ────────────────────────────────────────────────────────────────

    suspend fun recordUserDeletion(id: String) {
        _deletedUserIds.value = _deletedUserIds.value + id
        dataStore.edit { prefs ->
            prefs[deletedUserIdsKey] = (prefs[deletedUserIdsKey] ?: emptySet()) + id
        }
    }

    fun getDeletedUserIds(): Set<String> = _deletedUserIds.value

    fun isUserDeleted(id: String): Boolean = _deletedUserIds.value.contains(id)

    // ── Prayer goals ─────────────────────────────────────────────────────────

    suspend fun recordPrayerGoalDeletion(id: String) {
        _deletedPrayerGoalIds.value = _deletedPrayerGoalIds.value + id
        dataStore.edit { prefs ->
            prefs[deletedPrayerGoalIdsKey] = (prefs[deletedPrayerGoalIdsKey] ?: emptySet()) + id
        }
    }

    fun getDeletedPrayerGoalIds(): Set<String> = _deletedPrayerGoalIds.value

    fun isPrayerGoalDeleted(id: String): Boolean = _deletedPrayerGoalIds.value.contains(id)

    // ── Budgets ──────────────────────────────────────────────────────────────

    suspend fun recordBudgetDeletion(id: String) {
        _deletedBudgetIds.value = _deletedBudgetIds.value + id
        dataStore.edit { prefs ->
            prefs[deletedBudgetIdsKey] = (prefs[deletedBudgetIdsKey] ?: emptySet()) + id
        }
    }

    fun getDeletedBudgetIds(): Set<String> = _deletedBudgetIds.value

    fun isBudgetDeleted(id: String): Boolean = _deletedBudgetIds.value.contains(id)

    // ── Recurring tasks ──────────────────────────────────────────────────────

    suspend fun recordRecurringTaskDeletion(id: String) {
        _deletedRecurringTaskIds.value = _deletedRecurringTaskIds.value + id
        dataStore.edit { prefs ->
            prefs[deletedRecurringTaskIdsKey] = (prefs[deletedRecurringTaskIdsKey] ?: emptySet()) + id
        }
    }

    fun getDeletedRecurringTaskIds(): Set<String> = _deletedRecurringTaskIds.value

    fun isRecurringTaskDeleted(id: String): Boolean = _deletedRecurringTaskIds.value.contains(id)
}

package com.familyhome.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
 * Tracks user IDs that have been permanently deleted by the family leader.
 * Persisted to DataStore so deletions survive app restarts.
 *
 * Used by [SyncServer] to prevent re-insertion of deleted users when members push data,
 * and by [SyncRepositoryImpl] to apply leader deletions on member devices.
 */
@Singleton
class DeletionTracker @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deletedUserIdsKey = stringSetPreferencesKey("deleted_user_ids")

    private val _deletedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedUserIds: StateFlow<Set<String>> = _deletedUserIds.asStateFlow()

    init {
        scope.launch {
            val persisted = dataStore.data.first()[deletedUserIdsKey] ?: emptySet()
            _deletedUserIds.value = persisted
        }
    }

    suspend fun recordUserDeletion(id: String) {
        _deletedUserIds.value = _deletedUserIds.value + id
        dataStore.edit { prefs ->
            prefs[deletedUserIdsKey] = (prefs[deletedUserIdsKey] ?: emptySet()) + id
        }
    }

    fun getDeletedUserIds(): Set<String> = _deletedUserIds.value

    fun isUserDeleted(id: String): Boolean = _deletedUserIds.value.contains(id)
}

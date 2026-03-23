package com.familyhome.app.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.familyhome.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionRepository {

    private val currentUserKey = stringPreferencesKey("current_user_id")

    override val currentUserIdFlow: Flow<String?> =
        dataStore.data.map { it[currentUserKey] }

    override suspend fun getCurrentUserId(): String? =
        dataStore.data.first()[currentUserKey]

    override suspend fun setCurrentUserId(userId: String) {
        dataStore.edit { it[currentUserKey] = userId }
    }

    override suspend fun clearSession() {
        dataStore.edit { it.remove(currentUserKey) }
    }
}

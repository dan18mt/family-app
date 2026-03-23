package com.familyhome.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    /** Emits the current user's ID, or null when no one is logged in. */
    val currentUserIdFlow: Flow<String?>
    suspend fun getCurrentUserId(): String?
    suspend fun setCurrentUserId(userId: String)
    suspend fun clearSession()
}

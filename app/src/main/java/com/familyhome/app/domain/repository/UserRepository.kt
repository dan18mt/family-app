package com.familyhome.app.domain.repository

import com.familyhome.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getAllUsers(): Flow<List<User>>
    suspend fun getUserById(id: String): User?
    suspend fun insertUser(user: User)
    suspend fun updateUser(user: User)
    suspend fun deleteUser(id: String)
    suspend fun upsertAll(users: List<User>)
}

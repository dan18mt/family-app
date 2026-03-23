package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.UserDao
import com.familyhome.app.data.mapper.toDomain
import com.familyhome.app.data.mapper.toEntity
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val dao: UserDao,
) : UserRepository {

    override fun getAllUsers(): Flow<List<User>> =
        dao.getAllUsers().map { list -> list.map { it.toDomain() } }

    override suspend fun getUserById(id: String): User? =
        dao.getUserById(id)?.toDomain()

    override suspend fun insertUser(user: User) =
        dao.insertUser(user.toEntity())

    override suspend fun updateUser(user: User) =
        dao.updateUser(user.toEntity())

    override suspend fun deleteUser(id: String) =
        dao.deleteUser(id)

    override suspend fun upsertAll(users: List<User>) =
        dao.upsertAll(users.map { it.toEntity() })
}

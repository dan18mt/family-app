package com.familyhome.app.domain.usecase.user

import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.permission.PermissionManager
import com.familyhome.app.domain.repository.SessionRepository
import com.familyhome.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(): User? {
        val id = sessionRepository.getCurrentUserId() ?: return null
        return userRepository.getUserById(id)
    }

    fun asFlow(): Flow<String?> = sessionRepository.currentUserIdFlow
}

class GetFamilyMembersUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    operator fun invoke(): Flow<List<User>> = userRepository.getAllUsers()
}

class CreateUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
) {
    /**
     * Creates a new family member.
     * If no users exist yet, the first user is always made FATHER and the session is set.
     * Otherwise, requires [actor] to be FATHER.
     */
    suspend operator fun invoke(
        name: String,
        role: Role,
        avatarUri: String?,
        parentId: String?,
        actor: User?,
    ): Result<User> {
        val existingUsers = userRepository.getAllUsers().first()

        if (existingUsers.isNotEmpty() && actor != null) {
            if (!PermissionManager.canAddFamilyMember(actor)) {
                return Result.failure(IllegalStateException("Only Father can add family members."))
            }
        }

        val nameTaken = existingUsers.any { it.name.equals(name, ignoreCase = true) }
        if (nameTaken) {
            return Result.failure(IllegalStateException("A family member named '$name' already exists. Please choose a unique name."))
        }

        val newUser = User(
            id        = UUID.randomUUID().toString(),
            name      = name,
            role      = if (existingUsers.isEmpty()) Role.FATHER else role,
            parentId  = parentId,
            avatarUri = avatarUri,
            createdAt = System.currentTimeMillis(),
        )

        userRepository.insertUser(newUser)

        // Auto-login the first user created (family setup flow)
        if (existingUsers.isEmpty()) {
            sessionRepository.setCurrentUserId(newUser.id)
        }

        return Result.success(newUser)
    }
}

class LoginUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(userId: String): Boolean {
        val user = userRepository.getUserById(userId) ?: return false
        sessionRepository.setCurrentUserId(user.id)
        return true
    }
}

class DeleteFamilyMemberUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(actor: User, targetId: String): Result<Unit> {
        if (!PermissionManager.canRemoveFamilyMember(actor)) {
            return Result.failure(IllegalStateException("Only Father can remove family members."))
        }
        if (actor.id == targetId) {
            return Result.failure(IllegalStateException("Father cannot remove themselves."))
        }
        userRepository.deleteUser(targetId)
        return Result.success(Unit)
    }
}

class UpdateProfileUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(user: User): Result<Unit> {
        userRepository.updateUser(user)
        return Result.success(Unit)
    }
}

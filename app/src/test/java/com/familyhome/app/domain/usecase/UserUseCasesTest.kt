package com.familyhome.app.domain.usecase

import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.SessionRepository
import com.familyhome.app.domain.repository.UserRepository
import com.familyhome.app.domain.usecase.user.CreateUserUseCase
import com.familyhome.app.domain.usecase.user.DeleteFamilyMemberUseCase
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import com.familyhome.app.domain.usecase.user.UpdateProfileUseCase
import com.familyhome.app.domain.usecase.user.ValidatePinUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserUseCasesTest {

    private val userRepository = mockk<UserRepository>()
    private val sessionRepository = mockk<SessionRepository>()

    private fun fatherUser(id: String = "father-1") = User(
        id = id, name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null,
        pin = sha256("1234"), createdAt = 1_000L,
    )

    private fun wifeUser(id: String = "wife-1") = User(
        id = id, name = "Wife", role = Role.WIFE,
        parentId = "father-1", avatarUri = null,
        pin = sha256("5678"), createdAt = 2_000L,
    )

    private fun kidUser(id: String = "kid-1") = User(
        id = id, name = "Kid", role = Role.KID,
        parentId = "father-1", avatarUri = null,
        pin = sha256("9999"), createdAt = 3_000L,
    )

    private fun sha256(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Nested
    inner class GetCurrentUserUseCaseTest {
        private lateinit var useCase: GetCurrentUserUseCase

        @BeforeEach
        fun setup() { useCase = GetCurrentUserUseCase(sessionRepository, userRepository) }

        @Test
        fun `returns user when session id is set`() = runTest {
            val father = fatherUser()
            coEvery { sessionRepository.getCurrentUserId() } returns father.id
            coEvery { userRepository.getUserById(father.id) } returns father
            assertEquals(father, useCase())
        }

        @Test
        fun `returns null when no session set`() = runTest {
            coEvery { sessionRepository.getCurrentUserId() } returns null
            assertNull(useCase())
        }

        @Test
        fun `returns null when user not found for session id`() = runTest {
            coEvery { sessionRepository.getCurrentUserId() } returns "ghost-id"
            coEvery { userRepository.getUserById("ghost-id") } returns null
            assertNull(useCase())
        }
    }

    @Nested
    inner class GetFamilyMembersUseCaseTest {
        private lateinit var useCase: GetFamilyMembersUseCase

        @BeforeEach
        fun setup() { useCase = GetFamilyMembersUseCase(userRepository) }

        @Test
        fun `returns flow of all users`() = runTest {
            val members = listOf(fatherUser(), wifeUser(), kidUser())
            every { userRepository.getAllUsers() } returns flowOf(members)
            val emitted = mutableListOf<List<User>>()
            useCase().collect { emitted.add(it) }
            assertEquals(1, emitted.size)
            assertEquals(members, emitted[0])
        }
    }

    @Nested
    inner class CreateUserUseCaseTest {
        private lateinit var useCase: CreateUserUseCase

        @BeforeEach
        fun setup() { useCase = CreateUserUseCase(userRepository, sessionRepository) }

        @Test
        fun `first user becomes FATHER regardless of supplied role`() = runTest {
            every { userRepository.getAllUsers() } returns flowOf(emptyList())
            coJustRun { userRepository.insertUser(any()) }
            coJustRun { sessionRepository.setCurrentUserId(any()) }

            val result = useCase("Alice", Role.KID, "1234", null, null, null)

            assertTrue(result.isSuccess)
            assertEquals(Role.FATHER, result.getOrThrow().role)
        }

        @Test
        fun `first user auto-sets session`() = runTest {
            every { userRepository.getAllUsers() } returns flowOf(emptyList())
            coJustRun { userRepository.insertUser(any()) }
            val idSlot = slot<String>()
            coEvery { sessionRepository.setCurrentUserId(capture(idSlot)) } returns Unit

            val result = useCase("Alice", Role.FATHER, "1234", null, null, null)

            assertTrue(result.isSuccess)
            assertEquals(result.getOrThrow().id, idSlot.captured)
        }

        @Test
        fun `father can add new member`() = runTest {
            val father = fatherUser()
            every { userRepository.getAllUsers() } returns flowOf(listOf(father))
            coJustRun { userRepository.insertUser(any()) }

            val result = useCase("NewKid", Role.KID, "1111", null, father.id, father)

            assertTrue(result.isSuccess)
            assertEquals(Role.KID, result.getOrThrow().role)
        }

        @Test
        fun `non-father cannot add member`() = runTest {
            val wife = wifeUser()
            val father = fatherUser()
            every { userRepository.getAllUsers() } returns flowOf(listOf(father, wife))

            val result = useCase("NewKid", Role.KID, "1111", null, father.id, wife)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Father") == true)
        }

        @Test
        fun `duplicate name returns failure`() = runTest {
            val father = fatherUser()
            every { userRepository.getAllUsers() } returns flowOf(listOf(father))

            val result = useCase("Father", Role.WIFE, "5678", null, null, father)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("already exists") == true)
        }

        @Test
        fun `pin is stored as SHA-256 hash`() = runTest {
            every { userRepository.getAllUsers() } returns flowOf(emptyList())
            coJustRun { userRepository.insertUser(any()) }
            coJustRun { sessionRepository.setCurrentUserId(any()) }

            val result = useCase("User", Role.FATHER, "9876", null, null, null)

            assertTrue(result.isSuccess)
            assertEquals(sha256("9876"), result.getOrThrow().pin)
        }
    }

    @Nested
    inner class ValidatePinUseCaseTest {
        private lateinit var useCase: ValidatePinUseCase

        @BeforeEach
        fun setup() { useCase = ValidatePinUseCase(userRepository, sessionRepository) }

        @Test
        fun `correct pin validates and sets session`() = runTest {
            val father = fatherUser()
            coEvery { userRepository.getUserById(father.id) } returns father
            coJustRun { sessionRepository.setCurrentUserId(father.id) }

            val result = useCase(father.id, "1234")

            assertTrue(result)
            coVerify { sessionRepository.setCurrentUserId(father.id) }
        }

        @Test
        fun `wrong pin returns false without setting session`() = runTest {
            val father = fatherUser()
            coEvery { userRepository.getUserById(father.id) } returns father

            val result = useCase(father.id, "0000")

            assertFalse(result)
            coVerify(exactly = 0) { sessionRepository.setCurrentUserId(any()) }
        }

        @Test
        fun `unknown user returns false`() = runTest {
            coEvery { userRepository.getUserById("ghost") } returns null
            assertFalse(useCase("ghost", "1234"))
        }
    }

    @Nested
    inner class DeleteFamilyMemberUseCaseTest {
        private lateinit var useCase: DeleteFamilyMemberUseCase

        @BeforeEach
        fun setup() { useCase = DeleteFamilyMemberUseCase(userRepository) }

        @Test
        fun `father can delete another member`() = runTest {
            val father = fatherUser()
            val kid = kidUser()
            coJustRun { userRepository.deleteUser(kid.id) }

            val result = useCase(actor = father, targetId = kid.id)

            assertTrue(result.isSuccess)
            coVerify { userRepository.deleteUser(kid.id) }
        }

        @Test
        fun `father cannot delete themselves`() = runTest {
            val father = fatherUser()
            val result = useCase(actor = father, targetId = father.id)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("cannot remove themselves") == true)
        }

        @Test
        fun `wife cannot delete members`() = runTest {
            val result = useCase(actor = wifeUser(), targetId = "kid-1")
            assertTrue(result.isFailure)
        }

        @Test
        fun `kid cannot delete members`() = runTest {
            val result = useCase(actor = kidUser(), targetId = "other-kid")
            assertTrue(result.isFailure)
        }
    }

    @Nested
    inner class UpdateProfileUseCaseTest {
        private lateinit var useCase: UpdateProfileUseCase

        @BeforeEach
        fun setup() { useCase = UpdateProfileUseCase(userRepository) }

        @Test
        fun `updates user and returns success`() = runTest {
            val father = fatherUser()
            coJustRun { userRepository.updateUser(father) }

            val result = useCase(father)

            assertTrue(result.isSuccess)
            coVerify { userRepository.updateUser(father) }
        }
    }
}

package com.familyhome.app.presentation.screens

import app.cash.turbine.test
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.UserRepository
import com.familyhome.app.domain.usecase.user.ValidatePinUseCase
import com.familyhome.app.presentation.screens.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userRepository  = mockk<UserRepository>()
    private val validatePinUseCase = mockk<ValidatePinUseCase>()

    private fun sha256(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun makeUser(
        id: String   = "u1",
        name: String = "Ahmad",
        role: Role   = Role.FATHER,
    ) = User(id, name, role, null, null, sha256("1234"), 1_000L)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userRepository.getAllUsers() } returns flowOf(emptyList())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = LoginViewModel(userRepository, validatePinUseCase)

    @Test
    fun `initial state is empty`() = runTest {
        val vm    = createViewModel()
        val state = vm.state.value

        assertNull(state.selectedUser)
        assertEquals("", state.pin)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isSuccess)
    }

    @Test
    fun `selectUser updates state`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()

        vm.selectUser(user)

        assertEquals(user, vm.state.value.selectedUser)
        assertEquals("", vm.state.value.pin)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `onPinDigit appends digit`() = runTest {
        val vm = createViewModel()

        vm.onPinDigit("1")
        vm.onPinDigit("2")
        vm.onPinDigit("3")

        assertEquals("123", vm.state.value.pin)
    }

    @Test
    fun `onPinDigit max 4 digits`() = runTest {
        val vm = createViewModel()

        vm.onPinDigit("1")
        vm.onPinDigit("2")
        vm.onPinDigit("3")
        vm.onPinDigit("4")
        vm.onPinDigit("5") // should be ignored

        assertEquals("1234", vm.state.value.pin)
    }

    @Test
    fun `onPinBackspace removes last digit`() = runTest {
        val vm = createViewModel()

        vm.onPinDigit("1")
        vm.onPinDigit("2")
        vm.onPinBackspace()

        assertEquals("1", vm.state.value.pin)
    }

    @Test
    fun `onPinBackspace on empty pin does nothing`() = runTest {
        val vm = createViewModel()

        vm.onPinBackspace() // should not crash

        assertEquals("", vm.state.value.pin)
    }

    @Test
    fun `submitPin does nothing when no user selected`() = runTest {
        val vm = createViewModel()
        vm.onPinDigit("1")
        vm.onPinDigit("2")
        vm.onPinDigit("3")
        vm.onPinDigit("4")

        vm.submitPin()
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
    }

    @Test
    fun `submitPin does nothing when pin less than 4 digits`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()
        vm.selectUser(user)
        vm.onPinDigit("1")
        vm.onPinDigit("2")

        vm.submitPin()
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
    }

    @Test
    fun `submitPin with correct pin sets isSuccess true`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()
        vm.selectUser(user)
        vm.onPinDigit("1")
        vm.onPinDigit("2")
        vm.onPinDigit("3")
        vm.onPinDigit("4")

        coEvery { validatePinUseCase(user.id, "1234") } returns true

        vm.submitPin()
        advanceUntilIdle()

        assertTrue(vm.state.value.isSuccess)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `submitPin with wrong pin sets error and clears pin`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()
        vm.selectUser(user)
        vm.onPinDigit("9")
        vm.onPinDigit("9")
        vm.onPinDigit("9")
        vm.onPinDigit("9")

        coEvery { validatePinUseCase(user.id, "9999") } returns false

        vm.submitPin()
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error?.contains("Incorrect") == true)
        assertEquals("", vm.state.value.pin) // pin is cleared on failure
    }

    @Test
    fun `state emits loading while validating pin`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()
        vm.selectUser(user)
        vm.onPinDigit("1")
        vm.onPinDigit("2")
        vm.onPinDigit("3")
        vm.onPinDigit("4")

        coEvery { validatePinUseCase(user.id, "1234") } returns true

        vm.state.test {
            val initial = awaitItem()
            assertFalse(initial.isLoading)

            vm.submitPin()

            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val success = awaitItem()
            assertFalse(success.isLoading)
            assertTrue(success.isSuccess)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `users flow from repository is exposed`() = runTest {
        val users = listOf(makeUser(), makeUser("u2", "Siti", Role.WIFE))
        every { userRepository.getAllUsers() } returns flowOf(users)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.users.test {
            val emitted = awaitItem()
            assertEquals(users, emitted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting user clears previous error`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()

        // Force an error state by manually selecting and failing
        vm.selectUser(user)
        vm.onPinDigit("9"); vm.onPinDigit("9"); vm.onPinDigit("9"); vm.onPinDigit("9")
        coEvery { validatePinUseCase(user.id, "9999") } returns false
        vm.submitPin()
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)

        // Now select a different user — error must be cleared
        vm.selectUser(makeUser("u2", "Siti", Role.WIFE))

        assertNull(vm.state.value.error)
    }
}

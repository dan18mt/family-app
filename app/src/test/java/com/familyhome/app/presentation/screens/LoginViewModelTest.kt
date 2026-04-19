package com.familyhome.app.presentation.screens

import app.cash.turbine.test
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.UserRepository
import com.familyhome.app.domain.usecase.user.LoginUseCase
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userRepository = mockk<UserRepository>()
    private val loginUseCase   = mockk<LoginUseCase>()

    private fun makeUser(
        id: String   = "u1",
        name: String = "Ahmad",
        role: Role   = Role.FATHER,
    ) = User(id, name, role, null, null, 1_000L)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userRepository.getAllUsers() } returns flowOf(emptyList())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = LoginViewModel(userRepository, loginUseCase)

    @Test
    fun `initial state is not loading and not success`() = runTest {
        val vm    = createViewModel()
        val state = vm.state.value

        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
    }

    @Test
    fun `loginAs with valid user sets isSuccess true`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()

        coEvery { loginUseCase(user.id) } returns true

        vm.loginAs(user)
        advanceUntilIdle()

        assertTrue(vm.state.value.isSuccess)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `loginAs with unknown user does not set isSuccess`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()

        coEvery { loginUseCase(user.id) } returns false

        vm.loginAs(user)
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `state emits loading while login is in progress`() = runTest {
        val vm   = createViewModel()
        val user = makeUser()

        coEvery { loginUseCase(user.id) } returns true

        vm.state.test {
            val initial = awaitItem()
            assertFalse(initial.isLoading)

            vm.loginAs(user)

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

        vm.users.test {
            val first = awaitItem()
            if (first.isEmpty()) {
                advanceUntilIdle()
                val second = awaitItem()
                assert(second == users)
            } else {
                assert(first == users)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}

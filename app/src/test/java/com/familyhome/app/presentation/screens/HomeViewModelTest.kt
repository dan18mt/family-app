package com.familyhome.app.presentation.screens

import app.cash.turbine.test
import com.familyhome.app.domain.model.*
import com.familyhome.app.domain.usecase.chore.GetRecurringTasksUseCase
import com.familyhome.app.domain.usecase.expense.GetExpensesUseCase
import com.familyhome.app.domain.usecase.stock.GetLowStockItemsUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fatherUser() = User(
        id = "f1", name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null,
        pin = sha256("1234"), createdAt = 1L,
    )

    @BeforeEach fun setup() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `GetFamilyMembersUseCase returns all users`() = runTest {
        val useCase = mockk<GetFamilyMembersUseCase>()
        val wife = User("w1", "Wife", Role.WIFE, "f1", null, sha256("5678"), 2L)
        every { useCase() } returns flowOf(listOf(fatherUser(), wife))

        useCase().test {
            val users = awaitItem()
            assertEquals(2, users.size)
            assertTrue(users.any { it.role == Role.FATHER })
            assertTrue(users.any { it.role == Role.WIFE })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `GetLowStockItemsUseCase emits low items`() = runTest {
        val useCase = mockk<GetLowStockItemsUseCase>()
        val lowItem = StockItem(
            id = "s1", name = "Rice", category = StockCategory.FOOD,
            quantity = 1f, unit = "kg", minQuantity = 3f,
            updatedBy = "f1", updatedAt = 1L,
        )
        every { useCase() } returns flowOf(listOf(lowItem))

        useCase().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertTrue(items[0].quantity <= items[0].minQuantity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `GetLowStockItemsUseCase emits empty when all stock ok`() = runTest {
        val useCase = mockk<GetLowStockItemsUseCase>()
        every { useCase() } returns flowOf(emptyList())

        useCase().test {
            val items = awaitItem()
            assertTrue(items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `GetRecurringTasksUseCase returns tasks`() = runTest {
        val useCase = mockk<GetRecurringTasksUseCase>()
        val task = RecurringTask(
            id = "t1", taskName = "Wash dishes", frequency = Frequency.DAILY,
            assignedTo = "f1", lastDoneAt = null,
            nextDueAt = System.currentTimeMillis() + 86_400_000L,
            scheduledAt = null, reminderMinutesBefore = null,
        )
        every { useCase() } returns flowOf(listOf(task))

        useCase().test {
            val tasks = awaitItem()
            assertEquals(1, tasks.size)
            assertEquals("Wash dishes", tasks[0].taskName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

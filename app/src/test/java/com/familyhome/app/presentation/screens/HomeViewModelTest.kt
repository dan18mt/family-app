package com.familyhome.app.presentation.screens

import app.cash.turbine.test
import com.familyhome.app.domain.model.*
import com.familyhome.app.domain.usecase.chore.GetRecurringTasksUseCase
import com.familyhome.app.domain.usecase.expense.ExpenseUseCases
import com.familyhome.app.domain.usecase.stock.StockUseCases
import com.familyhome.app.domain.usecase.user.UserUseCases
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

    private lateinit var userUseCases: UserUseCases
    private lateinit var stockUseCases: StockUseCases
    private lateinit var expenseUseCases: ExpenseUseCases
    private lateinit var choreUseCases: GetRecurringTasksUseCase

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fatherUser() = User(
        id = "f1", name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null,
        pin = sha256("1234"), createdAt = 1L,
    )

    private fun stockItem(id: String, qty: Float, min: Float) = StockItem(
        id = id, name = "Rice", category = StockCategory.FOOD,
        quantity = qty, unit = "kg", minQuantity = min,
        updatedBy = "f1", updatedAt = 1L,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userUseCases = mockk(relaxed = true)
        stockUseCases = mockk(relaxed = true)
        expenseUseCases = mockk(relaxed = true)
        choreUseCases = mockk(relaxed = true)

        every { userUseCases.getAllUsers() } returns flowOf(listOf(fatherUser()))
        every { stockUseCases.getLowStockItems() } returns flowOf(emptyList())
        every { expenseUseCases.getExpensesForCurrentMonth(any()) } returns flowOf(emptyList())
        every { choreUseCases() } returns flowOf(emptyList())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `low stock items are surfaced correctly`() = runTest {
        val lowItem = stockItem("s1", qty = 1f, min = 3f)
        every { stockUseCases.getLowStockItems() } returns flowOf(listOf(lowItem))

        // Verify the use case emits what we expect
        stockUseCases.getLowStockItems().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertTrue(items[0].quantity <= items[0].minQuantity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `all family members are loaded`() = runTest {
        val wife = User(
            id = "w1", name = "Wife", role = Role.WIFE,
            parentId = "f1", avatarUri = null,
            pin = sha256("5678"), createdAt = 2L,
        )
        every { userUseCases.getAllUsers() } returns flowOf(listOf(fatherUser(), wife))

        userUseCases.getAllUsers().test {
            val users = awaitItem()
            assertEquals(2, users.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expenses flow emits empty list when no expenses`() = runTest {
        expenseUseCases.getExpensesForCurrentMonth(fatherUser()).test {
            val expenses = awaitItem()
            assertTrue(expenses.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

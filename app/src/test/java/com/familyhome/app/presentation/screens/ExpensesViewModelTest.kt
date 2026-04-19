package com.familyhome.app.presentation.screens

import app.cash.turbine.test
import com.familyhome.app.domain.model.*
import com.familyhome.app.domain.usecase.expense.ExpenseUseCases
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
class ExpensesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var expenseUseCases: ExpenseUseCases

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fatherUser() = User(
        id = "f1", name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null,
        pin = sha256("1234"), createdAt = 1L,
    )

    private fun expense(id: String, amount: Long, category: ExpenseCategory) = Expense(
        id = id, amount = amount, currency = "IDR",
        category = category, description = "Test $id",
        paidBy = "f1", receiptUri = null,
        loggedAt = System.currentTimeMillis(),
        expenseDate = System.currentTimeMillis(),
        aiExtracted = false,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        expenseUseCases = mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `getExpensesForCurrentMonth returns all expenses for father`() = runTest {
        val expenses = listOf(
            expense("e1", 50_000L, ExpenseCategory.GROCERIES),
            expense("e2", 20_000L, ExpenseCategory.TRANSPORT),
        )
        every { expenseUseCases.getExpensesForCurrentMonth(any()) } returns flowOf(expenses)

        expenseUseCases.getExpensesForCurrentMonth(fatherUser()).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals(70_000L, result.sumOf { it.amount })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logExpense succeeds for father`() = runTest {
        val actor = fatherUser()
        val expectedExpense = expense("e1", 50_000L, ExpenseCategory.GROCERIES)
        every {
            expenseUseCases.logExpense(actor, 50_000L, "Groceries", ExpenseCategory.GROCERIES, null)
        } returns Result.success(expectedExpense)

        val result = expenseUseCases.logExpense(actor, 50_000L, "Groceries", ExpenseCategory.GROCERIES, null)
        assertTrue(result.isSuccess)
        assertEquals(50_000L, result.getOrNull()?.amount)
    }

    @Test
    fun `logExpense fails for kid`() = runTest {
        val kid = User(
            id = "k1", name = "Kid", role = Role.KID,
            parentId = "f1", avatarUri = null,
            pin = sha256("0000"), createdAt = 3L,
        )
        every {
            expenseUseCases.logExpense(kid, any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("Kids cannot log expenses."))

        val result = expenseUseCases.logExpense(kid, 10_000L, "Candy", ExpenseCategory.OTHER, null)
        assertTrue(result.isFailure)
        assertEquals("Kids cannot log expenses.", result.exceptionOrNull()?.message)
    }
}

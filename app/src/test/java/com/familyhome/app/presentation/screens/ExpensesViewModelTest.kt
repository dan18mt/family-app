package com.familyhome.app.presentation.screens

import app.cash.turbine.test
import com.familyhome.app.domain.model.*
import com.familyhome.app.domain.usecase.expense.GetExpensesUseCase
import com.familyhome.app.domain.usecase.expense.LogExpenseUseCase
import io.mockk.coEvery
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

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fatherUser() = User(
        id = "f1", name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null,
        pin = sha256("1234"), createdAt = 1L,
    )

    private fun kidUser() = User(
        id = "k1", name = "Kid", role = Role.KID,
        parentId = "f1", avatarUri = null,
        pin = sha256("0000"), createdAt = 3L,
    )

    private fun expense(id: String, amount: Long, category: ExpenseCategory) = Expense(
        id = id, amount = amount, currency = "IDR",
        category = category, description = "Test $id",
        paidBy = "f1", receiptUri = null,
        loggedAt = System.currentTimeMillis(),
        expenseDate = System.currentTimeMillis(),
        aiExtracted = false,
    )

    @BeforeEach fun setup() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `GetExpensesUseCase returns all expenses for father`() = runTest {
        val useCase = mockk<GetExpensesUseCase>()
        val expenses = listOf(
            expense("e1", 50_000L, ExpenseCategory.GROCERIES),
            expense("e2", 20_000L, ExpenseCategory.TRANSPORT),
        )
        every { useCase(fatherUser(), any()) } returns flowOf(expenses)

        useCase(fatherUser(), emptyList()).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals(70_000L, result.sumOf { it.amount })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LogExpenseUseCase succeeds for father`() = runTest {
        val useCase = mockk<LogExpenseUseCase>(relaxed = true)
        val actor = fatherUser()
        val expectedExpense = expense("e1", 50_000L, ExpenseCategory.GROCERIES)
        coEvery { useCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(expectedExpense)

        val result = useCase(actor, 50_000L, "IDR", ExpenseCategory.GROCERIES, "Groceries", "f1", null)
        assertTrue(result.isSuccess)
        assertEquals(50_000L, result.getOrNull()?.amount)
    }

    @Test
    fun `expenses list sums correctly`() = runTest {
        val useCase = mockk<GetExpensesUseCase>()
        val expenses = listOf(
            expense("e1", 100_000L, ExpenseCategory.GROCERIES),
            expense("e2", 50_000L, ExpenseCategory.HEALTH),
            expense("e3", 25_000L, ExpenseCategory.TRANSPORT),
        )
        every { useCase(fatherUser(), any()) } returns flowOf(expenses)

        useCase(fatherUser(), emptyList()).test {
            val result = awaitItem()
            assertEquals(175_000L, result.sumOf { it.amount })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

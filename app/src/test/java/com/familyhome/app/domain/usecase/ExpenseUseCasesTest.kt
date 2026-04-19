package com.familyhome.app.domain.usecase

import com.familyhome.app.data.sync.DeletionTracker
import com.familyhome.app.domain.model.Budget
import com.familyhome.app.domain.model.BudgetPeriod
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.BudgetRepository
import com.familyhome.app.domain.repository.ExpenseRepository
import com.familyhome.app.domain.usecase.expense.CheckBudgetAlertUseCase
import com.familyhome.app.domain.usecase.expense.DeleteBudgetUseCase
import com.familyhome.app.domain.usecase.expense.DeleteExpenseUseCase
import com.familyhome.app.domain.usecase.expense.GetAllBudgetsUseCase
import com.familyhome.app.domain.usecase.expense.GetExpenseSummaryUseCase
import com.familyhome.app.domain.usecase.expense.GetExpensesUseCase
import com.familyhome.app.domain.usecase.expense.LogExpenseUseCase
import com.familyhome.app.domain.usecase.expense.SetBudgetUseCase
import com.familyhome.app.domain.usecase.expense.UpdateExpenseUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
private fun user(id: String = "u1", role: Role = Role.FATHER) = User(
    id = id, name = "Test", role = role, parentId = null,
    avatarUri = null, createdAt = 1L,
)

private fun expense(
    id: String = "e1",
    paidBy: String = "u1",
    amount: Long = 10_000L,
    category: ExpenseCategory = ExpenseCategory.GROCERIES,
) = Expense(
    id = id, amount = amount, currency = "IDR", category = category,
    description = "desc", paidBy = paidBy, receiptUri = null,
    loggedAt = System.currentTimeMillis(), expenseDate = System.currentTimeMillis(),
    aiExtracted = false,
)

private fun budget(
    id: String = "b1",
    userId: String? = "u1",
    category: ExpenseCategory? = null,
    limit: Long = 100_000L,
    period: BudgetPeriod = BudgetPeriod.MONTHLY,
) = Budget(
    id = id, targetUserId = userId, category = category,
    limitAmount = limit, period = period, setBy = "f1",
)

class ExpenseUseCasesTest {

    private val expenseRepo   = mockk<ExpenseRepository>(relaxed = true)
    private val budgetRepo    = mockk<BudgetRepository>(relaxed = true)
    private val deletionTracker = mockk<DeletionTracker>(relaxed = true)

    @Nested
    inner class LogExpenseUseCaseTest {

        private val useCase = LogExpenseUseCase(expenseRepo)

        @Test
        fun `creates expense and returns success`() = runTest {
            val actor = user(role = Role.FATHER)
            coEvery { expenseRepo.insertExpense(any()) } returns Unit

            val result = useCase(
                actor          = actor,
                amount         = 50_000L,
                category       = ExpenseCategory.GROCERIES,
                description    = "Weekly shop",
                paidByUserId   = actor.id,
                receiptUri     = null,
            )

            assertTrue(result.isSuccess)
            assertEquals(50_000L, result.getOrThrow().amount)
            assertEquals(ExpenseCategory.GROCERIES, result.getOrThrow().category)
            coVerify { expenseRepo.insertExpense(any()) }
        }

        @Test
        fun `aiExtracted flag is stored correctly`() = runTest {
            coEvery { expenseRepo.insertExpense(any()) } returns Unit

            val result = useCase(
                actor = user(), amount = 1000L, category = ExpenseCategory.OTHER,
                description = "AI scan", paidByUserId = "u1",
                receiptUri = null, aiExtracted = true,
            )

            assertTrue(result.getOrThrow().aiExtracted)
        }
    }

    @Nested
    inner class GetExpensesUseCaseTest {

        private val useCase = GetExpensesUseCase(expenseRepo)

        @Test
        fun `FATHER gets all expenses`() {
            val father = user(role = Role.FATHER)
            every { expenseRepo.getAllExpenses() } returns flowOf(emptyList())

            useCase(father, emptyList())

            coVerify { expenseRepo.getAllExpenses() }
        }

        @Test
        fun `WIFE gets all expenses (filtered in ViewModel)`() {
            val wife = user(role = Role.WIFE)
            every { expenseRepo.getAllExpenses() } returns flowOf(emptyList())

            useCase(wife, emptyList())

            coVerify { expenseRepo.getAllExpenses() }
        }

        @Test
        fun `KID gets only own expenses`() {
            val kid = user("kid1", Role.KID)
            every { expenseRepo.getExpensesByUser("kid1") } returns flowOf(emptyList())

            useCase(kid, emptyList())

            coVerify { expenseRepo.getExpensesByUser("kid1") }
        }
    }

    @Nested
    inner class GetExpenseSummaryUseCaseTest {

        private val useCase = GetExpenseSummaryUseCase(expenseRepo)

        @Test
        fun `sums all expense amounts for user in current month`() = runTest {
            val expenses = listOf(
                expense(amount = 10_000L),
                expense(id = "e2", amount = 25_000L),
            )
            every { expenseRepo.getExpensesByUserInRange(any(), any(), any()) } returns flowOf(expenses)

            val total = useCase("u1")

            assertEquals(35_000L, total)
        }

        @Test
        fun `returns 0 when no expenses in current month`() = runTest {
            every { expenseRepo.getExpensesByUserInRange(any(), any(), any()) } returns flowOf(emptyList())

            val total = useCase("u1")

            assertEquals(0L, total)
        }
    }

    @Nested
    inner class SetBudgetUseCaseTest {

        private val useCase = SetBudgetUseCase(budgetRepo)

        @Test
        fun `FATHER can set budget`() = runTest {
            val father = user(role = Role.FATHER)
            coEvery { budgetRepo.upsertBudget(any()) } returns Unit

            val result = useCase(father, "u1", null, 500_000L, BudgetPeriod.MONTHLY)

            assertTrue(result.isSuccess)
            assertEquals(500_000L, result.getOrThrow().limitAmount)
            assertEquals(father.id, result.getOrThrow().setBy)
        }

        @Test
        fun `WIFE cannot set budget`() = runTest {
            val wife = user(role = Role.WIFE)

            val result = useCase(wife, "u1", null, 200_000L, BudgetPeriod.MONTHLY)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("Only Father"))
        }

        @Test
        fun `KID cannot set budget`() = runTest {
            val kid = user(role = Role.KID)

            val result = useCase(kid, "u1", null, 100_000L, BudgetPeriod.WEEKLY)

            assertTrue(result.isFailure)
        }
    }

    @Nested
    inner class CheckBudgetAlertUseCaseTest {

        private val useCase = CheckBudgetAlertUseCase(expenseRepo, budgetRepo)

        @Test
        fun `returns warning when usage ratio is 80 percent or above`() = runTest {
            val b = budget(limit = 100_000L)
            // 80 000 spent → ratio 0.8 → isWarning true
            val expenses = listOf(expense(amount = 80_000L))
            every { budgetRepo.getBudgetForUser("u1") } returns flowOf(listOf(b))
            every { expenseRepo.getExpensesByUserInRange(any(), any(), any()) } returns flowOf(expenses)

            val alerts = useCase("u1")

            assertEquals(1, alerts.size)
            assertTrue(alerts[0].isWarning)
            assertEquals(80_000L, alerts[0].spent)
        }

        @Test
        fun `returns no warning when under 80 percent`() = runTest {
            val b = budget(limit = 100_000L)
            val expenses = listOf(expense(amount = 50_000L))
            every { budgetRepo.getBudgetForUser("u1") } returns flowOf(listOf(b))
            every { expenseRepo.getExpensesByUserInRange(any(), any(), any()) } returns flowOf(expenses)

            val alerts = useCase("u1")

            assertEquals(1, alerts.size)
            assertTrue(!alerts[0].isWarning)
        }

        @Test
        fun `returns empty list when no budgets`() = runTest {
            every { budgetRepo.getBudgetForUser("u1") } returns flowOf(emptyList())

            val alerts = useCase("u1")

            assertTrue(alerts.isEmpty())
        }

        @Test
        fun `category-specific budget filters expenses by category`() = runTest {
            val b = budget(limit = 100_000L, category = ExpenseCategory.GROCERIES)
            val groceries = expense(amount = 90_000L, category = ExpenseCategory.GROCERIES)
            val transport = expense(id = "e2", amount = 50_000L, category = ExpenseCategory.TRANSPORT)
            every { budgetRepo.getBudgetForUser("u1") } returns flowOf(listOf(b))
            every { expenseRepo.getExpensesByUserInRange(any(), any(), any()) } returns flowOf(listOf(groceries, transport))

            val alerts = useCase("u1")

            // Only groceries should count: 90k/100k = 0.9 → warning
            assertEquals(90_000L, alerts[0].spent)
            assertTrue(alerts[0].isWarning)
        }
    }

    @Nested
    inner class UpdateExpenseUseCaseTest {

        private val useCase = UpdateExpenseUseCase(expenseRepo)

        @Test
        fun `delegates to repository and returns success`() = runTest {
            val exp = expense()
            coEvery { expenseRepo.updateExpense(exp) } returns Unit

            val result = useCase(user(), exp)

            assertTrue(result.isSuccess)
            coVerify { expenseRepo.updateExpense(exp) }
        }
    }

    @Nested
    inner class DeleteExpenseUseCaseTest {

        private val useCase = DeleteExpenseUseCase(expenseRepo)

        @Test
        fun `delegates to repository and returns success`() = runTest {
            coEvery { expenseRepo.deleteExpense("e1") } returns Unit

            val result = useCase(user(), "e1")

            assertTrue(result.isSuccess)
            coVerify { expenseRepo.deleteExpense("e1") }
        }
    }

    @Nested
    inner class GetAllBudgetsUseCaseTest {

        private val useCase = GetAllBudgetsUseCase(budgetRepo)

        @Test
        fun `delegates to budget repository getAllBudgets`() {
            every { budgetRepo.getAllBudgets() } returns flowOf(emptyList())

            useCase()

            coVerify { budgetRepo.getAllBudgets() }
        }
    }

    @Nested
    inner class DeleteBudgetUseCaseTest {

        private val useCase = DeleteBudgetUseCase(budgetRepo, deletionTracker)

        @Test
        fun `FATHER can delete budget and tracks deletion`() = runTest {
            val father = user(role = Role.FATHER)
            coEvery { budgetRepo.deleteBudget("b1") } returns Unit
            coEvery { deletionTracker.recordBudgetDeletion("b1") } returns Unit

            val result = useCase(father, "b1")

            assertTrue(result.isSuccess)
            coVerify { budgetRepo.deleteBudget("b1") }
            coVerify { deletionTracker.recordBudgetDeletion("b1") }
        }

        @Test
        fun `WIFE cannot delete budget`() = runTest {
            val wife = user(role = Role.WIFE)

            val result = useCase(wife, "b1")

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { budgetRepo.deleteBudget(any()) }
        }

        @Test
        fun `KID cannot delete budget`() = runTest {
            val kid = user(role = Role.KID)

            assertTrue(useCase(kid, "b1").isFailure)
        }
    }
}

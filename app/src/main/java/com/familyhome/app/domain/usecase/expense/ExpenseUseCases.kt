package com.familyhome.app.domain.usecase.expense

import com.familyhome.app.domain.model.Budget
import com.familyhome.app.domain.model.BudgetPeriod
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.permission.PermissionManager
import com.familyhome.app.domain.repository.BudgetRepository
import com.familyhome.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

class LogExpenseUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) {
    suspend operator fun invoke(
        actor: User,
        amount: Long,
        currency: String = "IDR",
        category: ExpenseCategory,
        description: String,
        paidByUserId: String,
        receiptUri: String?,
        expenseDate: Long = System.currentTimeMillis(),
        aiExtracted: Boolean = false,
    ): Result<Expense> {
        val expense = Expense(
            id          = UUID.randomUUID().toString(),
            amount      = amount,
            currency    = currency,
            category    = category,
            description = description,
            paidBy      = paidByUserId,
            receiptUri  = receiptUri,
            loggedAt    = System.currentTimeMillis(),
            expenseDate = expenseDate,
            aiExtracted = aiExtracted,
        )
        expenseRepository.insertExpense(expense)
        return Result.success(expense)
    }
}

class GetExpensesUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) {
    operator fun invoke(actor: User, allUsers: List<User>): Flow<List<Expense>> {
        return when (actor.role.name) {
            "FATHER" -> expenseRepository.getAllExpenses()
            "WIFE"   -> {
                // Wife sees her own + all kids' — filtered in the ViewModel after collection
                expenseRepository.getAllExpenses()
            }
            else     -> expenseRepository.getExpensesByUser(actor.id)
        }
    }
}

class GetExpenseSummaryUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) {
    /** Returns total spent (in cents) for the given user within the current calendar month. */
    suspend operator fun invoke(userId: String): Long {
        val (start, end) = currentMonthRange()
        return expenseRepository
            .getExpensesByUserInRange(userId, start, end)
            .first()
            .sumOf { it.amount }
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }
}

class SetBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(
        actor: User,
        targetUserId: String?,
        category: ExpenseCategory?,
        limitAmount: Long,
        period: BudgetPeriod,
    ): Result<Budget> {
        if (!PermissionManager.canSetBudget(actor)) {
            return Result.failure(IllegalStateException("Only Father can set budgets."))
        }
        val budget = Budget(
            id           = UUID.randomUUID().toString(),
            targetUserId = targetUserId,
            category     = category,
            limitAmount  = limitAmount,
            period       = period,
            setBy        = actor.id,
        )
        budgetRepository.upsertBudget(budget)
        return Result.success(budget)
    }
}

/** Returns how much of the budget has been used (0.0 – 1.0+). >0.8 triggers a warning. */
class CheckBudgetAlertUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
) {
    data class BudgetAlert(
        val budget: Budget,
        val spent: Long,
        val usageRatio: Float,
        val isWarning: Boolean,
    )

    suspend operator fun invoke(userId: String): List<BudgetAlert> {
        val budgets = budgetRepository.getBudgetForUser(userId).first()
        val (start, end) = currentPeriodRange(BudgetPeriod.MONTHLY)

        return budgets.map { budget ->
            val expenses = expenseRepository
                .getExpensesByUserInRange(userId, start, end)
                .first()
                .filter { budget.category == null || it.category == budget.category }
            val spent = expenses.sumOf { it.amount }
            val ratio = spent.toFloat() / budget.limitAmount.coerceAtLeast(1)
            BudgetAlert(budget, spent, ratio, ratio >= 0.8f)
        }
    }

    private fun currentPeriodRange(period: BudgetPeriod): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        when (period) {
            BudgetPeriod.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                return start to cal.timeInMillis
            }
            BudgetPeriod.WEEKLY -> {
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                return start to cal.timeInMillis
            }
        }
    }
}

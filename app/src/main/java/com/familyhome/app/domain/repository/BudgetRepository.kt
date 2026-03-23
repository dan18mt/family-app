package com.familyhome.app.domain.repository

import com.familyhome.app.domain.model.Budget
import com.familyhome.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getAllBudgets(): Flow<List<Budget>>
    fun getBudgetForUser(userId: String): Flow<List<Budget>>
    fun getBudgetForCategory(category: ExpenseCategory): Flow<List<Budget>>
    suspend fun upsertBudget(budget: Budget)
    suspend fun deleteBudget(id: String)
    suspend fun upsertAll(budgets: List<Budget>)
}

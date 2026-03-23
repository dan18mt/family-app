package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.BudgetDao
import com.familyhome.app.data.mapper.toDomain
import com.familyhome.app.data.mapper.toEntity
import com.familyhome.app.domain.model.Budget
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
) : BudgetRepository {

    override fun getAllBudgets(): Flow<List<Budget>> =
        dao.getAllBudgets().map { list -> list.map { it.toDomain() } }

    override fun getBudgetForUser(userId: String): Flow<List<Budget>> =
        dao.getBudgetForUser(userId).map { list -> list.map { it.toDomain() } }

    override fun getBudgetForCategory(category: ExpenseCategory): Flow<List<Budget>> =
        dao.getBudgetForCategory(category.name).map { list -> list.map { it.toDomain() } }

    override suspend fun upsertBudget(budget: Budget) =
        dao.upsertBudget(budget.toEntity())

    override suspend fun deleteBudget(id: String) =
        dao.deleteBudget(id)

    override suspend fun upsertAll(budgets: List<Budget>) =
        dao.upsertAll(budgets.map { it.toEntity() })
}

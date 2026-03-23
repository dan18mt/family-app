package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.ExpenseDao
import com.familyhome.app.data.mapper.toDomain
import com.familyhome.app.data.mapper.toEntity
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<Expense>> =
        dao.getAllExpenses().map { list -> list.map { it.toDomain() } }

    override fun getExpensesByUser(userId: String): Flow<List<Expense>> =
        dao.getExpensesByUser(userId).map { list -> list.map { it.toDomain() } }

    override fun getExpensesByCategory(category: ExpenseCategory): Flow<List<Expense>> =
        dao.getExpensesByCategory(category.name).map { list -> list.map { it.toDomain() } }

    override fun getExpensesInRange(fromTimestamp: Long, toTimestamp: Long): Flow<List<Expense>> =
        dao.getExpensesInRange(fromTimestamp, toTimestamp).map { list -> list.map { it.toDomain() } }

    override fun getExpensesByUserInRange(
        userId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
    ): Flow<List<Expense>> =
        dao.getExpensesByUserInRange(userId, fromTimestamp, toTimestamp)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun insertExpense(expense: Expense) =
        dao.insertExpense(expense.toEntity())

    override suspend fun updateExpense(expense: Expense) =
        dao.updateExpense(expense.toEntity())

    override suspend fun deleteExpense(id: String) =
        dao.deleteExpense(id)

    override suspend fun upsertAll(expenses: List<Expense>) =
        dao.upsertAll(expenses.map { it.toEntity() })
}

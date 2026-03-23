package com.familyhome.app.domain.repository

import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun getExpensesByUser(userId: String): Flow<List<Expense>>
    fun getExpensesByCategory(category: ExpenseCategory): Flow<List<Expense>>
    fun getExpensesInRange(fromTimestamp: Long, toTimestamp: Long): Flow<List<Expense>>
    fun getExpensesByUserInRange(
        userId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
    ): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(id: String)
    suspend fun upsertAll(expenses: List<Expense>)
}

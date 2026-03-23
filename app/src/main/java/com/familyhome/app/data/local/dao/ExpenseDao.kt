package com.familyhome.app.data.local.dao

import androidx.room.*
import com.familyhome.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE paidBy = :userId ORDER BY expenseDate DESC")
    fun getExpensesByUser(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY expenseDate DESC")
    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT * FROM expenses
        WHERE expenseDate >= :from AND expenseDate < :to
        ORDER BY expenseDate DESC
    """)
    fun getExpensesInRange(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT * FROM expenses
        WHERE paidBy = :userId AND expenseDate >= :from AND expenseDate < :to
        ORDER BY expenseDate DESC
    """)
    fun getExpensesByUserInRange(userId: String, from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)
}

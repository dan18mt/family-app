package com.familyhome.app.data.local.dao

import androidx.room.*
import com.familyhome.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE targetUserId = :userId")
    fun getBudgetForUser(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category = :category")
    fun getBudgetForCategory(category: String): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(budgets: List<BudgetEntity>)
}

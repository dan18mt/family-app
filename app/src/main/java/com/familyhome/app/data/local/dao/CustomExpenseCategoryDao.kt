package com.familyhome.app.data.local.dao

import androidx.room.*
import com.familyhome.app.data.local.entity.CustomExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomExpenseCategoryDao {

    @Query("SELECT * FROM custom_expense_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CustomExpenseCategoryEntity>>

    @Query("SELECT * FROM custom_expense_categories WHERE id = :id")
    suspend fun getById(id: String): CustomExpenseCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CustomExpenseCategoryEntity)

    @Update
    suspend fun updateCategory(category: CustomExpenseCategoryEntity)

    @Query("DELETE FROM custom_expense_categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CustomExpenseCategoryEntity>)
}

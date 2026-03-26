package com.familyhome.app.domain.repository

import com.familyhome.app.domain.model.CustomExpenseCategory
import kotlinx.coroutines.flow.Flow

interface CustomExpenseCategoryRepository {
    fun getAllCategories(): Flow<List<CustomExpenseCategory>>
    suspend fun insertCategory(category: CustomExpenseCategory)
    suspend fun updateCategory(category: CustomExpenseCategory)
    suspend fun deleteCategory(id: String)
}

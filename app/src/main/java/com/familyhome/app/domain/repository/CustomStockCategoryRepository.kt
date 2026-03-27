package com.familyhome.app.domain.repository

import com.familyhome.app.domain.model.CustomStockCategory
import kotlinx.coroutines.flow.Flow

interface CustomStockCategoryRepository {
    fun getAllCategories(): Flow<List<CustomStockCategory>>
    suspend fun insertCategory(category: CustomStockCategory)
    suspend fun updateCategory(category: CustomStockCategory)
    suspend fun deleteCategory(id: String)
    suspend fun upsertAll(categories: List<CustomStockCategory>)
}

package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.CustomExpenseCategoryDao
import com.familyhome.app.data.mapper.toDomain
import com.familyhome.app.data.mapper.toEntity
import com.familyhome.app.domain.model.CustomExpenseCategory
import com.familyhome.app.domain.repository.CustomExpenseCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CustomExpenseCategoryRepositoryImpl @Inject constructor(
    private val dao: CustomExpenseCategoryDao,
) : CustomExpenseCategoryRepository {

    override fun getAllCategories(): Flow<List<CustomExpenseCategory>> =
        dao.getAllCategories().map { it.map { entity -> entity.toDomain() } }

    override suspend fun insertCategory(category: CustomExpenseCategory) =
        dao.insertCategory(category.toEntity())

    override suspend fun updateCategory(category: CustomExpenseCategory) =
        dao.updateCategory(category.toEntity())

    override suspend fun deleteCategory(id: String) =
        dao.deleteCategory(id)
}

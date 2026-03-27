package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.CustomStockCategoryDao
import com.familyhome.app.data.local.entity.CustomStockCategoryEntity
import com.familyhome.app.domain.model.CustomStockCategory
import com.familyhome.app.domain.repository.CustomStockCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CustomStockCategoryRepositoryImpl @Inject constructor(
    private val dao: CustomStockCategoryDao,
) : CustomStockCategoryRepository {

    override fun getAllCategories(): Flow<List<CustomStockCategory>> =
        dao.getAllCategories().map { list -> list.map { CustomStockCategory(it.id, it.name, it.iconName) } }

    override suspend fun insertCategory(category: CustomStockCategory) =
        dao.insertCategory(CustomStockCategoryEntity(category.id, category.name, category.iconName))

    override suspend fun updateCategory(category: CustomStockCategory) =
        dao.updateCategory(CustomStockCategoryEntity(category.id, category.name, category.iconName))

    override suspend fun deleteCategory(id: String) =
        dao.deleteCategory(id)

    override suspend fun upsertAll(categories: List<CustomStockCategory>) =
        dao.upsertAll(categories.map { CustomStockCategoryEntity(it.id, it.name, it.iconName) })
}

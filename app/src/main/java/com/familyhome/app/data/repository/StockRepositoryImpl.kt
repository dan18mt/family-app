package com.familyhome.app.data.repository

import com.familyhome.app.data.local.dao.StockItemDao
import com.familyhome.app.data.mapper.toDomain
import com.familyhome.app.data.mapper.toEntity
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.repository.StockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StockRepositoryImpl @Inject constructor(
    private val dao: StockItemDao,
) : StockRepository {

    override fun getAllItems(): Flow<List<StockItem>> =
        dao.getAllItems().map { list -> list.map { it.toDomain() } }

    override fun getItemsByCategory(category: StockCategory): Flow<List<StockItem>> =
        dao.getItemsByCategory(category.name).map { list -> list.map { it.toDomain() } }

    override fun getLowStockItems(): Flow<List<StockItem>> =
        dao.getLowStockItems().map { list -> list.map { it.toDomain() } }

    override suspend fun getItemById(id: String): StockItem? =
        dao.getItemById(id)?.toDomain()

    override suspend fun insertItem(item: StockItem) =
        dao.insertItem(item.toEntity())

    override suspend fun updateItem(item: StockItem) =
        dao.updateItem(item.toEntity())

    override suspend fun deleteItem(id: String) =
        dao.deleteItem(id)

    override suspend fun upsertAll(items: List<StockItem>) =
        dao.upsertAll(items.map { it.toEntity() })
}

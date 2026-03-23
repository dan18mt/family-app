package com.familyhome.app.domain.repository

import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import kotlinx.coroutines.flow.Flow

interface StockRepository {
    fun getAllItems(): Flow<List<StockItem>>
    fun getItemsByCategory(category: StockCategory): Flow<List<StockItem>>
    fun getLowStockItems(): Flow<List<StockItem>>
    suspend fun getItemById(id: String): StockItem?
    suspend fun insertItem(item: StockItem)
    suspend fun updateItem(item: StockItem)
    suspend fun deleteItem(id: String)
    suspend fun upsertAll(items: List<StockItem>)
}

package com.familyhome.app.data.local.dao

import androidx.room.*
import com.familyhome.app.data.local.entity.StockItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockItemDao {
    @Query("SELECT * FROM stock_items ORDER BY name ASC")
    fun getAllItems(): Flow<List<StockItemEntity>>

    @Query("SELECT * FROM stock_items WHERE category = :category ORDER BY name ASC")
    fun getItemsByCategory(category: String): Flow<List<StockItemEntity>>

    @Query("SELECT * FROM stock_items WHERE quantity <= minQuantity ORDER BY name ASC")
    fun getLowStockItems(): Flow<List<StockItemEntity>>

    @Query("SELECT * FROM stock_items WHERE id = :id")
    suspend fun getItemById(id: String): StockItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: StockItemEntity)

    @Update
    suspend fun updateItem(item: StockItemEntity)

    @Query("DELETE FROM stock_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StockItemEntity>)
}

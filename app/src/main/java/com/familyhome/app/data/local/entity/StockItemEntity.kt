package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_items")
data class StockItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val quantity: Float,
    val unit: String,
    val minQuantity: Float,
    val updatedBy: String,
    val updatedAt: Long,
    val customCategoryId: String? = null,
)

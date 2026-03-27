package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_stock_categories")
data class CustomStockCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconName: String,
)

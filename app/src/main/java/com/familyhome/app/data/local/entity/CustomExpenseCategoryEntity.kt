package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_expense_categories")
data class CustomExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconName: String,
)

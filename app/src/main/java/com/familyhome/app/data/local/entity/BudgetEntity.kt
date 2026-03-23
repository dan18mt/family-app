package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val targetUserId: String?,
    val category: String?,
    val limitAmount: Long,
    val period: String,
    val setBy: String,
)

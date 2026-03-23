package com.familyhome.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amount: Long,
    val currency: String,
    val category: String,
    val description: String,
    val paidBy: String,
    val receiptUri: String?,
    val loggedAt: Long,
    val expenseDate: Long,
    val aiExtracted: Boolean,
)

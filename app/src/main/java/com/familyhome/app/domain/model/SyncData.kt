package com.familyhome.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Full-snapshot payload exchanged between sync host and clients.
 * All fields are nullable so a partial push is possible (only changed collections).
 */
@Serializable
data class SyncPayload(
    val users: List<UserDto>? = null,
    val stockItems: List<StockItemDto>? = null,
    val choreLogs: List<ChoreLogDto>? = null,
    val recurringTasks: List<RecurringTaskDto>? = null,
    val expenses: List<ExpenseDto>? = null,
    val budgets: List<BudgetDto>? = null,
    val snapshotAt: Long = System.currentTimeMillis(),
)

@Serializable data class UserDto(
    val id: String, val name: String, val role: String,
    val parentId: String?, val avatarUri: String?,
    val pin: String, val createdAt: Long,
)

@Serializable data class StockItemDto(
    val id: String, val name: String, val category: String,
    val quantity: Float, val unit: String, val minQuantity: Float,
    val updatedBy: String, val updatedAt: Long,
)

@Serializable data class ChoreLogDto(
    val id: String, val taskName: String, val doneBy: String,
    val doneAt: Long, val note: String?,
)

@Serializable data class RecurringTaskDto(
    val id: String, val taskName: String, val frequency: String,
    val assignedTo: String?, val lastDoneAt: Long?, val nextDueAt: Long,
)

@Serializable data class ExpenseDto(
    val id: String, val amount: Long, val currency: String,
    val category: String, val description: String, val paidBy: String,
    val receiptUri: String?, val loggedAt: Long, val expenseDate: Long,
    val aiExtracted: Boolean,
)

@Serializable data class BudgetDto(
    val id: String, val targetUserId: String?, val category: String?,
    val limitAmount: Long, val period: String, val setBy: String,
)

sealed class SyncResult {
    data class Success(val syncedAt: Long) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

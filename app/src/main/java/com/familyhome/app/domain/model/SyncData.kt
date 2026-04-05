package com.familyhome.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncPayload(
    val users: List<UserDto>? = null,
    val stockItems: List<StockItemDto>? = null,
    val choreLogs: List<ChoreLogDto>? = null,
    val recurringTasks: List<RecurringTaskDto>? = null,
    val choreAssignments: List<ChoreAssignmentDto>? = null,
    val expenses: List<ExpenseDto>? = null,
    val budgets: List<BudgetDto>? = null,
    val customStockCategories: List<CustomStockCategoryDto>? = null,
    val customExpenseCategories: List<CustomExpenseCategoryDto>? = null,
    val prayerGoalSettings: List<PrayerGoalSettingDto>? = null,
    val prayerLogs: List<PrayerLogDto>? = null,
    /** ID of the user who initiated this push; null when coming from the server. */
    val pusherId: String? = null,
    val snapshotAt: Long = System.currentTimeMillis(),
    /** User IDs that have been deleted by the leader; members must remove these locally. */
    val deletedUserIds: List<String>? = null,
    /** Prayer-goal IDs deleted by the leader; members must remove these locally. */
    val deletedPrayerGoalIds: List<String>? = null,
    /** userId → last-seen epoch-ms map; used to propagate presence to all devices. */
    val presenceMap: Map<String, Long>? = null,
    /** ID of the family leader (Father); included in pull responses. */
    val leaderId: String? = null,
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
    val customCategoryId: String? = null,
)

@Serializable data class CustomStockCategoryDto(
    val id: String, val name: String, val iconName: String,
)

@Serializable data class CustomExpenseCategoryDto(
    val id: String, val name: String, val iconName: String,
)

@Serializable data class ChoreLogDto(
    val id: String, val taskName: String, val doneBy: String,
    val doneAt: Long, val note: String?,
)

@Serializable data class RecurringTaskDto(
    val id: String, val taskName: String, val frequency: String,
    val assignedTo: String?, val lastDoneAt: Long?, val nextDueAt: Long,
    val scheduledAt: Long? = null,
    val reminderMinutesBefore: Int? = null,
)

@Serializable data class ExpenseDto(
    val id: String, val amount: Long, val currency: String,
    val category: String, val description: String, val paidBy: String,
    val receiptUri: String?, val loggedAt: Long, val expenseDate: Long,
    val aiExtracted: Boolean,
    val customCategoryId: String? = null,
)

@Serializable data class ChoreAssignmentDto(
    val id: String, val taskId: String, val taskName: String,
    val assignedTo: String, val assignedBy: String, val status: String,
    val declineReason: String?, val assignedAt: Long, val respondedAt: Long?,
)

@Serializable data class BudgetDto(
    val id: String, val targetUserId: String?, val category: String?,
    val limitAmount: Long, val period: String, val setBy: String,
)

@Serializable data class PrayerGoalSettingDto(
    val id: String, val sunnahKey: String, val isEnabled: Boolean,
    /** Comma-separated user IDs, or null for "all family". Backward-compatible with single IDs. */
    val assignedTo: String?, val createdBy: String, val createdAt: Long,
    val reminderEnabled: Boolean = false,
)

@Serializable data class PrayerLogDto(
    val id: String, val userId: String, val sunnahKey: String,
    val epochDay: Long, val completedCount: Int, val loggedAt: Long,
)

sealed class SyncResult {
    data class Success(val syncedAt: Long) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

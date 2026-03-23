package com.familyhome.app.domain.model

data class Budget(
    val id: String,
    /** null = applies to the whole family */
    val targetUserId: String?,
    /** null = applies across all categories */
    val category: ExpenseCategory?,
    /** Limit amount in IDR cents */
    val limitAmount: Long,
    val period: BudgetPeriod,
    val setBy: String,
)

enum class BudgetPeriod {
    MONTHLY,
    WEEKLY;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

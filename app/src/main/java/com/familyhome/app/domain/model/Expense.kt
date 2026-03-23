package com.familyhome.app.domain.model

data class Expense(
    val id: String,
    /** Amount in IDR cents (e.g. 15000 = Rp 150.00) */
    val amount: Long,
    val currency: String,
    val category: ExpenseCategory,
    val description: String,
    val paidBy: String,
    /** Local file URI for the receipt photo */
    val receiptUri: String?,
    val loggedAt: Long,
    val expenseDate: Long,
    /** True when the AI agent auto-extracted and logged this expense */
    val aiExtracted: Boolean,
)

enum class ExpenseCategory {
    GROCERIES,
    TRANSPORT,
    SCHOOL,
    HEALTH,
    ENTERTAINMENT,
    HOUSEHOLD,
    OTHER;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

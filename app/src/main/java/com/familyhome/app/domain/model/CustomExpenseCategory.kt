package com.familyhome.app.domain.model

/**
 * A user-defined expense category stored in Room.
 * Built-in categories are represented by [ExpenseCategory]; custom ones live here.
 *
 * [iconName] is the name of a Material Icons vector — e.g. "ShoppingCart", "DirectionsCar".
 * The UI maps this name back to the actual [ImageVector] at render time.
 */
data class CustomExpenseCategory(
    val id: String,
    val name: String,
    val iconName: String,
)

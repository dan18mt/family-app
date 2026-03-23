package com.familyhome.app.presentation.navigation

/** Sealed class that enumerates every destination in the app. */
sealed class Screen(val route: String) {
    // Auth flow
    data object Setup : Screen("setup")
    data object Login : Screen("login")

    // Main tabs
    data object Home     : Screen("home")
    data object Stock    : Screen("stock")
    data object Chores   : Screen("chores")
    data object Expenses : Screen("expenses")
    data object Chat     : Screen("chat")

    // Detail / secondary
    data object AddStockItem  : Screen("stock/add")
    data object AddChore      : Screen("chores/add")
    data object AddExpense    : Screen("expenses/add")
    data object ManageMembers : Screen("settings/members")
    data object SyncSettings  : Screen("settings/sync")
}

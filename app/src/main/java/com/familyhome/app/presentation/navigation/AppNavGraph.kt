package com.familyhome.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.familyhome.app.presentation.screens.chat.ChatScreen
import com.familyhome.app.presentation.screens.chores.ChoresScreen
import com.familyhome.app.presentation.screens.expenses.ExpensesScreen
import com.familyhome.app.presentation.screens.home.HomeScreen
import com.familyhome.app.presentation.screens.login.LoginScreen
import com.familyhome.app.presentation.screens.login.LoginViewModel
import com.familyhome.app.presentation.screens.setup.SetupScreen
import com.familyhome.app.presentation.screens.stock.StockScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
    ) {
        // ── Auth ──────────────────────────────────────────────────────────────
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = hiltViewModel()
            val users by viewModel.users.collectAsStateWithLifecycle()
            LoginScreen(
                users          = users,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                viewModel = viewModel,
            )
        }

        // ── Main screens ──────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateTo      = { navController.navigate(it.route) },
                onNavigateToSync  = { navController.navigate(Screen.SyncSettings.route) },
            )
        }

        composable(Screen.Stock.route) {
            StockScreen(
                onAddItem = { navController.navigate(Screen.AddStockItem.route) }
            )
        }

        composable(Screen.Chores.route) {
            ChoresScreen(
                onAddChore = { navController.navigate(Screen.AddChore.route) }
            )
        }

        composable(Screen.Expenses.route) {
            ExpensesScreen(
                onAddExpense = { navController.navigate(Screen.AddExpense.route) }
            )
        }

        composable(Screen.Chat.route) {
            ChatScreen()
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(Screen.SyncSettings.route) {
            com.familyhome.app.presentation.screens.sync.SyncSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

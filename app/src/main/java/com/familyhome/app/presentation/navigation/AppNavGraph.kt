package com.familyhome.app.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import com.familyhome.app.presentation.screens.notifications.NotificationsScreen
import com.familyhome.app.presentation.screens.onboarding.FatherOnboardingScreen
import com.familyhome.app.presentation.screens.onboarding.MemberOnboardingScreen
import com.familyhome.app.presentation.screens.setup.SetupScreen
import com.familyhome.app.presentation.screens.stock.StockScreen
import com.familyhome.app.presentation.screens.tutorial.TutorialScreen

private const val ANIM_DURATION = 300

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        enterTransition  = {
            slideInHorizontally(tween(ANIM_DURATION)) { it } + fadeIn(tween(ANIM_DURATION))
        },
        exitTransition   = {
            slideOutHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeOut(tween(ANIM_DURATION))
        },
        popEnterTransition  = {
            slideInHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeIn(tween(ANIM_DURATION))
        },
        popExitTransition   = {
            slideOutHorizontally(tween(ANIM_DURATION)) { it } + fadeOut(tween(ANIM_DURATION))
        },
    ) {
        // ── Auth / Onboarding ──────────────────────────────────────────────────
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.FatherOnboarding.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                onJoinFamily = {
                    navController.navigate(Screen.MemberOnboarding.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.FatherOnboarding.route) {
            FatherOnboardingScreen(
                onDone = {
                    navController.navigate(Screen.Tutorial.route) {
                        popUpTo(Screen.FatherOnboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Same screen reached from Home → just pop back when done instead of showing Tutorial
        composable(Screen.ManageMembers.route) {
            FatherOnboardingScreen(
                onDone = { navController.popBackStack() }
            )
        }

        composable(Screen.MemberOnboarding.route) {
            MemberOnboardingScreen(
                onDone = {
                    navController.navigate(Screen.Tutorial.route) {
                        popUpTo(Screen.MemberOnboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route           = Screen.Tutorial.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition  = { fadeOut(tween(300)) },
        ) {
            TutorialScreen(
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Tutorial.route) { inclusive = true }
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
                onNavigateTo     = { navController.navigate(it.route) },
                onNavigateToSync = { navController.navigate(Screen.SyncSettings.route) },
            )
        }

        composable(Screen.Stock.route) {
            StockScreen(onAddItem = { navController.navigate(Screen.AddStockItem.route) })
        }

        composable(Screen.Chores.route) {
            ChoresScreen(onAddChore = { navController.navigate(Screen.AddChore.route) })
        }

        composable(Screen.Expenses.route) {
            ExpensesScreen(onAddExpense = { navController.navigate(Screen.AddExpense.route) })
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

        composable(Screen.Notifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
    }
}

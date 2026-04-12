package com.familyhome.app.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.familyhome.app.presentation.components.FamilyBottomBar
import com.familyhome.app.presentation.screens.chat.ChatScreen
import com.familyhome.app.presentation.screens.chores.ChoresScreen
import com.familyhome.app.presentation.screens.prayer.PrayerScreen
import com.familyhome.app.presentation.screens.expenses.ExpensesScreen
import com.familyhome.app.presentation.screens.home.HomeScreen
import com.familyhome.app.presentation.screens.login.LoginScreen
import com.familyhome.app.presentation.screens.login.LoginViewModel
import com.familyhome.app.presentation.screens.notifications.NotificationsScreen
import com.familyhome.app.presentation.screens.onboarding.FatherOnboardingScreen
import com.familyhome.app.presentation.screens.onboarding.MemberOnboardingScreen
import com.familyhome.app.presentation.screens.setup.SetupScreen
import com.familyhome.app.presentation.screens.stock.AddStockItemScreen
import com.familyhome.app.presentation.screens.stock.StockScreen
import com.familyhome.app.presentation.screens.tutorial.TutorialScreen

private const val ANIM_DURATION = 300

private val MAIN_TAB_ROUTES = setOf(
    Screen.Home.route,
    Screen.Stock.route,
    Screen.Chores.route,
    Screen.Expenses.route,
    Screen.Prayer.route,
)

@Composable
fun AppNavGraph(
    navController: androidx.navigation.NavHostController,
    startDestination: String,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Tab-switching navigation: restore state and avoid duplicate back stack entries
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in MAIN_TAB_ROUTES) {
                FamilyBottomBar(
                    currentRoute = currentRoute,
                    onNavigate   = navigateToTab,
                )
            }
        },
        // Let each inner Scaffold handle its own window insets (status bar, etc.)
        // The outer Scaffold only provides bottom padding for the nav bar.
    ) { innerPadding ->
        NavHost(
            // Apply only the bottom padding so each screen's TopAppBar still handles status bar insets
            modifier         = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
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

            // ── Main tabs ─────────────────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateTo    = { navController.navigate(it.route) },
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Home.route,
                )
            }

            composable(Screen.Stock.route) {
                StockScreen(
                    onAddItem       = { navController.navigate(Screen.AddStockItem.route) },
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Stock.route,
                )
            }

            composable(Screen.AddStockItem.route) {
                AddStockItemScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Chores.route) {
                ChoresScreen(
                    onAddChore      = { navController.navigate(Screen.AddChore.route) },
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Chores.route,
                )
            }

            composable(Screen.Expenses.route) {
                ExpensesScreen(
                    onAddExpense    = { navController.navigate(Screen.AddExpense.route) },
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Expenses.route,
                )
            }

            composable(Screen.Prayer.route) {
                PrayerScreen(
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Prayer.route,
                )
            }

            composable(Screen.Chat.route) {
                ChatScreen()
            }

            // ── Settings ──────────────────────────────────────────────────────────
            composable(Screen.Notifications.route) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

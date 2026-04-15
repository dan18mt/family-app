package com.familyhome.app.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.familyhome.app.presentation.components.FamilyBottomBar
import com.familyhome.app.presentation.screens.chat.ChatScreen
import com.familyhome.app.presentation.screens.chores.ChoresScreen
import com.familyhome.app.presentation.screens.expenses.ExpensesScreen
import com.familyhome.app.presentation.screens.home.HomeScreen
import com.familyhome.app.presentation.screens.login.LoginScreen
import com.familyhome.app.presentation.screens.login.LoginViewModel
import com.familyhome.app.presentation.screens.notifications.NotificationsScreen
import com.familyhome.app.presentation.screens.onboarding.FatherOnboardingScreen
import com.familyhome.app.presentation.screens.onboarding.MemberOnboardingScreen
import com.familyhome.app.presentation.screens.prayer.PrayerScreen
import com.familyhome.app.presentation.screens.setup.SetupScreen
import com.familyhome.app.presentation.screens.stock.AddStockItemScreen
import com.familyhome.app.presentation.screens.stock.StockScreen
import com.familyhome.app.presentation.screens.tutorial.TutorialScreen
import kotlinx.coroutines.launch

private const val ANIM_DURATION = 300

// Ordered list of main tab routes — index is used as pager page number.
private val TAB_ROUTES = listOf(
    Screen.Home.route,
    Screen.Stock.route,
    Screen.Chores.route,
    Screen.Expenses.route,
    Screen.Prayer.route,
)

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(
        navController       = navController,
        startDestination    = startDestination,
        enterTransition     = { slideInHorizontally(tween(ANIM_DURATION)) { it } + fadeIn(tween(ANIM_DURATION)) },
        exitTransition      = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeOut(tween(ANIM_DURATION)) },
        popEnterTransition  = { slideInHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeIn(tween(ANIM_DURATION)) },
        popExitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) { it } + fadeOut(tween(ANIM_DURATION)) },
    ) {

        // ── Auth / Onboarding ────────────────────────────────────────────────────
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

        // ── Main tabs — single entry point, pager handles tab switching ──────────
        composable(Screen.Home.route) {
            MainTabsHost(navController = navController)
        }

        // ── Detail screens — pushed on top of the tab pager ─────────────────────
        composable(Screen.AddStockItem.route) {
            AddStockItemScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Chat.route) {
            ChatScreen()
        }
    }
}

/**
 * Hosts all five main tabs in a [HorizontalPager] so the user can swipe between them.
 * The [FamilyBottomBar] stays in sync with the pager — tapping a tab or swiping both
 * update the same [pagerState], so there is a single source of truth.
 */
@Composable
private fun MainTabsHost(navController: NavHostController) {
    val pagerState = rememberPagerState(pageCount = { TAB_ROUTES.size })
    val scope      = rememberCoroutineScope()

    // The currently settled tab route drives the bottom bar highlight.
    val currentRoute = TAB_ROUTES[pagerState.currentPage]

    // Translate a route string into a pager scroll — used by bottom bar and
    // by screens that want to navigate to another tab (e.g. HomeScreen cards).
    val navigateToTab: (String) -> Unit = { route ->
        val index = TAB_ROUTES.indexOf(route)
        if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
    }

    // Full navigator: tab routes go through the pager; everything else through NavController.
    val navigateTo: (Screen) -> Unit = { screen ->
        if (screen.route in TAB_ROUTES) navigateToTab(screen.route)
        else navController.navigate(screen.route)
    }

    Scaffold(
        bottomBar = {
            FamilyBottomBar(
                currentRoute = currentRoute,
                onNavigate   = navigateToTab,
            )
        }
    ) { padding ->
        HorizontalPager(
            state                   = pagerState,
            modifier                = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            beyondViewportPageCount = 1, // keep one adjacent page composed on each side
            key                     = { it },
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onNavigateTo    = navigateTo,
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Home.route,
                )
                1 -> StockScreen(
                    onAddItem       = { navController.navigate(Screen.AddStockItem.route) },
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Stock.route,
                )
                2 -> ChoresScreen(
                    onAddChore      = { navController.navigate(Screen.AddChore.route) },
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Chores.route,
                )
                3 -> ExpensesScreen(
                    onAddExpense    = { navController.navigate(Screen.AddExpense.route) },
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Expenses.route,
                )
                4 -> PrayerScreen(
                    onNavigateToTab = navigateToTab,
                    currentTabRoute = Screen.Prayer.route,
                )
                else -> Unit
            }
        }
    }
}

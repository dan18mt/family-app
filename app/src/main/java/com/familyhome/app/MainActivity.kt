package com.familyhome.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.familyhome.app.domain.repository.SessionRepository
import com.familyhome.app.domain.repository.UserRepository
import com.familyhome.app.presentation.navigation.AppNavGraph
import com.familyhome.app.presentation.navigation.Screen
import com.familyhome.app.presentation.theme.FamilyHomeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyHomeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        sessionRepository = sessionRepository,
                        userRepository    = userRepository,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRoot(
    sessionRepository: SessionRepository,
    userRepository: UserRepository,
) {
    val navController = rememberNavController()

    // Determine start destination:
    //   - No users at all → Setup screen
    //   - Users exist, no session → Login screen
    //   - Active session → Home screen
    val startDestination by produceState<String?>(initialValue = null) {
        combine(
            userRepository.getAllUsers(),
            sessionRepository.currentUserIdFlow,
        ) { users, currentId ->
            when {
                users.isEmpty()    -> Screen.Setup.route
                currentId == null  -> Screen.Login.route
                else               -> Screen.Home.route
            }
        }.collect { value = it }
    }

    startDestination?.let { start ->
        AppNavGraph(
            navController    = navController,
            startDestination = start,
        )
    }
}

package com.familyhome.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.familyhome.app.data.LocaleHelper
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.familyhome.app.data.notification.AlarmScheduler
import com.familyhome.app.data.service.FamilyBackgroundService
import com.familyhome.app.domain.repository.SessionRepository
import com.familyhome.app.domain.repository.UserRepository
import com.familyhome.app.presentation.navigation.AppNavGraph
import com.familyhome.app.presentation.navigation.Screen
import com.familyhome.app.presentation.theme.FamilyHomeTheme
import com.familyhome.app.presentation.theme.ThemePreference
import com.familyhome.app.presentation.theme.ThemeViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getStoredLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, language))
    }

    // ── Permission launchers ─────────────────────────────────────────────────

    private var notificationPermissionGranted by mutableStateOf(false)
    private var showNotificationRationale by mutableStateOf(false)
    private var showAlarmRationale by mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionGranted = granted
            if (!granted) showNotificationRationale = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FamilyBackgroundService.start(this)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationPermissionGranted = true
        }

        setContent {
            val themeVm: ThemeViewModel = hiltViewModel()
            val themePref by themeVm.themePreference.collectAsStateWithLifecycle()
            val isDark = when (themePref) {
                ThemePreference.LIGHT  -> false
                ThemePreference.DARK   -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }
            FamilyHomeTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Show rationale dialogs if permissions were denied
                    if (showNotificationRationale) {
                        PermissionRationaleDialog(
                            title   = "Notifications",
                            message = "FamilyHome needs notification permission to remind you about chores and alert you when your budget is nearly full. Please enable it in Settings.",
                            onDismiss = { showNotificationRationale = false },
                            onSettings = {
                                showNotificationRationale = false
                                openAppSettings()
                            },
                        )
                    }

                    if (showAlarmRationale) {
                        PermissionRationaleDialog(
                            title   = "Exact Alarms",
                            message = "FamilyHome needs exact alarm permission to fire chore reminders at the precise scheduled time. Please enable it in Settings.",
                            onDismiss = { showAlarmRationale = false },
                            onSettings = {
                                showAlarmRationale = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                } else {
                                    openAppSettings()
                                }
                            },
                        )
                    }

                    // Check exact alarm permission on API 31+ and prompt if missing
                    LaunchedEffect(Unit) {
                        if (!alarmScheduler.canScheduleExact()) {
                            showAlarmRationale = true
                        }
                    }

                    AppRoot(
                        sessionRepository = sessionRepository,
                        userRepository    = userRepository,
                    )
                }
            }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }
}

@Composable
private fun PermissionRationaleDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(title) },
        text             = { Text(message) },
        confirmButton    = { Button(onClick = onSettings) { Text("Open Settings") } },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun AppRoot(
    sessionRepository: SessionRepository,
    userRepository: UserRepository,
) {
    val navController = rememberNavController()

    val startDestination by produceState<String?>(initialValue = null) {
        combine(
            userRepository.getAllUsers(),
            sessionRepository.currentUserIdFlow,
        ) { users, currentId ->
            when {
                users.isEmpty()   -> Screen.Setup.route
                currentId == null -> Screen.Login.route
                else              -> Screen.Home.route
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

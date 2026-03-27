package com.familyhome.app.presentation.screens.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.AppNotification
import com.familyhome.app.domain.model.NotificationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    // Mark all as read when the screen is opened
    LaunchedEffect(Unit) {
        viewModel.markAllRead()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearAll) {
                            Text("Clear all")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector        = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier           = Modifier.size(64.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text      = "No notifications yet",
                        style     = MaterialTheme.typography.bodyLarge,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = "Family updates, chore completions, and join requests will appear here.",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationItem(notification = notification)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(notification: AppNotification) {
    val (icon, iconTint) = notificationStyle(notification.type)
    val timeText = remember(notification.timestamp) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(notification.timestamp))
    }

    ListItem(
        headlineContent = {
            Text(
                text  = notification.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (notification.isRead)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text  = notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            if (!notification.isRead) {
                Badge()
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun notificationStyle(type: NotificationType): Pair<ImageVector, Color> = when (type) {
    NotificationType.JOIN_REQUEST    -> Icons.Default.PersonAdd        to MaterialTheme.colorScheme.primary
    NotificationType.MEMBER_JOINED   -> Icons.Default.Person           to MaterialTheme.colorScheme.tertiary
    NotificationType.CHORE_COMPLETED -> Icons.Default.Check            to MaterialTheme.colorScheme.secondary
    NotificationType.CHORE_ASSIGNED  -> Icons.Default.CheckCircle      to MaterialTheme.colorScheme.secondary
    NotificationType.EXPENSE_ADDED   -> Icons.Default.MonetizationOn   to MaterialTheme.colorScheme.error
    NotificationType.GENERAL         -> Icons.Default.Info             to MaterialTheme.colorScheme.onSurfaceVariant
    NotificationType.CHORE_REMINDER  -> Icons.Default.NotificationImportant to MaterialTheme.colorScheme.surfaceBright
    NotificationType.CHORE_OVERDUE   -> Icons.Default.CrisisAlert to MaterialTheme.colorScheme.errorContainer
    NotificationType.LOW_STOCK       -> Icons.Default.CrisisAlert to MaterialTheme.colorScheme.error
}

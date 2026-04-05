package com.familyhome.app.presentation.screens.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    LaunchedEffect(Unit) { viewModel.markAllRead() }

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
                        TextButton(onClick = viewModel::clearAll) { Text("Clear all") }
                    }
                },
            )
        },
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Notifications, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No notifications yet", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Family updates, chore completions, and join requests will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationItem(
                        notification = notification,
                        onSnooze = { minutes -> viewModel.snooze(notification.id, System.currentTimeMillis() + minutes * 60_000L) },
                        onSilence = { viewModel.silence(notification.id) },
                        onRestore = { viewModel.restore(notification.id) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: AppNotification,
    onSnooze: (Int) -> Unit,
    onSilence: () -> Unit,
    onRestore: () -> Unit,
) {
    val (icon, iconTint) = notificationStyle(notification.type)
    val timeText = remember(notification.timestamp) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(notification.timestamp))
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showSnoozeDialog by remember { mutableStateOf(false) }

    if (showSnoozeDialog) {
        SnoozeDialog(
            onDismiss = { showSnoozeDialog = false },
            onSnooze  = { minutes -> onSnooze(minutes); showSnoozeDialog = false },
        )
    }

    val dimmed = notification.isSilenced || (notification.snoozedUntil != null && !notification.isActive)
    val statusLabel = when {
        notification.isSilenced -> "Silenced"
        notification.snoozedUntil != null && !notification.isActive -> {
            val remaining = (notification.snoozedUntil - System.currentTimeMillis()) / 60_000
            "Snoozed ${remaining}m"
        }
        else -> null
    }

    ListItem(
        headlineContent = {
            Text(
                text  = notification.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (notification.isRead || dimmed)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Column {
                Text(notification.message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(timeText, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    if (statusLabel != null) {
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        },
        leadingContent = {
            Icon(icon, null, tint = if (dimmed) iconTint.copy(alpha = 0.4f) else iconTint,
                modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!notification.isRead && notification.isActive) {
                    Badge()
                    Spacer(Modifier.width(4.dp))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "Options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (notification.isSilenced || notification.snoozedUntil != null) {
                            DropdownMenuItem(
                                text = { Text("Turn alert back on") },
                                leadingIcon = { Icon(Icons.Default.NotificationsActive, null) },
                                onClick = { onRestore(); menuExpanded = false },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Snooze…") },
                                leadingIcon = { Icon(Icons.Default.Snooze, null) },
                                onClick = { showSnoozeDialog = true; menuExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Silence this alert") },
                                leadingIcon = { Icon(Icons.Default.NotificationsOff, null) },
                                onClick = { onSilence(); menuExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SnoozeDialog(
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
) {
    val options = listOf(
        30  to "30 minutes",
        60  to "1 hour",
        120 to "2 hours",
        240 to "4 hours",
        480 to "8 hours",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Snooze for…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (minutes, label) ->
                    TextButton(
                        onClick = { onSnooze(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun notificationStyle(type: NotificationType): Pair<ImageVector, Color> = when (type) {
    NotificationType.JOIN_REQUEST    -> Icons.Default.PersonAdd             to MaterialTheme.colorScheme.primary
    NotificationType.MEMBER_JOINED   -> Icons.Default.Person                to MaterialTheme.colorScheme.tertiary
    NotificationType.CHORE_COMPLETED -> Icons.Default.Check                 to MaterialTheme.colorScheme.secondary
    NotificationType.CHORE_ASSIGNED  -> Icons.Default.CheckCircle           to MaterialTheme.colorScheme.secondary
    NotificationType.EXPENSE_ADDED   -> Icons.Default.MonetizationOn        to MaterialTheme.colorScheme.error
    NotificationType.GENERAL         -> Icons.Default.Info                  to MaterialTheme.colorScheme.onSurfaceVariant
    NotificationType.CHORE_REMINDER  -> Icons.Default.NotificationImportant to MaterialTheme.colorScheme.surfaceBright
    NotificationType.CHORE_OVERDUE   -> Icons.Default.CrisisAlert           to MaterialTheme.colorScheme.errorContainer
    NotificationType.LOW_STOCK       -> Icons.Default.CrisisAlert           to MaterialTheme.colorScheme.error
    NotificationType.PRAYER_REMINDER -> Icons.Default.NotificationsActive   to Color(0xFF2D6A4F)
}

package com.familyhome.app.presentation.screens.chores

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.presentation.components.LoadingScreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoresScreen(
    onAddChore: () -> Unit,
    viewModel: ChoresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLogDialog by remember { mutableStateOf(false) }

    if (showLogDialog) {
        QuickLogChoreDialog(
            onDismiss = { showLogDialog = false },
            onConfirm = { task, note ->
                viewModel.logChore(task, note.ifBlank { null })
                showLogDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Chores") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showLogDialog = true }) {
                Icon(Icons.Default.Add, "Log a chore")
            }
        }
    ) { padding ->
        if (state.isLoading) { LoadingScreen(); return@Scaffold }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── Recurring tasks ──────────────────────────────────────────
            if (state.recurringTasks.isNotEmpty()) {
                item {
                    Text(
                        "Recurring Tasks",
                        style    = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(state.recurringTasks, key = { it.id }) { task ->
                    RecurringTaskRow(task = task, onComplete = { viewModel.completeTask(task) })
                    HorizontalDivider()
                }
            }

            // ── History ──────────────────────────────────────────────────
            item {
                Row(
                    modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("History (last ${state.historyDays}d)", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        viewModel.changeHistoryRange(if (state.historyDays == 7) 30 else 7)
                    }) {
                        Text(if (state.historyDays == 7) "Show 30d" else "Show 7d")
                    }
                }
            }

            if (state.history.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No chores logged yet.")
                    }
                }
            } else {
                items(state.history, key = { it.id }) { log ->
                    ChoreLogRow(log)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RecurringTaskRow(task: RecurringTask, onComplete: () -> Unit) {
    ListItem(
        headlineContent    = { Text(task.taskName) },
        supportingContent  = {
            Text(
                if (task.isOverdue) "Overdue · ${task.frequency.displayName}"
                else                "Due soon · ${task.frequency.displayName}",
                color = if (task.isOverdue) MaterialTheme.colorScheme.error
                        else               MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                if (task.isOverdue) Icons.Default.Warning else Icons.Default.Refresh,
                null,
                tint = if (task.isOverdue) MaterialTheme.colorScheme.error
                       else                MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            FilledTonalButton(onClick = onComplete) { Text("Done") }
        }
    )
}

@Composable
private fun ChoreLogRow(log: ChoreLog) {
    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    ListItem(
        headlineContent   = { Text(log.taskName) },
        supportingContent = { Text(fmt.format(Date(log.doneAt))) },
        leadingContent    = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent   = log.note?.let { note -> { Text(note, style = MaterialTheme.typography.bodySmall) } },
    )
}

@Composable
private fun QuickLogChoreDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var task by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Log a chore") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = task,
                    onValueChange = { task = it },
                    label         = { Text("Task name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value         = note,
                    onValueChange = { note = it },
                    label         = { Text("Note (optional)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton    = {
            Button(onClick = { if (task.isNotBlank()) onConfirm(task, note) }) { Text("Log") }
        },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

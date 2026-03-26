package com.familyhome.app.presentation.screens.chores

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.AssignmentStatus
import com.familyhome.app.domain.model.ChoreAssignment
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.Frequency
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
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
    var showLogDialog      by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var assignTask         by remember { mutableStateOf<RecurringTask?>(null) }
    var respondAssignment  by remember { mutableStateOf<ChoreAssignment?>(null) }
    val snackbarHostState  = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(state.error!!)
            viewModel.clearError()
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showLogDialog) {
        QuickLogChoreDialog(
            onDismiss = { showLogDialog = false },
            onConfirm = { task, note ->
                viewModel.logChore(task, note.ifBlank { null })
                showLogDialog = false
            },
        )
    }

    if (showScheduleDialog) {
        ScheduleChoreDialog(
            familyMembers = state.allUsers,
            currentUser   = state.currentUser,
            onDismiss     = { showScheduleDialog = false },
            onConfirm     = { name, freq, assignTo, scheduledAt, reminderMin ->
                viewModel.addScheduledTask(name, freq, assignTo, scheduledAt, reminderMin)
                showScheduleDialog = false
            },
        )
    }

    assignTask?.let { task ->
        AssignTaskDialog(
            task          = task,
            familyMembers = state.allUsers.filter { it.id != state.currentUser?.id },
            onDismiss     = { assignTask = null },
            onConfirm     = { userId -> viewModel.assignTask(task, userId); assignTask = null },
        )
    }

    respondAssignment?.let { assignment ->
        RespondAssignmentDialog(
            assignment = assignment,
            onDismiss  = { respondAssignment = null },
            onAccept   = { viewModel.acceptAssignment(assignment); respondAssignment = null },
            onDecline  = { reason -> viewModel.declineAssignment(assignment, reason); respondAssignment = null },
        )
    }

    // ── Scaffold ─────────────────────────────────────────────────────────────

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Chores") },
                actions = {
                    if (state.currentUser?.role == Role.FATHER || state.currentUser?.role == Role.WIFE) {
                        IconButton(onClick = { showScheduleDialog = true }) {
                            Icon(Icons.Default.Schedule, "Schedule a chore")
                        }
                    }
                }
            )
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

            // ── Pending assignments for this user ────────────────────────────
            if (state.pendingAssignments.isNotEmpty()) {
                item {
                    Text(
                        "Assigned to me (${state.pendingAssignments.size})",
                        style    = MaterialTheme.typography.titleMedium,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.pendingAssignments, key = { it.id }) { assignment ->
                    PendingAssignmentRow(
                        assignment = assignment,
                        onRespond  = { respondAssignment = assignment },
                    )
                    HorizontalDivider()
                }
            }

            // ── Recurring tasks ──────────────────────────────────────────────
            if (state.recurringTasks.isNotEmpty()) {
                item {
                    Text(
                        "Scheduled Tasks",
                        style    = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.recurringTasks, key = { it.id }) { task ->
                    RecurringTaskRow(
                        task          = task,
                        allUsers      = state.allUsers,
                        canAssign     = state.currentUser?.role == Role.FATHER || state.currentUser?.role == Role.WIFE,
                        onComplete    = { viewModel.completeTask(task) },
                        onAssign      = { assignTask = task },
                    )
                    HorizontalDivider()
                }
            }

            // ── History ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
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
                    ChoreLogRow(log = log, allUsers = state.allUsers)
                    HorizontalDivider()
                }
            }
        }
    }
}

// ── Rows ──────────────────────────────────────────────────────────────────────

@Composable
private fun RecurringTaskRow(
    task: RecurringTask,
    allUsers: List<User>,
    canAssign: Boolean,
    onComplete: () -> Unit,
    onAssign: () -> Unit,
) {
    val fmt = SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault())
    val assigneeName = task.assignedTo?.let { id -> allUsers.find { it.id == id }?.name }

    ListItem(
        headlineContent   = { Text(task.taskName) },
        supportingContent = {
            Column {
                if (task.scheduledAt != null) {
                    Text("Scheduled: ${fmt.format(Date(task.scheduledAt))}",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (task.reminderMinutesBefore != null && task.reminderMinutesBefore > 0) {
                    Text("Reminder: ${task.reminderMinutesBefore} min before",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
                val statusText = buildString {
                    if (task.isOverdue) append("Overdue · ") else append("Due soon · ")
                    append(task.frequency.displayName)
                    if (assigneeName != null) append(" · Assigned to $assigneeName")
                }
                Text(
                    statusText,
                    color = if (task.isOverdue) MaterialTheme.colorScheme.error
                            else               MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Icon(
                if (task.isOverdue) Icons.Default.Warning else Icons.Default.Schedule,
                null,
                tint = if (task.isOverdue) MaterialTheme.colorScheme.error
                       else                MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canAssign) {
                    IconButton(onClick = onAssign) {
                        Icon(Icons.Default.PersonAdd, "Assign task",
                            tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                FilledTonalButton(onClick = onComplete) { Text("Done") }
            }
        },
    )
}

@Composable
private fun ChoreLogRow(log: ChoreLog, allUsers: List<User>) {
    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val doerName = allUsers.find { it.id == log.doneBy }?.name ?: log.doneBy
    ListItem(
        headlineContent   = { Text(log.taskName) },
        supportingContent = {
            Text("${fmt.format(Date(log.doneAt))} · by $doerName")
        },
        leadingContent  = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = log.note?.let { note -> { Text(note, style = MaterialTheme.typography.bodySmall) } },
    )
}

@Composable
private fun PendingAssignmentRow(assignment: ChoreAssignment, onRespond: () -> Unit) {
    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    ListItem(
        headlineContent   = { Text(assignment.taskName) },
        supportingContent = { Text("Assigned ${fmt.format(Date(assignment.assignedAt))}") },
        leadingContent    = {
            Icon(Icons.Default.AssignmentInd, null, tint = MaterialTheme.colorScheme.tertiary)
        },
        trailingContent   = {
            Button(onClick = onRespond) { Text("Respond") }
        },
    )
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

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
                OutlinedTextField(value = task, onValueChange = { task = it },
                    label = { Text("Task name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton    = { Button(onClick = { if (task.isNotBlank()) onConfirm(task, note) }) { Text("Log") } },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleChoreDialog(
    familyMembers: List<User>,
    currentUser: User?,
    onDismiss: () -> Unit,
    onConfirm: (String, Frequency, String?, Long?, Int?) -> Unit,
) {
    var taskName    by remember { mutableStateOf("") }
    var frequency   by remember { mutableStateOf(Frequency.CUSTOM) }
    var assignTo    by remember { mutableStateOf<String?>(null) }
    var dateText    by remember { mutableStateOf("") }
    var timeText    by remember { mutableStateOf("") }
    var reminderMin by remember { mutableStateOf<Int?>(null) }
    var freqExpanded  by remember { mutableStateOf(false) }
    var assignExpanded by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }

    val reminderOptions = listOf(null to "No reminder", 0 to "At the time",
        1 to "1 min before", 5 to "5 min before", 15 to "15 min before", 30 to "30 min before")
    val reminderLabel = reminderOptions.firstOrNull { it.first == reminderMin }?.second ?: "No reminder"

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Schedule a chore") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = taskName, onValueChange = { taskName = it },
                    label = { Text("Task name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Frequency
                ExposedDropdownMenuBox(expanded = freqExpanded, onExpandedChange = { freqExpanded = it }) {
                    OutlinedTextField(
                        value = frequency.displayName, onValueChange = {}, readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(freqExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                        Frequency.entries.forEach { f ->
                            DropdownMenuItem(text = { Text(f.displayName) },
                                onClick = { frequency = f; freqExpanded = false })
                        }
                    }
                }
                // Date + time
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText, onValueChange = { dateText = it },
                        label = { Text("Date (dd/MM/yyyy)") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = timeText, onValueChange = { timeText = it },
                        label = { Text("Time (HH:mm)") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Reminder
                ExposedDropdownMenuBox(expanded = reminderExpanded, onExpandedChange = { reminderExpanded = it }) {
                    OutlinedTextField(
                        value = reminderLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Reminder") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reminderExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = reminderExpanded, onDismissRequest = { reminderExpanded = false }) {
                        reminderOptions.forEach { (min, label) ->
                            DropdownMenuItem(text = { Text(label) },
                                onClick = { reminderMin = min; reminderExpanded = false })
                        }
                    }
                }
                // Assign to (optional)
                if (familyMembers.isNotEmpty()) {
                    val assignLabel = familyMembers.find { it.id == assignTo }?.name ?: "Anyone"
                    ExposedDropdownMenuBox(expanded = assignExpanded, onExpandedChange = { assignExpanded = it }) {
                        OutlinedTextField(
                            value = assignLabel, onValueChange = {}, readOnly = true,
                            label = { Text("Assign to (optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(assignExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = assignExpanded, onDismissRequest = { assignExpanded = false }) {
                            DropdownMenuItem(text = { Text("Anyone") }, onClick = { assignTo = null; assignExpanded = false })
                            familyMembers.forEach { user ->
                                DropdownMenuItem(text = { Text(user.name) },
                                    onClick = { assignTo = user.id; assignExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (taskName.isBlank()) return@Button
                val scheduledAt = parseDateTime(dateText, timeText)
                onConfirm(taskName, frequency, assignTo, scheduledAt, reminderMin)
            }) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignTaskDialog(
    task: RecurringTask,
    familyMembers: List<User>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var expanded       by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Assign \"${task.taskName}\"") },
        text             = {
            if (familyMembers.isEmpty()) {
                Text("No family members to assign to.")
            } else {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = familyMembers.find { it.id == selectedUserId }?.name ?: "Select member",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Assign to") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        familyMembers.forEach { user ->
                            DropdownMenuItem(
                                text = { Text(user.name) },
                                onClick = { selectedUserId = user.id; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { selectedUserId?.let { onConfirm(it) } },
                enabled = selectedUserId != null) { Text("Assign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RespondAssignmentDialog(
    assignment: ChoreAssignment,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onDecline: (String) -> Unit,
) {
    var declineMode by remember { mutableStateOf(false) }
    var reason      by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (declineMode) "Decline task" else "Task assigned to you") },
        text = {
            if (declineMode) {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Task: ${assignment.taskName}", style = MaterialTheme.typography.bodyLarge)
                    Text("Do you accept this task?", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (declineMode) {
                Button(onClick = { onDecline(reason) }) { Text("Confirm decline") }
            } else {
                Button(onClick = onAccept) { Text("Accept") }
            }
        },
        dismissButton = {
            if (declineMode) {
                TextButton(onClick = { declineMode = false }) { Text("Back") }
            } else {
                TextButton(onClick = { declineMode = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Decline")
                }
            }
        },
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Parse "dd/MM/yyyy" + "HH:mm" into epoch millis, or null if invalid. */
private fun parseDateTime(dateStr: String, timeStr: String): Long? {
    return try {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        fmt.isLenient = false
        val combined = "${dateStr.trim()} ${timeStr.trim()}"
        fmt.parse(combined)?.time
    } catch (e: Exception) {
        null
    }
}

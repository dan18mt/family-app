package com.familyhome.app.presentation.screens.chores

import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.familyhome.app.presentation.components.FamilyBottomBar
import com.familyhome.app.presentation.components.LoadingScreen
import com.familyhome.app.presentation.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar as Cal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoresScreen(
    onAddChore: () -> Unit,
    onNavigateToTab: (String) -> Unit = {},
    currentTabRoute: String = Screen.Chores.route,
    viewModel: ChoresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLogDialog      by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var assignTask         by remember { mutableStateOf<RecurringTask?>(null) }
    var respondAssignment  by remember { mutableStateOf<ChoreAssignment?>(null) }
    var editTask           by remember { mutableStateOf<RecurringTask?>(null) }
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

    editTask?.let { task ->
        EditTaskDialog(
            task      = task,
            onDismiss = { editTask = null },
            onConfirm = { updated -> viewModel.updateTask(updated); editTask = null },
        )
    }

    // ── Scaffold ─────────────────────────────────────────────────────────────

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar    = { FamilyBottomBar(currentRoute = currentTabRoute, onNavigate = onNavigateToTab) },
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
                        task       = task,
                        allUsers   = state.allUsers,
                        canAssign  = state.currentUser?.role == Role.FATHER || state.currentUser?.role == Role.WIFE,
                        canEdit    = state.currentUser?.role == Role.FATHER || state.currentUser?.role == Role.WIFE,
                        onComplete = { viewModel.completeTask(task) },
                        onAssign   = { assignTask = task },
                        onEdit     = { editTask = task },
                        onDelete   = { viewModel.deleteTask(task) },
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
                    ChoreLogRow(
                        log      = log,
                        allUsers = state.allUsers,
                        canEdit  = state.currentUser?.role == Role.FATHER || state.currentUser?.role == Role.WIFE,
                        onDelete = { viewModel.deleteChoreLog(log) },
                    )
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
    canEdit: Boolean,
    onComplete: () -> Unit,
    onAssign: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                if (canEdit) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit task", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete task", tint = MaterialTheme.colorScheme.error)
                    }
                }
                FilledTonalButton(onClick = onComplete) { Text("Done") }
            }
        },
    )
}

@Composable
private fun ChoreLogRow(log: ChoreLog, allUsers: List<User>, canEdit: Boolean, onDelete: () -> Unit) {
    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val doerName = allUsers.find { it.id == log.doneBy }?.name ?: log.doneBy
    ListItem(
        headlineContent   = { Text(log.taskName) },
        supportingContent = {
            Text("${fmt.format(Date(log.doneAt))} · by $doerName")
        },
        leadingContent  = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (log.note != null) {
                    Text(log.note, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(4.dp))
                }
                if (canEdit) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete log", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
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
    var taskName         by remember { mutableStateOf("") }
    var frequency        by remember { mutableStateOf(Frequency.CUSTOM) }
    var assignTo         by remember { mutableStateOf<String?>(null) }
    var reminderMin      by remember { mutableStateOf<Int?>(null) }
    var freqExpanded     by remember { mutableStateOf(false) }
    var assignExpanded   by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }
    var showDatePicker   by remember { mutableStateOf(false) }
    var showTimePicker   by remember { mutableStateOf(false) }

    val datePickerState   = rememberDatePickerState()
    val timePickerState   = rememberTimePickerState(is24Hour = true)
    val dateInteraction   = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val timeInteraction   = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val datePressed by dateInteraction.collectIsPressedAsState()
    val timePressed by timeInteraction.collectIsPressedAsState()

    LaunchedEffect(datePressed) { if (datePressed) showDatePicker = true }
    LaunchedEffect(timePressed) { if (timePressed) showTimePicker = true }

    val dateLabel = datePickerState.selectedDateMillis?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
    } ?: "Pick date"
    val timeLabel = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)

    val reminderOptions = listOf(null to "No reminder", 0 to "At the time",
        1 to "1 min before", 5 to "5 min before", 15 to "15 min before", 30 to "30 min before")
    val reminderLabel = reminderOptions.firstOrNull { it.first == reminderMin }?.second ?: "No reminder"

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) },
        )
    }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Date") }, singleLine = true,
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                        modifier = Modifier.weight(1f),
                        interactionSource = dateInteraction,
                    )
                    OutlinedTextField(
                        value = timeLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Time") }, singleLine = true,
                        trailingIcon = { Icon(Icons.Default.Schedule, null) },
                        modifier = Modifier.weight(1f),
                        interactionSource = timeInteraction,
                    )
                }
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
                val scheduledAt = buildScheduledAt(datePickerState.selectedDateMillis, timePickerState.hour, timePickerState.minute)
                onConfirm(taskName, frequency, assignTo, scheduledAt, reminderMin)
            }) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTaskDialog(
    task: RecurringTask,
    onDismiss: () -> Unit,
    onConfirm: (RecurringTask) -> Unit,
) {
    var taskName         by remember { mutableStateOf(task.taskName) }
    var frequency        by remember { mutableStateOf(task.frequency) }
    var reminderMin      by remember { mutableStateOf(task.reminderMinutesBefore) }
    var freqExpanded     by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }
    var showDatePicker   by remember { mutableStateOf(false) }
    var showTimePicker   by remember { mutableStateOf(false) }

    val initialCal = Cal.getInstance().also { cal ->
        task.scheduledAt?.let { cal.timeInMillis = it }
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = task.scheduledAt)
    val timePickerState = rememberTimePickerState(
        initialHour   = initialCal.get(Cal.HOUR_OF_DAY),
        initialMinute = initialCal.get(Cal.MINUTE),
        is24Hour      = true,
    )
    val dateInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val timeInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val datePressed by dateInteraction.collectIsPressedAsState()
    val timePressed by timeInteraction.collectIsPressedAsState()

    LaunchedEffect(datePressed) { if (datePressed) showDatePicker = true }
    LaunchedEffect(timePressed) { if (timePressed) showTimePicker = true }

    val dateLabel = datePickerState.selectedDateMillis?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
    } ?: "Pick date"
    val timeLabel = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)

    val reminderOptions = listOf(null to "No reminder", 0 to "At the time",
        1 to "1 min before", 5 to "5 min before", 15 to "15 min before", 30 to "30 min before")
    val reminderLabel = reminderOptions.firstOrNull { it.first == reminderMin }?.second ?: "No reminder"

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = taskName, onValueChange = { taskName = it },
                    label = { Text("Task name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Date") }, singleLine = true,
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                        modifier = Modifier.weight(1f),
                        interactionSource = dateInteraction,
                    )
                    OutlinedTextField(
                        value = timeLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Time") }, singleLine = true,
                        trailingIcon = { Icon(Icons.Default.Schedule, null) },
                        modifier = Modifier.weight(1f),
                        interactionSource = timeInteraction,
                    )
                }
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
            }
        },
        confirmButton = {
            Button(onClick = {
                if (taskName.isBlank()) return@Button
                val scheduledAt = buildScheduledAt(datePickerState.selectedDateMillis, timePickerState.hour, timePickerState.minute)
                onConfirm(task.copy(taskName = taskName, frequency = frequency, scheduledAt = scheduledAt, reminderMinutesBefore = reminderMin))
            }) { Text("Save") }
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

/**
 * Combines a date from the DatePicker (midnight UTC epoch) with hour/minute from
 * the TimePicker into a local-time epoch-ms value, or null if no date was picked.
 */
private fun buildScheduledAt(dateMillis: Long?, hour: Int, minute: Int): Long? {
    dateMillis ?: return null
    val cal = Cal.getInstance()
    cal.timeInMillis = dateMillis
    cal.set(Cal.HOUR_OF_DAY, hour)
    cal.set(Cal.MINUTE, minute)
    cal.set(Cal.SECOND, 0)
    cal.set(Cal.MILLISECOND, 0)
    return cal.timeInMillis
}

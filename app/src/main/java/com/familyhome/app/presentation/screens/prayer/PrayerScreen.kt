package com.familyhome.app.presentation.screens.prayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.R
import com.familyhome.app.domain.model.PrayerGoalSetting
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.SunnahGoal
import com.familyhome.app.domain.model.User
import com.familyhome.app.presentation.components.SectionHeader
import java.util.Calendar

private val PrayerGreen      = Color(0xFF1B4332)
private val PrayerGreenLight = Color(0xFF2D6A4F)
private val PrayerGold       = Color(0xFFD4A017)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(
    viewModel: PrayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentUser = state.currentUser
    val isFather = currentUser?.role == Role.FATHER

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddGoalDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.prayer_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.prayer_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (isFather && selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showAddGoalDialog = true },
                    containerColor = PrayerGreenLight,
                    contentColor   = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Sunnah Goal")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isFather) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = MaterialTheme.colorScheme.background,
                    contentColor     = MaterialTheme.colorScheme.primary,
                    indicator        = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = PrayerGreenLight,
                        )
                    },
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        text     = { Text(stringResource(R.string.prayer_tab_today)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        text     = { Text(stringResource(R.string.prayer_tab_manage)) },
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrayerGreenLight)
                }
            } else if (selectedTab == 0 || !isFather) {
                TodayTab(state = state, currentUser = currentUser, viewModel = viewModel)
            } else {
                ManageTab(state = state, viewModel = viewModel)
            }
        }
    }

    if (showAddGoalDialog) {
        AddGoalDialog(
            existing    = state.goalSettings,
            allUsers    = state.allUsers,
            onAdd       = { sunnahKey, assignedTo ->
                viewModel.addGoal(sunnahKey, assignedTo)
                showAddGoalDialog = false
            },
            onDismiss   = { showAddGoalDialog = false },
        )
    }
}

// ── Today Tab ──────────────────────────────────────────────────────────────────

@Composable
private fun TodayTab(
    state: PrayerUiState,
    currentUser: User?,
    viewModel: PrayerViewModel,
) {
    val userId = currentUser?.id ?: return
    val activeGoals = state.activeGoalsFor(userId)

    if (activeGoals.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.prayer_no_goals),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.prayer_no_goals_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        return
    }

    val completedCount = state.completedTodayCount(userId)
    val totalCount = activeGoals.size

    LazyColumn(
        contentPadding      = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            // Daily summary header
            DailySummaryCard(completed = completedCount, total = totalCount)
        }
        item { SectionHeader(stringResource(R.string.prayer_today_goals)) }
        val langTag = LocalContext.current.resources.configuration.locales[0].language
        items(activeGoals, key = { it.id }) { goal ->
            val log = state.todayLogFor(goal.sunnahKey, userId)
            val sunnah = goal.sunnah
            if (sunnah != null) {
                GoalProgressCard(
                    sunnah         = sunnah,
                    langTag        = langTag,
                    completedCount = log?.completedCount ?: 0,
                    isCompleted    = log?.isCompleted == true,
                    weekLogs       = state.weekLogs.filter { it.sunnahKey == goal.sunnahKey && it.userId == userId },
                    onLog          = { viewModel.logPrayer(goal.sunnahKey) },
                    onUndo         = { viewModel.undoPrayer(goal.sunnahKey) },
                )
            }
        }
    }
}

@Composable
private fun DailySummaryCard(completed: Int, total: Int) {
    val allDone = completed == total && total > 0
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (allDone) PrayerGreen else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier             = Modifier.padding(16.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier         = Modifier.size(56.dp).clip(CircleShape)
                    .background(if (allDone) PrayerGold else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$completed/$total",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (allDone) Color.White else MaterialTheme.colorScheme.primary,
                )
            }
            Column {
                Text(
                    if (allDone) stringResource(R.string.prayer_all_done) else stringResource(R.string.prayer_today_progress),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (allDone) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress            = { if (total > 0) completed.toFloat() / total else 0f },
                    modifier            = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(CircleShape),
                    color               = if (allDone) PrayerGold else PrayerGreenLight,
                    trackColor          = if (allDone) PrayerGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GoalProgressCard(
    sunnah: SunnahGoal,
    langTag: String,
    completedCount: Int,
    isCompleted: Boolean,
    weekLogs: List<com.familyhome.app.domain.model.PrayerLog>,
    onLog: () -> Unit,
    onUndo: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                PrayerGreen.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Completion circle
                Box(
                    modifier         = Modifier.size(44.dp).clip(CircleShape)
                        .background(
                            if (isCompleted) PrayerGreenLight
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (!isCompleted) Modifier.border(1.5.dp, PrayerGreenLight.copy(alpha = 0.4f), CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            "$completedCount",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sunnah.localizedTitle(langTag),
                        style    = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${completedCount}/${sunnah.dailyTarget} ${sunnah.localizedUnit(langTag)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCompleted) PrayerGreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (completedCount > 0) {
                        OutlinedIconButton(
                            onClick = onUndo,
                            modifier = Modifier.size(36.dp),
                            border  = ButtonDefaults.outlinedButtonBorder(),
                        ) {
                            Icon(Icons.Default.Remove, "Undo", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (!isCompleted) {
                        IconButton(
                            onClick   = onLog,
                            modifier  = Modifier.size(36.dp).clip(CircleShape).background(PrayerGreenLight),
                        ) {
                            Icon(Icons.Default.Add, "Log", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Hadith snippet
            Spacer(Modifier.height(8.dp))
            Text(
                "\"${sunnah.hadith.take(120)}${if (sunnah.hadith.length > 120) "..." else ""}\"",
                style     = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
            )
            Text(
                sunnah.source,
                style = MaterialTheme.typography.labelSmall,
                color = PrayerGreenLight,
            )

            // 7-day dots
            Spacer(Modifier.height(10.dp))
            WeekStreak(sunnah = sunnah, weekLogs = weekLogs)
        }
    }
}

@Composable
private fun WeekStreak(
    sunnah: SunnahGoal,
    weekLogs: List<com.familyhome.app.domain.model.PrayerLog>,
) {
    val today = Calendar.getInstance().run {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis / (24 * 60 * 60 * 1000L)
    }
    val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
    val cal = Calendar.getInstance()
    // Sunday = 1, Monday = 2... map to index 0-6 (Mon-Sun)
    val todayDow = ((cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7)

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (i in 0..6) {
            val dayOffset = i - todayDow
            val epochDay  = today + dayOffset
            val isFuture  = dayOffset > 0
            val log       = weekLogs.firstOrNull { it.epochDay == epochDay }
            val done      = !isFuture && (log?.isCompleted == true)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier         = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done     -> PrayerGreenLight
                                isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else     -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
                Text(
                    dayNames[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ── Manage Tab (Father only) ───────────────────────────────────────────────────

@Composable
private fun ManageTab(
    state: PrayerUiState,
    viewModel: PrayerViewModel,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        item { SectionHeader(stringResource(R.string.prayer_active_goals)) }
        if (state.goalSettings.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.prayer_no_goals_manage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.goalSettings, key = { it.id }) { setting ->
                ManageGoalItem(
                    setting  = setting,
                    allUsers = state.allUsers,
                    onToggle = { viewModel.toggleGoal(setting) },
                    onRemove = { viewModel.removeGoal(setting) },
                )
            }
        }
    }
}

@Composable
private fun ManageGoalItem(
    setting: PrayerGoalSetting,
    allUsers: List<User>,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val sunnah       = setting.sunnah ?: return
    val langTag      = LocalContext.current.resources.configuration.locales[0].language
    val allFamilyLabel = stringResource(R.string.prayer_all_family)
    val assigneeName = when {
        setting.assignedTo == null -> allFamilyLabel
        else -> allUsers.firstOrNull { it.id == setting.assignedTo }?.name ?: "Unknown"
    }

    ListItem(
        headlineContent = {
            Text(sunnah.localizedTitle(langTag), style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Text(
                "Assigned to: $assigneeName • ${sunnah.dailyTarget} ${sunnah.localizedUnit(langTag)}/day",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Box(
                modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (setting.isEnabled) PrayerGreen else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint     = if (setting.isEnabled) PrayerGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked  = setting.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors   = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrayerGreenLight),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, "Remove goal", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
}

// ── Add Goal Dialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalDialog(
    existing: List<PrayerGoalSetting>,
    allUsers: List<User>,
    onAdd: (sunnahKey: String, assignedTo: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val existingKeys = existing.map { it.sunnahKey }.toSet()
    val available    = SunnahGoal.entries.filter { it.name !in existingKeys }

    val langTag = LocalContext.current.resources.configuration.locales[0].language
    var selectedSunnah by remember { mutableStateOf<SunnahGoal?>(null) }
    var selectedUser   by remember { mutableStateOf<String?>(null) } // null = all family

    var sunnahExpanded by remember { mutableStateOf(false) }
    var memberExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title  = { Text(stringResource(R.string.prayer_add_goal)) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Sunnah picker
                ExposedDropdownMenuBox(
                    expanded        = sunnahExpanded,
                    onExpandedChange = { sunnahExpanded = it },
                ) {
                    OutlinedTextField(
                        value         = selectedSunnah?.localizedTitle(langTag) ?: if (langTag == "en") "Select a sunnah practice" else "Pilih amalan sunnah",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Sunnah Practice") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sunnahExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded        = sunnahExpanded,
                        onDismissRequest = { sunnahExpanded = false },
                    ) {
                        if (available.isEmpty()) {
                            DropdownMenuItem(
                                text    = { Text("All sunnah goals already added") },
                                onClick = { sunnahExpanded = false },
                                enabled = false,
                            )
                        } else {
                            available.forEach { sunnah ->
                                DropdownMenuItem(
                                    text    = { Text(sunnah.localizedTitle(langTag)) },
                                    onClick = {
                                        selectedSunnah = sunnah
                                        sunnahExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Hadith preview
                selectedSunnah?.let { sunnah ->
                    Card(
                        shape  = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "\"${sunnah.hadith.take(150)}${if (sunnah.hadith.length > 150) "..." else ""}\"",
                                style    = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                sunnah.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = PrayerGreenLight,
                            )
                        }
                    }
                }

                // Assign to picker
                ExposedDropdownMenuBox(
                    expanded         = memberExpanded,
                    onExpandedChange = { memberExpanded = it },
                ) {
                    val allFamilyStr = stringResource(R.string.prayer_all_family)
                    OutlinedTextField(
                        value         = if (selectedUser == null) allFamilyStr
                                        else allUsers.firstOrNull { it.id == selectedUser }?.name ?: allFamilyStr,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Assign to") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded         = memberExpanded,
                        onDismissRequest = { memberExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text    = { Text(allFamilyStr) },
                            onClick = { selectedUser = null; memberExpanded = false },
                        )
                        allUsers.forEach { user ->
                            DropdownMenuItem(
                                text    = { Text("${user.name} (${user.role.displayName})") },
                                onClick = { selectedUser = user.id; memberExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { selectedSunnah?.let { onAdd(it.name, selectedUser) } },
                enabled  = selectedSunnah != null,
                colors   = ButtonDefaults.buttonColors(containerColor = PrayerGreenLight),
            ) { Text("Add Goal") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

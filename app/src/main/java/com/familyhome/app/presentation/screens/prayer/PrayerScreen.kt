package com.familyhome.app.presentation.screens.prayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.R
import com.familyhome.app.domain.model.PrayerGoalSetting
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.SunnahGoal
import com.familyhome.app.domain.model.User
import com.familyhome.app.presentation.components.SectionHeader
import com.familyhome.app.presentation.navigation.Screen
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.util.Calendar

// ── Color palette ─────────────────────────────────────────────────────────────
private val PrayerGreen      = Color(0xFF1B4332)
private val PrayerGreenLight = Color(0xFF2D6A4F)
private val PrayerGold       = Color(0xFFD4A017)
private val PrayerGoldLight  = Color(0xFFF5C842)

// ── Per-member line colors for the family trend chart ─────────────────────────
private val MemberLineColors = listOf(
    Color(0xFF2D6A4F), // green
    Color(0xFF1565C0), // blue
    Color(0xFFD4A017), // gold
    Color(0xFFAD1457), // pink
    Color(0xFF6A1B9A), // purple
    Color(0xFFE65100), // deep orange
    Color(0xFF00838F), // teal
    Color(0xFF558B2F), // lime green
)

// ── Period selector ───────────────────────────────────────────────────────────
private enum class ProgressPeriod(val labelRes: Int, val days: Long) {
    TODAY(R.string.prayer_period_today, 0L),
    WEEK(R.string.prayer_period_week, 6L),
    MONTH(R.string.prayer_period_month, 29L),
    THREE_MONTHS(R.string.prayer_period_3months, 89L),
}

// ── Root composable ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(
    onNavigateToTab: (String) -> Unit = {},
    currentTabRoute: String = Screen.Prayer.route,
    viewModel: PrayerViewModel = hiltViewModel(),
) {
    val state       by viewModel.state.collectAsStateWithLifecycle()
    val currentUser = state.currentUser
    val isFather    = currentUser?.role == Role.FATHER

    // Tab indices:
    //   Father  → 0=Today  1=Family  2=Manage
    //   Others  → 0=Today  1=Family
    val tabs = if (isFather)
        listOf(R.string.prayer_tab_today, R.string.prayer_tab_family, R.string.prayer_tab_manage)
    else
        listOf(R.string.prayer_tab_today, R.string.prayer_tab_family)

    var selectedTab       by remember { mutableIntStateOf(0) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Consume reminderSentTo and show a Snackbar confirmation
    val reminderSentTo = state.reminderSentTo
    val reminderMsg    = stringResource(R.string.prayer_reminder_sent_to)
    LaunchedEffect(reminderSentTo) {
        if (reminderSentTo != null) {
            snackbarHostState.showSnackbar("$reminderMsg $reminderSentTo 🔔")
            viewModel.clearReminderSent()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            val manageTabIndex = if (isFather) 2 else -1
            if (isFather && selectedTab == manageTabIndex) {
                FloatingActionButton(
                    onClick        = { showAddGoalDialog = true },
                    containerColor = PrayerGreenLight,
                    contentColor   = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.prayer_add_goal))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.background,
                contentColor     = MaterialTheme.colorScheme.primary,
                indicator        = { positions ->
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[selectedTab]),
                        color = PrayerGreenLight,
                    )
                },
            ) {
                tabs.forEachIndexed { index, labelRes ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(stringResource(labelRes)) },
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrayerGreenLight)
                }
                return@Column
            }

            when {
                selectedTab == 0 ->
                    TodayTab(state = state, currentUser = currentUser, viewModel = viewModel)
                selectedTab == 1 ->
                    FamilyStatsTab(
                        state          = state,
                        currentUser    = currentUser,
                        onSendReminder = { userId, name -> viewModel.sendReminder(userId, name) },
                    )
                isFather && selectedTab == 2 ->
                    ManageTab(state = state, viewModel = viewModel)
            }
        }
    }

    if (showAddGoalDialog) {
        AddGoalDialog(
            existing  = state.goalSettings,
            allUsers  = state.allUsers,
            onAdd     = { sunnahKey, assignedUserIds ->
                viewModel.addGoal(sunnahKey, assignedUserIds)
                showAddGoalDialog = false
            },
            onDismiss = { showAddGoalDialog = false },
        )
    }
}

// ── Today Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun TodayTab(
    state: PrayerUiState,
    currentUser: User?,
    viewModel: PrayerViewModel,
) {
    val userId = currentUser?.id ?: return
    val activeGoals = state.activeGoalsFor(userId)

    if (activeGoals.isEmpty()) {
        EmptyGoalsPlaceholder(isFather = currentUser.role == Role.FATHER)
        return
    }

    val completedCount = state.completedTodayCount(userId)
    val totalCount     = activeGoals.size
    val allDone        = completedCount == totalCount
    val today          = remember { System.currentTimeMillis() / PrayerUiState.DAY_MS }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { DailySummaryCard(completed = completedCount, total = totalCount) }
        item { SectionHeader(stringResource(R.string.prayer_today_goals)) }
        items(activeGoals, key = { it.id }) { goal ->
            val langTag = LocalContext.current.resources.configuration.locales[0].language
            val log     = state.todayLogFor(goal.sunnahKey, userId)
            val sunnah  = goal.sunnah ?: return@items
            val streak  = state.streakFor(goal.sunnahKey, userId)
            GoalProgressCard(
                sunnah         = sunnah,
                langTag        = langTag,
                completedCount = log?.completedCount ?: 0,
                isCompleted    = log?.isCompleted == true,
                streak         = streak,
                weekLogs       = state.weekLogs.filter { it.sunnahKey == goal.sunnahKey && it.userId == userId },
                onLog          = { viewModel.logPrayer(goal.sunnahKey) },
                onUndo         = { viewModel.undoPrayer(goal.sunnahKey) },
            )
        }
        // Achievement summary when all goals are done
        if (allDone) {
            item { AllDoneAchievements(state = state, userId = userId) }
        }

        // ── My History ────────────────────────────────────────────────────
        item { SectionHeader(stringResource(R.string.prayer_my_history)) }
        item { WeeklyBarChart(userId = userId, state = state, today = today) }
        item { SectionHeader(stringResource(R.string.prayer_my_monthly)) }
        item { MonthlyHeatmap(userId = userId, state = state, today = today) }
        item { SectionHeader(stringResource(R.string.prayer_my_quarterly)) }
        item { QuarterlyHeatmap(userId = userId, state = state, today = today) }
    }
}

@Composable
private fun EmptyGoalsPlaceholder(isFather: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("✨", fontSize = 48.sp)
            Text(
                stringResource(R.string.prayer_no_goals),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(if (isFather) R.string.prayer_no_goals_manage_hint else R.string.prayer_no_goals_hint),
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun DailySummaryCard(completed: Int, total: Int) {
    val allDone = completed == total && total > 0

    // Subtle pulse animation when all done
    val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue  = if (allDone) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .scale(scale),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (allDone) PrayerGreen else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (allDone) PrayerGold
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (allDone) {
                    Text("🏆", fontSize = 24.sp)
                } else {
                    Text(
                        "$completed/$total",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (allDone) stringResource(R.string.prayer_all_done)
                    else stringResource(R.string.prayer_today_progress),
                    style     = MaterialTheme.typography.titleSmall,
                    color     = if (allDone) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!allDone) {
                    Text(
                        stringResource(R.string.prayer_goals_remaining, total - completed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress            = { if (total > 0) completed.toFloat() / total else 0f },
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(6.dp)
                        .clip(CircleShape),
                    color               = if (allDone) PrayerGoldLight else PrayerGreenLight,
                    trackColor          = if (allDone) PrayerGreen.copy(alpha = 0.5f)
                                         else MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalProgressCard(
    sunnah: SunnahGoal,
    langTag: String,
    completedCount: Int,
    isCompleted: Boolean,
    streak: Int,
    weekLogs: List<com.familyhome.app.domain.model.PrayerLog>,
    onLog: () -> Unit,
    onUndo: () -> Unit,
) {
    var showHadithSheet by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isCompleted) PrayerGreen.copy(alpha = 0.08f)
                             else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row: circle + title + action buttons
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompletionCircle(completedCount = completedCount, isCompleted = isCompleted)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sunnah.localizedTitle(langTag),
                        style    = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$completedCount/${sunnah.dailyTarget} ${sunnah.localizedUnit(langTag)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCompleted) PrayerGreenLight
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Streak badge
                if (streak >= 3) {
                    StreakBadge(streak = streak)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Hadith / reward info button
                    IconButton(
                        onClick  = { showHadithSheet = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = "View hadith",
                            tint     = PrayerGreenLight.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    if (completedCount > 0) {
                        OutlinedIconButton(
                            onClick  = onUndo,
                            modifier = Modifier.size(36.dp),
                            border   = ButtonDefaults.outlinedButtonBorder(),
                        ) {
                            Icon(Icons.Default.Remove, "Undo", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (!isCompleted) {
                        IconButton(
                            onClick  = onLog,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrayerGreenLight),
                        ) {
                            Icon(Icons.Default.Add, "Log", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 7-day streak dots
            Spacer(Modifier.height(10.dp))
            WeekStreak(weekLogs = weekLogs)
        }
    }

    // Hadith + reward bottom sheet
    if (showHadithSheet) {
        HadithBottomSheet(
            sunnah      = sunnah,
            langTag     = langTag,
            isCompleted = isCompleted,
            onDismiss   = { showHadithSheet = false },
        )
    }
}

@Composable
private fun CompletionCircle(completedCount: Int, isCompleted: Boolean) {
    Box(
        modifier         = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (isCompleted) PrayerGreenLight
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (!isCompleted)
                    Modifier.border(1.5.dp, PrayerGreenLight.copy(alpha = 0.4f), CircleShape)
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
}

@Composable
private fun StreakBadge(streak: Int) {
    val (icon, color) = when {
        streak >= 30 -> "🔥" to PrayerGold
        streak >= 7  -> "⚡" to PrayerGreenLight
        else         -> "✅" to PrayerGreenLight.copy(alpha = 0.7f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(icon, fontSize = 11.sp)
        Text(
            "$streak",
            style     = MaterialTheme.typography.labelSmall,
            color     = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RewardBadge(sunnah: SunnahGoal, langTag: String) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PrayerGold.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(sunnah.rewardIcon, fontSize = 16.sp)
        Text(
            sunnah.localizedReward(langTag),
            style      = MaterialTheme.typography.labelMedium,
            color      = PrayerGold,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HadithBottomSheet(
    sunnah: SunnahGoal,
    langTag: String,
    isCompleted: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = MaterialTheme.colorScheme.surface,
        dragHandle        = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title
            Text(
                "${sunnah.rewardIcon}  ${sunnah.localizedTitle(langTag)}",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // Reward banner — always shown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrayerGold.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(sunnah.rewardIcon, fontSize = 22.sp)
                Column {
                    Text(
                        stringResource(R.string.prayer_hadith_reward),
                        style = MaterialTheme.typography.labelSmall,
                        color = PrayerGold.copy(alpha = 0.7f),
                    )
                    Text(
                        sunnah.localizedReward(langTag),
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = PrayerGold,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Completed indicator
            if (isCompleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrayerGreen.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint     = PrayerGreenLight,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.prayer_hadith_completed_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = PrayerGreenLight,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            // Full hadith
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.prayer_hadith_label),
                    style      = MaterialTheme.typography.labelMedium,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "\"${sunnah.localizedHadith(langTag)}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "— ${sunnah.localizedSource(langTag)}",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = PrayerGreenLight,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Reminder time window (if applicable)
            sunnah.reminderWindowLabel()?.let { window ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "${stringResource(R.string.prayer_hadith_time_window)}: $window",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekStreak(
    weekLogs: List<com.familyhome.app.domain.model.PrayerLog>,
) {
    val today     = Calendar.getInstance().run {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis / (24 * 60 * 60 * 1000L)
    }
    val dayNames  = listOf("M", "T", "W", "T", "F", "S", "S")
    val todayDow  = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7)

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (i in 0..6) {
            val dayOffset = i - todayDow
            val epochDay  = today + dayOffset
            val isFuture  = dayOffset > 0
            val done      = !isFuture && weekLogs.any { it.epochDay == epochDay && it.isCompleted }

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
                    if (done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
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

@Composable
private fun AllDoneAchievements(state: PrayerUiState, userId: String) {
    val langTag     = LocalContext.current.resources.configuration.locales[0].language
    val activeGoals = state.activeGoalsFor(userId)
    val completedSunnahs = activeGoals.mapNotNull { it.sunnah }.filter { sunnah ->
        state.todayLogFor(sunnah.name, userId)?.isCompleted == true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = PrayerGreen),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "🏆 ${stringResource(R.string.prayer_achievements_today)}",
                style      = MaterialTheme.typography.titleSmall,
                color      = PrayerGoldLight,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.prayer_achievements_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            completedSunnahs.forEach { sunnah ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(sunnah.rewardIcon, fontSize = 18.sp)
                    Column {
                        Text(
                            sunnah.localizedTitle(langTag),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        Text(
                            sunnah.localizedReward(langTag),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrayerGoldLight,
                        )
                    }
                }
            }
        }
    }
}

// ── Family Summary Card ───────────────────────────────────────────────────────

@Composable
private fun FamilySummaryCard(
    state: PrayerUiState,
    period: ProgressPeriod,
    today: Long,
) {
    if (state.allUsers.isEmpty()) return

    val (icon, summaryText, rate) = when (period) {
        ProgressPeriod.TODAY -> {
            val completedAll = state.allUsers.count { member ->
                val goals = state.activeGoalsFor(member.id)
                goals.isNotEmpty() && state.completedTodayCount(member.id) == goals.size
            }
            val total = state.allUsers.size
            Triple(
                if (completedAll == total) "🏆" else "📊",
                stringResource(R.string.prayer_family_summary_all, completedAll, total),
                if (total > 0) completedAll.toFloat() / total else 0f,
            )
        }
        else -> {
            val periodDays = (period.days + 1).toInt()
            val avgRate = state.allUsers.map { member ->
                state.completedDaysInPeriod(member.id, today - period.days, today).toFloat() / periodDays
            }.average().toFloat()
            Triple(
                "📈",
                stringResource(R.string.prayer_family_summary_avg, (avgRate * 100).toInt()),
                avgRate,
            )
        }
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (rate >= 1f) PrayerGreen else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(icon, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summaryText,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (rate >= 1f) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress   = { rate },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(5.dp)
                        .clip(CircleShape),
                    color      = if (rate >= 1f) PrayerGoldLight else PrayerGreenLight,
                    trackColor = if (rate >= 1f) PrayerGreen.copy(alpha = 0.5f)
                                 else MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

// ── Family Progress Line Chart ────────────────────────────────────────────────

@Composable
private fun FamilyProgressLineChart(
    state: PrayerUiState,
    period: ProgressPeriod,
    today: Long,
) {
    if (state.allUsers.isEmpty()) return

    // ── Build per-member series data ──────────────────────────────────────────
    data class SeriesResult(
        val series: List<List<FloatEntry>>,
        val xLabels: List<String>,
    )

    val result = remember(state.monthLogs, state.allUsers, period, today) {
        when (period) {
            ProgressPeriod.WEEK -> {
                val series = state.allUsers.map { member ->
                    (0..6).map { i ->
                        FloatEntry(i.toFloat(), state.dailyCompletionRate(member.id, today - 6 + i) * 100f)
                    }
                }
                val labels = (0..6).map { i ->
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = (today - 6 + i) * PrayerUiState.DAY_MS
                    listOf("M", "T", "W", "T", "F", "S", "S")[(cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7]
                }
                SeriesResult(series, labels)
            }
            ProgressPeriod.MONTH -> {
                val series = state.allUsers.map { member ->
                    (0..29).map { i ->
                        FloatEntry(i.toFloat(), state.dailyCompletionRate(member.id, today - 29 + i) * 100f)
                    }
                }
                // Label every 5th day to avoid crowding
                val labels = (0..29).map { i ->
                    if (i % 5 == 0) {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = (today - 29 + i) * PrayerUiState.DAY_MS
                        "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}"
                    } else ""
                }
                SeriesResult(series, labels)
            }
            ProgressPeriod.THREE_MONTHS -> {
                // Aggregate into 13 weekly averages to keep the chart readable
                val weeks = 13
                val series = state.allUsers.map { member ->
                    (0 until weeks).map { w ->
                        val weekStart = today - (weeks - w) * 7
                        val avg = (0..6).map { d ->
                            state.dailyCompletionRate(member.id, weekStart + d)
                        }.average().toFloat() * 100f
                        FloatEntry(w.toFloat(), avg)
                    }
                }
                val labels = (0 until weeks).map { w -> "W${w + 1}" }
                SeriesResult(series, labels)
            }
            else -> SeriesResult(emptyList(), emptyList())
        }
    }

    if (result.series.isEmpty()) return

    // ── Build Vico model producer ─────────────────────────────────────────────
    val modelProducer = remember { ChartEntryModelProducer() }
    LaunchedEffect(result) {
        modelProducer.setEntries(result.series)
    }

    // ── Line specs — one per family member ───────────────────────────────────
    val lines = remember(state.allUsers.size) {
        state.allUsers.mapIndexed { i, _ ->
            LineChart.LineSpec(
                lineColor        = MemberLineColors[i % MemberLineColors.size].toArgb(),
                lineThicknessDp  = 2f,
            )
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    val xLabels = result.xLabels
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.prayer_family_trend_detail),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Chart(
                chart = lineChart(lines = lines),
                chartModelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                startAxis = rememberStartAxis(
                    valueFormatter = { value, _ -> "${value.toInt()}%" },
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ -> xLabels.getOrElse(value.toInt()) { "" } },
                ),
            )

            // ── Legend ────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.allUsers.forEachIndexed { i, member ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MemberLineColors[i % MemberLineColors.size]),
                        )
                        Text(
                            member.name.split(" ").first(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Family Stats Tab ──────────────────────────────────────────────────────────

@Composable
private fun FamilyStatsTab(
    state: PrayerUiState,
    currentUser: User?,
    onSendReminder: (userId: String, name: String) -> Unit,
) {
    val userId  = currentUser?.id ?: return
    val today   = remember { System.currentTimeMillis() / PrayerUiState.DAY_MS }
    val langTag = LocalContext.current.resources.configuration.locales[0].language

    var selectedPeriod   by remember { mutableStateOf(ProgressPeriod.TODAY) }
    var expandedMemberId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding      = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── Period filter ─────────────────────────────────────────────────
        item {
            PeriodFilterChips(
                selected = selectedPeriod,
                onSelect = { period ->
                    selectedPeriod   = period
                    expandedMemberId = null
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ── Family summary card ───────────────────────────────────────────
        item { FamilySummaryCard(state = state, period = selectedPeriod, today = today) }

        // ── Family Progress ───────────────────────────────────────────────
        item {
            SectionHeader(
                if (selectedPeriod == ProgressPeriod.TODAY)
                    stringResource(R.string.prayer_family_today)
                else
                    stringResource(R.string.prayer_family_progress),
            )
        }
        items(state.allUsers, key = { it.id }) { member ->
            val fromDay      = today - selectedPeriod.days
            val periodDays   = (selectedPeriod.days + 1).toInt()
            val isMe         = member.id == userId
            val activeGoals  = state.activeGoalsFor(member.id)
            val isExpanded   = expandedMemberId == member.id

            val (primaryLabel, rate) = when (selectedPeriod) {
                ProgressPeriod.TODAY -> {
                    val completed = state.completedTodayCount(member.id)
                    val total     = activeGoals.size
                    val r         = if (total > 0) completed.toFloat() / total else 0f
                    "$completed/$total goals" to r
                }
                else -> {
                    val completedDays = state.completedDaysInPeriod(member.id, fromDay, today)
                    val r             = if (periodDays > 0) completedDays.toFloat() / periodDays else 0f
                    "$completedDays/$periodDays ${stringResource(R.string.prayer_days_completed)}" to r
                }
            }

            FamilyMemberProgressRow(
                member        = member,
                primaryLabel  = primaryLabel,
                rate          = rate,
                isMe          = isMe,
                isExpanded    = isExpanded,
                onToggleExpand = { expandedMemberId = if (isExpanded) null else member.id },
                onSendReminder = if (!isMe && selectedPeriod == ProgressPeriod.TODAY && rate < 1f)
                    { -> onSendReminder(member.id, member.name) }
                else null,
            )
            // ── Expandable per-goal breakdown ─────────────────────────────
            if (isExpanded) {
                GoalBreakdown(
                    member   = member,
                    state    = state,
                    today    = today,
                    fromDay  = fromDay,
                    period   = selectedPeriod,
                    langTag  = langTag,
                )
            }
        }

        // ── Family Progress Trend (line chart, hidden for TODAY) ──────────
        if (selectedPeriod != ProgressPeriod.TODAY) {
            item { SectionHeader(stringResource(R.string.prayer_family_trend)) }
            item {
                FamilyProgressLineChart(
                    state  = state,
                    period = selectedPeriod,
                    today  = today,
                )
            }
        }

        // ── Achievements ──────────────────────────────────────────────────
        val earnedRewards = state.activeGoalsFor(userId).mapNotNull { it.sunnah }.filter { sunnah ->
            state.todayLogFor(sunnah.name, userId)?.isCompleted == true
        }
        if (earnedRewards.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.prayer_achievements_today)) }
            items(earnedRewards) { sunnah ->
                AchievementRow(sunnah = sunnah, langTag = langTag, state = state, userId = userId)
            }
        }
    }
}

// ── Period filter chips ───────────────────────────────────────────────────────

@Composable
private fun PeriodFilterChips(
    selected: ProgressPeriod,
    onSelect: (ProgressPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier              = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick  = { onSelect(period) },
                label    = { Text(stringResource(period.labelRes), style = MaterialTheme.typography.labelMedium) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor  = PrayerGreenLight,
                    selectedLabelColor      = Color.White,
                ),
            )
        }
    }
}

// ── Family member progress row (with expansion) ───────────────────────────────

@Composable
private fun FamilyMemberProgressRow(
    member: User,
    primaryLabel: String,
    rate: Float,
    isMe: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSendReminder: (() -> Unit)?,
) {
    Column {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avatar (initials)
            Box(
                modifier         = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isMe) PrayerGreenLight else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.name.firstOrNull()?.uppercase() ?: "?",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${member.name}${if (isMe) " (${stringResource(R.string.prayer_me)})" else ""}",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        primaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rate >= 1f) PrayerGreenLight
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress   = { rate },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(6.dp)
                        .clip(CircleShape),
                    color      = when {
                        rate >= 1f   -> PrayerGreenLight
                        rate >= 0.5f -> PrayerGoldLight
                        else         -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // Expand chevron
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(R.string.prayer_goal_details),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )

            // Remind button — only for other members who haven't completed yet
            if (onSendReminder != null) {
                IconButton(
                    onClick  = onSendReminder,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PrayerGreenLight.copy(alpha = 0.12f)),
                ) {
                    Icon(
                        Icons.Default.NotificationAdd,
                        contentDescription = stringResource(R.string.prayer_send_reminder),
                        tint     = PrayerGreenLight,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
    }
}

// ── Per-goal breakdown (shown when member row is expanded) ────────────────────

@Composable
private fun GoalBreakdown(
    member: User,
    state: PrayerUiState,
    today: Long,
    fromDay: Long,
    period: ProgressPeriod,
    langTag: String,
) {
    val activeGoals = state.activeGoalsFor(member.id)
    if (activeGoals.isEmpty()) return

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.prayer_goal_details),
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            activeGoals.forEach { goal ->
                val sunnah = goal.sunnah ?: return@forEach
                val (progressLabel, isDone) = when (period) {
                    ProgressPeriod.TODAY -> {
                        val log      = state.todayLogFor(sunnah.name, member.id)
                        val count    = log?.completedCount ?: 0
                        val target   = sunnah.dailyTarget
                        val unitStr  = sunnah.localizedUnit(langTag)
                        "$count/$target $unitStr" to (log?.isCompleted == true)
                    }
                    else -> {
                        val periodDays   = (period.days + 1).toInt()
                        val total        = state.totalCountForPeriod(member.id, sunnah.name, fromDay, today)
                        val maxTotal     = sunnah.dailyTarget * periodDays
                        val unitStr      = sunnah.localizedUnit(langTag)
                        "$total/$maxTotal $unitStr" to (total >= maxTotal)
                    }
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(sunnah.rewardIcon, fontSize = 14.sp)
                    Text(
                        sunnah.localizedTitle(langTag),
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDone) PrayerGreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isDone) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (isDone) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint     = PrayerGreenLight,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── 3-Month heatmap ───────────────────────────────────────────────────────────

@Composable
private fun QuarterlyHeatmap(userId: String, state: PrayerUiState, today: Long) {
    // 90 cells in a 15-column × 6-row grid
    val rates = (0..89).reversed().map { daysAgo ->
        state.dailyCompletionRate(userId, today - daysAgo)
    }

    val greenLight  = PrayerGreenLight
    val goldColor   = PrayerGold
    val surfacePale = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.prayer_last_90_days),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                val cols     = 15
                val rows     = 6
                val cellSize = size.width / cols
                val radius   = cellSize * 0.33f

                rates.forEachIndexed { index, rate ->
                    val col   = index % cols
                    val row   = index / cols
                    val cx    = col * cellSize + cellSize / 2
                    val cy    = row * (size.height / rows) + (size.height / rows) / 2
                    val color = when {
                        rate >= 1f -> goldColor
                        rate > 0f  -> greenLight.copy(alpha = 0.3f + rate * 0.7f)
                        else       -> surfacePale
                    }
                    drawCircle(color = color, radius = radius, center = Offset(cx, cy))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(color = surfacePale,                  label = stringResource(R.string.prayer_legend_none))
                LegendDot(color = greenLight.copy(alpha = 0.6f), label = stringResource(R.string.prayer_legend_partial))
                LegendDot(color = goldColor,                    label = stringResource(R.string.prayer_legend_complete))
            }
        }
    }
}

@Composable
private fun WeeklyBarChart(userId: String, state: PrayerUiState, today: Long) {
    val cal      = Calendar.getInstance()
    val todayDow = ((cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7)
    val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")

    val bars = (0..6).map { i ->
        val dayOffset = i - todayDow
        val epochDay  = today + dayOffset
        val isFuture  = dayOffset > 0
        val rate      = if (isFuture) 0f else state.dailyCompletionRate(userId, epochDay)
        Triple(dayNames[i], rate, isFuture)
    }

    val barColor   = PrayerGreenLight
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val goldColor  = PrayerGold

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val barCount  = bars.size
                val totalGap  = size.width * 0.3f
                val barWidth  = (size.width - totalGap) / barCount
                val gapWidth  = totalGap / (barCount + 1)

                bars.forEachIndexed { i, (_, rate, isFuture) ->
                    val left      = gapWidth + i * (barWidth + gapWidth)
                    val barHeight = (size.height * rate).coerceAtLeast(if (isFuture) 0f else 4.dp.toPx())
                    val color     = when {
                        isFuture    -> emptyColor
                        rate >= 1f  -> goldColor
                        rate > 0f   -> barColor.copy(alpha = 0.5f + rate * 0.5f)
                        else        -> emptyColor
                    }
                    drawRoundRect(
                        color       = color,
                        topLeft     = Offset(left, size.height - barHeight),
                        size        = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }

            // Day labels
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                bars.forEach { (label, rate, isFuture) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            !isFuture && rate >= 1f -> PrayerGold
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyHeatmap(userId: String, state: PrayerUiState, today: Long) {
    val rates = (0..29).reversed().map { daysAgo ->
        state.dailyCompletionRate(userId, today - daysAgo)
    }

    val greenLight  = PrayerGreenLight
    val goldColor   = PrayerGold
    val surfacePale = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.prayer_last_30_days),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val cols      = 10
                val rows      = 3
                val cellSize  = size.width / cols
                val radius    = cellSize * 0.35f

                rates.forEachIndexed { index, rate ->
                    val col = index % cols
                    val row = index / cols
                    val cx  = col * cellSize + cellSize / 2
                    val cy  = row * (size.height / rows) + (size.height / rows) / 2
                    val color = when {
                        rate >= 1f  -> goldColor
                        rate > 0f   -> greenLight.copy(alpha = 0.3f + rate * 0.7f)
                        else        -> surfacePale
                    }
                    drawCircle(color = color, radius = radius, center = Offset(cx, cy))
                }
            }
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(color = surfacePale, label = stringResource(R.string.prayer_legend_none))
                LegendDot(color = greenLight.copy(alpha = 0.6f), label = stringResource(R.string.prayer_legend_partial))
                LegendDot(color = goldColor, label = stringResource(R.string.prayer_legend_complete))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AchievementRow(sunnah: SunnahGoal, langTag: String, state: PrayerUiState, userId: String) {
    val streak = state.streakFor(sunnah.name, userId)
    ListItem(
        headlineContent = {
            Text("${sunnah.rewardIcon}  ${sunnah.localizedTitle(langTag)}", style = MaterialTheme.typography.bodyMedium)
        },
        supportingContent = {
            Text(sunnah.localizedReward(langTag), style = MaterialTheme.typography.bodySmall, color = PrayerGold)
        },
        trailingContent = {
            if (streak > 0) StreakBadge(streak = streak)
        },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
}

// ── Manage Tab (Father only) ───────────────────────────────────────────────────

@Composable
private fun ManageTab(
    state: PrayerUiState,
    viewModel: PrayerViewModel,
) {
    var addMemberGoalId by remember { mutableStateOf<String?>(null) }

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
                    setting   = setting,
                    allUsers  = state.allUsers,
                    onToggle  = { viewModel.toggleGoal(setting) },
                    onRemove  = { viewModel.removeGoal(setting) },
                    onToggleReminder = { viewModel.toggleReminder(setting) },
                    onAddMember = { addMemberGoalId = setting.id },
                )
            }
        }
    }

    // Add-member dialog
    val targetGoalId = addMemberGoalId
    if (targetGoalId != null) {
        val setting = state.goalSettings.firstOrNull { it.id == targetGoalId }
        if (setting != null) {
            AddMemberDialog(
                setting   = setting,
                allUsers  = state.allUsers,
                onAssign  = { userId ->
                    viewModel.addAssigneeToGoal(targetGoalId, userId)
                    addMemberGoalId = null
                },
                onDismiss = { addMemberGoalId = null },
            )
        }
    }
}

@Composable
private fun ManageGoalItem(
    setting: PrayerGoalSetting,
    allUsers: List<User>,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onToggleReminder: () -> Unit,
    onAddMember: () -> Unit,
) {
    val sunnah           = setting.sunnah ?: return
    val langTag          = LocalContext.current.resources.configuration.locales[0].language
    val allFamilyLabel   = stringResource(R.string.prayer_all_family)
    val isFullyAssigned  = setting.isFullyAssigned(allUsers.map { it.id })
    val hasReminderTime  = sunnah.reminderHour != null

    val assigneeLabel = when {
        setting.assignedUserIds == null -> allFamilyLabel
        setting.assignedUserIds.isEmpty() -> allFamilyLabel
        else -> setting.assignedUserIds.mapNotNull { id ->
            allUsers.firstOrNull { it.id == id }?.name
        }.joinToString(", ").ifEmpty { allFamilyLabel }
    }

    Column {
        ListItem(
            headlineContent = {
                Text(sunnah.localizedTitle(langTag), style = MaterialTheme.typography.bodyLarge)
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${stringResource(R.string.prayer_assigned_to_label)} $assigneeLabel",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (hasReminderTime) {
                        Text(
                            "⏰ ${sunnah.reminderWindowLabel() ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (setting.reminderEnabled) PrayerGreenLight
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            },
            leadingContent = {
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (setting.isEnabled) PrayerGreen
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(sunnah.rewardIcon, fontSize = 18.sp)
                }
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked         = setting.isEnabled,
                        onCheckedChange = { onToggle() },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrayerGreenLight,
                        ),
                    )
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )

        // Action buttons row
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Reminder toggle (only for sunnahs with a fixed time window)
            if (hasReminderTime) {
                OutlinedButton(
                    onClick   = onToggleReminder,
                    modifier  = Modifier.weight(1f),
                    colors    = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (setting.reminderEnabled) PrayerGreenLight
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        if (setting.reminderEnabled) Icons.Default.NotificationsActive
                        else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (setting.reminderEnabled) R.string.prayer_reminder_on
                            else R.string.prayer_reminder_off
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Add member button (only if not fully assigned)
            if (!isFullyAssigned) {
                OutlinedButton(
                    onClick  = onAddMember,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrayerGreenLight),
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.prayer_add_member), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
    }
}

// ── Add Goal Dialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalDialog(
    existing: List<PrayerGoalSetting>,
    allUsers: List<User>,
    onAdd: (sunnahKey: String, assignedUserIds: List<String>?) -> Unit,
    onDismiss: () -> Unit,
) {
    val existingKeys = existing.map { it.sunnahKey }.toSet()
    val available    = SunnahGoal.entries.filter { it.name !in existingKeys }
    val langTag      = LocalContext.current.resources.configuration.locales[0].language

    var selectedSunnah   by remember { mutableStateOf<SunnahGoal?>(null) }
    var selectedUserId   by remember { mutableStateOf<String?>(null) } // null = all family
    var sunnahExpanded   by remember { mutableStateOf(false) }
    var memberExpanded   by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title  = { Text(stringResource(R.string.prayer_add_goal)) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Sunnah picker
                ExposedDropdownMenuBox(
                    expanded         = sunnahExpanded,
                    onExpandedChange = { sunnahExpanded = it },
                ) {
                    OutlinedTextField(
                        value         = selectedSunnah?.localizedTitle(langTag)
                                        ?: if (langTag == "en") "Select a sunnah practice" else "Pilih amalan sunnah",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Sunnah Practice") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sunnahExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded         = sunnahExpanded,
                        onDismissRequest = { sunnahExpanded = false },
                    ) {
                        if (available.isEmpty()) {
                            DropdownMenuItem(
                                text    = { Text(stringResource(R.string.prayer_all_added)) },
                                onClick = { sunnahExpanded = false },
                                enabled = false,
                            )
                        } else {
                            available.forEach { sunnah ->
                                DropdownMenuItem(
                                    text    = { Text("${sunnah.rewardIcon}  ${sunnah.localizedTitle(langTag)}") },
                                    onClick = { selectedSunnah = sunnah; sunnahExpanded = false },
                                )
                            }
                        }
                    }
                }

                // Hadith + reward preview
                selectedSunnah?.let { sunnah ->
                    Card(
                        shape  = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "\"${sunnah.hadith.take(150)}${if (sunnah.hadith.length > 150) "…" else ""}\"",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(sunnah.source, style = MaterialTheme.typography.labelSmall, color = PrayerGreenLight)
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PrayerGold.copy(alpha = 0.1f))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text(sunnah.rewardIcon, fontSize = 14.sp)
                                Text(
                                    sunnah.localizedReward(langTag),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrayerGold,
                                )
                            }
                            sunnah.reminderWindowLabel()?.let { window ->
                                Text(
                                    "⏰ $window",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrayerGreenLight,
                                )
                            }
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
                        value         = if (selectedUserId == null) allFamilyStr
                                        else allUsers.firstOrNull { it.id == selectedUserId }?.name ?: allFamilyStr,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text(stringResource(R.string.prayer_assign_to)) },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded         = memberExpanded,
                        onDismissRequest = { memberExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text    = { Text(allFamilyStr) },
                            onClick = { selectedUserId = null; memberExpanded = false },
                        )
                        allUsers.forEach { user ->
                            DropdownMenuItem(
                                text    = { Text("${user.name} (${user.role.displayName})") },
                                onClick = { selectedUserId = user.id; memberExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    selectedSunnah?.let { sunnah ->
                        val assignedUserIds = selectedUserId?.let { listOf(it) }
                        onAdd(sunnah.name, assignedUserIds)
                    }
                },
                enabled  = selectedSunnah != null,
                colors   = ButtonDefaults.buttonColors(containerColor = PrayerGreenLight),
            ) { Text(stringResource(R.string.prayer_add_goal_btn)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ── Add Member Dialog ─────────────────────────────────────────────────────────

@Composable
private fun AddMemberDialog(
    setting: PrayerGoalSetting,
    allUsers: List<User>,
    onAssign: (userId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val langTag     = LocalContext.current.resources.configuration.locales[0].language
    val sunnah      = setting.sunnah ?: return
    val unassigned  = allUsers.filter { user ->
        setting.assignedUserIds != null && user.id !in setting.assignedUserIds
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title  = {
            Text(stringResource(R.string.prayer_add_member_title))
        },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${sunnah.rewardIcon}  ${sunnah.localizedTitle(langTag)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (unassigned.isEmpty()) {
                    Text(
                        stringResource(R.string.prayer_all_assigned),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    unassigned.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAssign(user.id) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    user.name.firstOrNull()?.uppercase() ?: "?",
                                    style  = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column {
                                Text(user.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    user.role.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Default.PersonAdd,
                                null,
                                tint     = PrayerGreenLight,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

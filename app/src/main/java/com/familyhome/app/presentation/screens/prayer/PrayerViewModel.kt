package com.familyhome.app.presentation.screens.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.notification.PrayerReminderScheduler
import com.familyhome.app.data.sync.DeletionTracker
import com.familyhome.app.data.sync.MemberPresenceTracker
import com.familyhome.app.data.sync.PrayerReminderStore
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.domain.model.PrayerReminderDto
import com.familyhome.app.domain.model.PrayerGoalSetting
import com.familyhome.app.domain.model.PrayerLog
import com.familyhome.app.domain.model.SunnahGoal
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.PrayerRepository
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class PrayerUiState(
    val goalSettings: List<PrayerGoalSetting> = emptyList(),
    val todayLogs: List<PrayerLog> = emptyList(),
    /** 30 days of logs for all users — used for weekly bar chart, monthly heatmap, and streaks. */
    val monthLogs: List<PrayerLog> = emptyList(),
    val currentUser: User? = null,
    val allUsers: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Non-null briefly after sending a reminder — consumed by the UI to show a Snackbar. */
    val reminderSentTo: String? = null,
    /**
     * IDs of family members currently online on the same Wi-Fi network.
     * Populated from NSD discovery (real-time) and sync-based presence.
     */
    val onlineUserIds: Set<String> = emptySet(),
) {
    // ── Active goals ─────────────────────────────────────────────────────────

    fun activeGoalsFor(userId: String): List<PrayerGoalSetting> =
        goalSettings.filter { it.isEnabled && it.isAssignedTo(userId) }

    // ── Log helpers ──────────────────────────────────────────────────────────

    fun todayLogFor(sunnahKey: String, userId: String): PrayerLog? =
        todayLogs.firstOrNull { it.sunnahKey == sunnahKey && it.userId == userId }

    fun completedTodayCount(userId: String): Int =
        activeGoalsFor(userId).count { goal ->
            todayLogFor(goal.sunnahKey, userId)?.isCompleted == true
        }

    // ── Derived week logs (last 7 days from monthLogs) ───────────────────────

    val weekLogs: List<PrayerLog>
        get() {
            val today = System.currentTimeMillis() / DAY_MS
            return monthLogs.filter { it.epochDay >= today - 6 }
        }

    // ── Stats helpers ────────────────────────────────────────────────────────

    /**
     * Completion rate (0.0–1.0) for [userId] on [epochDay].
     * Returns 0 if there are no active goals on that day.
     */
    fun dailyCompletionRate(userId: String, epochDay: Long): Float {
        val active = activeGoalsFor(userId)
        if (active.isEmpty()) return 0f
        val completed = active.count { goal ->
            monthLogs.firstOrNull {
                it.sunnahKey == goal.sunnahKey && it.userId == userId && it.epochDay == epochDay
            }?.isCompleted == true
        }
        return completed.toFloat() / active.size
    }

    /**
     * Current streak (consecutive completed days up to today) for [userId] / [sunnahKey].
     * Checks up to 30 days back (month window).
     */
    fun streakFor(sunnahKey: String, userId: String): Int {
        val today = System.currentTimeMillis() / DAY_MS
        var streak = 0
        var day = today
        while (day >= today - 29) {
            val done = monthLogs.any { it.sunnahKey == sunnahKey && it.userId == userId && it.epochDay == day && it.isCompleted }
            if (done) { streak++; day-- } else break
        }
        return streak
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

@HiltViewModel
class PrayerViewModel @Inject constructor(
    private val prayerRepository: PrayerRepository,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFamilyMembersUseCase: GetFamilyMembersUseCase,
    private val deletionTracker: DeletionTracker,
    private val reminderScheduler: PrayerReminderScheduler,
    private val prayerReminderStore: PrayerReminderStore,
    private val syncRepository: SyncRepositoryImpl,
    private val presenceTracker: MemberPresenceTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(PrayerUiState())
    val state = _state.asStateFlow()

    init {
        // Load current user first, then stream goals so isLoading becomes false only when we
        // know who the user is — avoids flash of empty state for the leader.
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            _state.update { it.copy(currentUser = user) }
            prayerRepository.getAllGoalSettings().collect { settings ->
                _state.update { it.copy(goalSettings = settings, isLoading = false) }
            }
        }
        viewModelScope.launch {
            getFamilyMembersUseCase().collect { members ->
                _state.update { it.copy(allUsers = members) }
            }
        }
        viewModelScope.launch {
            prayerRepository.getLogsForDay(todayEpochDay()).collect { logs ->
                _state.update { it.copy(todayLogs = logs) }
            }
        }
        // 30 days covers weekly chart + monthly heatmap + streak calculations
        viewModelScope.launch {
            val monthAgo = todayEpochDay() - 29L
            prayerRepository.getLogsSince(monthAgo).collect { logs ->
                _state.update { it.copy(monthLogs = logs) }
            }
        }
        // Track which family members are online on the same network (real-time)
        viewModelScope.launch {
            presenceTracker.networkOnlineUserIds.collect { onlineIds ->
                _state.update { it.copy(onlineUserIds = onlineIds) }
            }
        }
    }

    // ── Logging ──────────────────────────────────────────────────────────────

    /** Increment today's completion count for the current user (capped at dailyTarget). */
    fun logPrayer(sunnahKey: String) {
        val user   = _state.value.currentUser ?: return
        val sunnah = SunnahGoal.entries.firstOrNull { it.name == sunnahKey } ?: return
        val today  = todayEpochDay()
        val existing = _state.value.todayLogFor(sunnahKey, user.id)
        viewModelScope.launch {
            prayerRepository.upsertLog(
                PrayerLog(
                    id             = existing?.id ?: UUID.randomUUID().toString(),
                    userId         = user.id,
                    sunnahKey      = sunnahKey,
                    epochDay       = today,
                    completedCount = ((existing?.completedCount ?: 0) + 1).coerceAtMost(sunnah.dailyTarget),
                    loggedAt       = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Decrement today's completion count (minimum 0). */
    fun undoPrayer(sunnahKey: String) {
        val user     = _state.value.currentUser ?: return
        val existing = _state.value.todayLogFor(sunnahKey, user.id) ?: return
        viewModelScope.launch {
            prayerRepository.upsertLog(
                existing.copy(
                    completedCount = (existing.completedCount - 1).coerceAtLeast(0),
                    loggedAt       = System.currentTimeMillis(),
                )
            )
        }
    }

    // ── Goal management (leader only) ────────────────────────────────────────

    /** Enable or disable a sunnah goal. */
    fun toggleGoal(setting: PrayerGoalSetting) {
        viewModelScope.launch {
            prayerRepository.updateGoalSetting(setting.copy(isEnabled = !setting.isEnabled))
        }
    }

    /**
     * Add a new goal for [sunnahKey].
     * [assignedUserIds] = null → all family. Single-element list → specific member.
     */
    fun addGoal(sunnahKey: String, assignedUserIds: List<String>?) {
        val user = _state.value.currentUser ?: return
        if (_state.value.goalSettings.any { it.sunnahKey == sunnahKey }) return
        viewModelScope.launch {
            prayerRepository.insertGoalSetting(
                PrayerGoalSetting(
                    id              = UUID.randomUUID().toString(),
                    sunnahKey       = sunnahKey,
                    isEnabled       = true,
                    assignedUserIds = assignedUserIds,
                    reminderEnabled = false,
                    createdBy       = user.id,
                    createdAt       = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * Add [newUserId] to the goal's assigned list.
     * No-op if the user is already assigned or the goal is assigned to all (null).
     */
    fun addAssigneeToGoal(goalId: String, newUserId: String) {
        val setting = _state.value.goalSettings.firstOrNull { it.id == goalId } ?: return
        if (setting.assignedUserIds == null) return // already "all family"
        if (newUserId in setting.assignedUserIds) return // already assigned
        val updated = setting.copy(
            assignedUserIds = setting.assignedUserIds + newUserId,
        )
        viewModelScope.launch { prayerRepository.updateGoalSetting(updated) }
    }

    /**
     * Toggle the daily reminder for a goal. Only has effect if the sunnah has a [SunnahGoal.reminderHour].
     */
    fun toggleReminder(setting: PrayerGoalSetting) {
        val sunnah = setting.sunnah ?: return
        val newEnabled = !setting.reminderEnabled
        viewModelScope.launch {
            prayerRepository.updateGoalSetting(setting.copy(reminderEnabled = newEnabled))
        }
        // Schedule or cancel the alarm
        val startHour   = sunnah.reminderHour ?: return
        val startMinute = sunnah.reminderMinute ?: 0
        if (newEnabled) {
            reminderScheduler.schedule(
                sunnahKey        = setting.sunnahKey,
                hour             = startHour,
                minute           = startMinute,
                windowStartHour  = startHour,
                windowStartMinute = startMinute,
                windowEndHour    = sunnah.reminderEndHour ?: -1,
                windowEndMinute  = sunnah.reminderEndMinute ?: 0,
            )
        } else {
            reminderScheduler.cancel(setting.sunnahKey)
        }
    }

    /** Delete a goal (leader only). Records deletion so it propagates via sync. */
    fun removeGoal(setting: PrayerGoalSetting) {
        viewModelScope.launch {
            prayerRepository.deleteGoalSetting(setting.id)
            deletionTracker.recordPrayerGoalDeletion(setting.id)
            reminderScheduler.cancel(setting.sunnahKey)
        }
    }

    /**
     * Send a prayer reminder to [targetUserId].
     *
     * When both devices are on the same Wi-Fi, the notification is delivered
     * immediately via a direct HTTP push — no sync cycle needed, the target
     * device doesn't need the app open. The reminder is also stored locally as
     * a sync-based fallback for when the direct push fails or the device is
     * temporarily unreachable.
     */
    fun sendReminder(targetUserId: String, targetUserName: String) {
        val sender = _state.value.currentUser ?: return
        val reminder = PrayerReminderDto(
            id           = UUID.randomUUID().toString(),
            targetUserId = targetUserId,
            sentByUserId = sender.id,
            sentByName   = sender.name,
            sentAt       = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            // Always store for sync-based delivery (fallback)
            prayerReminderStore.addReminder(reminder)
            // Also attempt immediate direct push if on same network
            syncRepository.sendDirectReminder(targetUserId, reminder)
            _state.update { it.copy(reminderSentTo = targetUserName) }
        }
    }

    fun clearReminderSent() = _state.update { it.copy(reminderSentTo = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    // ── Utilities ────────────────────────────────────────────────────────────

    private fun todayEpochDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / (24 * 60 * 60 * 1000L)
    }
}

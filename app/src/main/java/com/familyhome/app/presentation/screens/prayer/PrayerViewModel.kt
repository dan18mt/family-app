package com.familyhome.app.presentation.screens.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.notification.PrayerReminderScheduler
import com.familyhome.app.data.sync.DeletionTracker
import com.familyhome.app.data.sync.MemberPresenceTracker
import com.familyhome.app.data.sync.PrayerReminderStore
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.domain.helper.HijriCalendarHelper
import com.familyhome.app.domain.model.IslamicCalendarSunnah
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

/**
 * Status of an Islamic-calendar sunnah event relative to today.
 */
enum class IslamicEventStatus { ACTIVE, UPCOMING, PAST }

/**
 * Computed information for one [IslamicCalendarSunnah] event in the current Hijri year,
 * including how close it is to today and the family's progress.
 *
 * @param goalSetting       The [PrayerGoalSetting] if the leader has added this event as a
 *                          family goal; null if not yet added.
 * @param status            Whether the event is active now, upcoming, or recently past.
 * @param periodStartEpochDay Gregorian epoch-day when the valid period starts.
 * @param periodEndEpochDay   Gregorian epoch-day when the valid period ends (inclusive).
 * @param daysUntilStart    Days from today until the period starts (0 if already active/past).
 * @param daysUntilEnd      Days from today until the period ends (0 if past).
 * @param hijriYear         The Hijri year this instance refers to.
 */
data class IslamicEventInfo(
    val sunnah: IslamicCalendarSunnah,
    val goalSetting: PrayerGoalSetting?,
    val status: IslamicEventStatus,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val daysUntilStart: Int,
    val daysUntilEnd: Int,
    val hijriYear: Int,
)

data class PrayerUiState(
    val goalSettings: List<PrayerGoalSetting> = emptyList(),
    val todayLogs: List<PrayerLog> = emptyList(),
    /** 90 days of logs for all users — used for charts, heatmaps, streaks, and Islamic events. */
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
    /**
     * Keys ([IslamicCalendarSunnah.name]) of events whose valid period overlaps with today.
     * Used to filter Islamic calendar goals into the Today tab active list.
     */
    val activeIslamicEventKeys: Set<String> = emptySet(),
    /**
     * Computed information for all relevant Islamic calendar events this year —
     * active, upcoming (within 30 days), and recently past (within 90 days).
     */
    val islamicEvents: List<IslamicEventInfo> = emptyList(),
) {
    // ── Active goals ─────────────────────────────────────────────────────────

    /**
     * Goals that are enabled and assigned to [userId].
     * Islamic-calendar goals are included only while their Hijri period is active.
     */
    fun activeGoalsFor(userId: String): List<PrayerGoalSetting> =
        goalSettings.filter { setting ->
            setting.isEnabled &&
            setting.isAssignedTo(userId) &&
            (!setting.isIslamicCalendarEvent || setting.sunnahKey in activeIslamicEventKeys)
        }

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
     * Number of days within [fromEpochDay]..[toEpochDay] on which [userId] completed ALL
     * active goals. Used to drive weekly / monthly / quarterly family progress cards.
     */
    fun completedDaysInPeriod(userId: String, fromEpochDay: Long, toEpochDay: Long): Int {
        val active = activeGoalsFor(userId)
        if (active.isEmpty()) return 0
        var count = 0
        for (day in fromEpochDay..toEpochDay) {
            val allDone = active.all { goal ->
                monthLogs.any { it.sunnahKey == goal.sunnahKey && it.userId == userId && it.epochDay == day && it.isCompleted }
            }
            if (allDone) count++
        }
        return count
    }

    /**
     * Total accumulated count for [userId] / [sunnahKey] over [fromEpochDay]..[toEpochDay].
     * Shows "6/84 rakaat this week" for SUNNAH_RAWATIB (target=12, 7 days → max 84).
     */
    fun totalCountForPeriod(userId: String, sunnahKey: String, fromEpochDay: Long, toEpochDay: Long): Int =
        monthLogs
            .filter { it.userId == userId && it.sunnahKey == sunnahKey && it.epochDay in fromEpochDay..toEpochDay }
            .sumOf { it.completedCount }

    /**
     * Current streak (consecutive completed days up to today) for [userId] / [sunnahKey].
     * Checks up to 90 days back.
     */
    fun streakFor(sunnahKey: String, userId: String): Int {
        val today = System.currentTimeMillis() / DAY_MS
        var streak = 0
        var day = today
        while (day >= today - 89) {
            val done = monthLogs.any { it.sunnahKey == sunnahKey && it.userId == userId && it.epochDay == day && it.isCompleted }
            if (done) { streak++; day-- } else break
        }
        return streak
    }

    // ── Islamic calendar event helpers ──────────────────────────────────────

    /**
     * How many days [userId] has completed for an Islamic calendar event in its active period.
     *
     * Counts log entries with [PrayerLog.isCompleted] == true within
     * [startEpochDay]..[endEpochDay].
     */
    fun islamicEventCompletedDays(
        sunnahKey: String,
        userId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Int = monthLogs.count { log ->
        log.sunnahKey == sunnahKey &&
        log.userId    == userId    &&
        log.epochDay  in startEpochDay..endEpochDay &&
        log.isCompleted
    }

    /**
     * Number of family members who have completed [minDays] or more days for an event.
     */
    fun islamicEventFamilyCompletedCount(
        sunnahKey: String,
        startEpochDay: Long,
        endEpochDay: Long,
        minDays: Int = 1,
    ): Int = allUsers.count { user ->
        islamicEventCompletedDays(sunnahKey, user.id, startEpochDay, endEpochDay) >= minDays
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
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
                _state.update { s ->
                    // Attach goal settings to any already-computed Islamic calendar events
                    val updatedEvents = s.islamicEvents.map { info ->
                        info.copy(goalSetting = settings.firstOrNull { it.sunnahKey == info.sunnah.name })
                    }
                    s.copy(goalSettings = settings, isLoading = false, islamicEvents = updatedEvents)
                }
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
        // 90 days covers weekly chart, monthly heatmap, 3-month overview, and streak calculations
        viewModelScope.launch {
            prayerRepository.getLogsSince(todayEpochDay() - 89L).collect { logs ->
                _state.update { it.copy(monthLogs = logs) }
            }
        }
        // Track which family members are online on the same network (real-time)
        viewModelScope.launch {
            presenceTracker.networkOnlineUserIds.collect { onlineIds ->
                _state.update { it.copy(onlineUserIds = onlineIds) }
            }
        }
        // Compute Islamic calendar events once on startup (Hijri periods change at most daily)
        viewModelScope.launch {
            val events    = computeIslamicCalendarEvents()
            val activeKeys = events
                .filter { it.status == IslamicEventStatus.ACTIVE }
                .map { it.sunnah.name }
                .toSet()
            _state.update { it.copy(islamicEvents = events, activeIslamicEventKeys = activeKeys) }
        }
    }

    // ── Logging ──────────────────────────────────────────────────────────────

    /** Increment today's completion count for the current user (capped at dailyTarget). */
    fun logPrayer(sunnahKey: String) {
        val user     = _state.value.currentUser ?: return
        val sunnah   = SunnahGoal.entries.firstOrNull { it.name == sunnahKey } ?: return
        val today    = todayEpochDay()
        val existing = _state.value.todayLogFor(sunnahKey, user.id)
        val newCount = ((existing?.completedCount ?: 0) + 1).coerceAtMost(sunnah.dailyTarget)
        viewModelScope.launch {
            prayerRepository.upsertLog(
                PrayerLog(
                    id             = existing?.id ?: UUID.randomUUID().toString(),
                    userId         = user.id,
                    sunnahKey      = sunnahKey,
                    epochDay       = today,
                    completedCount = newCount,
                    loggedAt       = System.currentTimeMillis(),
                )
            )
            // Goal fully completed → dismiss any active notification and stop today's
            // alarm cycle. The alarm is rescheduled for tomorrow automatically.
            if (newCount >= sunnah.dailyTarget) {
                val hasReminder = _state.value.goalSettings
                    .firstOrNull { it.sunnahKey == sunnahKey }?.reminderEnabled == true
                if (hasReminder && sunnah.reminderHour != null) {
                    reminderScheduler.dismissNotification(sunnahKey)
                    reminderScheduler.cancel(sunnahKey)
                    // Restart daily cycle from tomorrow's window start
                    reminderScheduler.schedule(
                        sunnahKey         = sunnahKey,
                        hour              = sunnah.reminderHour,
                        minute            = sunnah.reminderMinute ?: 0,
                        windowStartHour   = sunnah.reminderHour,
                        windowStartMinute = sunnah.reminderMinute ?: 0,
                        windowEndHour     = sunnah.reminderEndHour ?: -1,
                        windowEndMinute   = sunnah.reminderEndMinute ?: 0,
                    )
                }
            }
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
     *
     * Uses an optimistic state update so the button responds immediately without
     * waiting for the DB round-trip — prevents the need for multiple taps.
     */
    fun toggleReminder(setting: PrayerGoalSetting) {
        val sunnah     = setting.sunnah ?: return
        val newEnabled = !setting.reminderEnabled
        // Optimistic UI update: reflect the new state before the DB write completes
        _state.update { s ->
            s.copy(goalSettings = s.goalSettings.map { g ->
                if (g.id == setting.id) g.copy(reminderEnabled = newEnabled) else g
            })
        }
        viewModelScope.launch {
            prayerRepository.updateGoalSetting(setting.copy(reminderEnabled = newEnabled))
            val startHour   = sunnah.reminderHour ?: return@launch
            val startMinute = sunnah.reminderMinute ?: 0
            if (newEnabled) {
                reminderScheduler.schedule(
                    sunnahKey         = setting.sunnahKey,
                    hour              = startHour,
                    minute            = startMinute,
                    windowStartHour   = startHour,
                    windowStartMinute = startMinute,
                    windowEndHour     = sunnah.reminderEndHour ?: -1,
                    windowEndMinute   = sunnah.reminderEndMinute ?: 0,
                )
            } else {
                reminderScheduler.cancel(setting.sunnahKey)
            }
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

    /**
     * Add an Islamic-calendar sunnah as a family goal.
     * Uses the same storage as daily sunnah goals — the key is just
     * [IslamicCalendarSunnah.name] stored in [PrayerGoalSetting.sunnahKey].
     */
    fun addIslamicGoal(sunnah: IslamicCalendarSunnah, assignedUserIds: List<String>?) {
        val user = _state.value.currentUser ?: return
        if (_state.value.goalSettings.any { it.sunnahKey == sunnah.name }) return
        viewModelScope.launch {
            prayerRepository.insertGoalSetting(
                PrayerGoalSetting(
                    id              = UUID.randomUUID().toString(),
                    sunnahKey       = sunnah.name,
                    isEnabled       = true,
                    assignedUserIds = assignedUserIds,
                    reminderEnabled = false,
                    createdBy       = user.id,
                    createdAt       = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Log one day of an Islamic-calendar ibadah for today. */
    fun logIslamicIbadah(sunnahKey: String) {
        val user    = _state.value.currentUser ?: return
        val today   = todayEpochDay()
        val existing = _state.value.todayLogFor(sunnahKey, user.id)
        // Cap at 1 per day (you either did it or you didn't)
        if (existing?.isCompleted == true) return
        viewModelScope.launch {
            prayerRepository.upsertLog(
                PrayerLog(
                    id             = existing?.id ?: UUID.randomUUID().toString(),
                    userId         = user.id,
                    sunnahKey      = sunnahKey,
                    epochDay       = today,
                    completedCount = 1,
                    loggedAt       = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Undo today's log for an Islamic-calendar ibadah. */
    fun undoIslamicIbadah(sunnahKey: String) {
        val user     = _state.value.currentUser ?: return
        val existing = _state.value.todayLogFor(sunnahKey, user.id) ?: return
        viewModelScope.launch {
            prayerRepository.upsertLog(
                existing.copy(completedCount = 0, loggedAt = System.currentTimeMillis())
            )
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

    /**
     * Computes [IslamicEventInfo] for every [IslamicCalendarSunnah] that is active,
     * upcoming (within 30 days), or recently past (within 90 days) relative to today.
     *
     * For monthly-recurring events (Ayyamul Bidh) it picks the most relevant month
     * instance (current month's range, or the upcoming one if this month's already passed).
     */
    /**
     * Computes [IslamicEventInfo] for every [IslamicCalendarSunnah] that is active,
     * upcoming (within 30 days), or recently past (within 90 days) relative to today.
     *
     * For monthly-recurring events (Ayyamul Bidh) it uses only the current month's range.
     * For other events it checks the current Hijri year first, then year+1 (for upcoming
     * events whose Hijri month is before the current month, i.e. next year), and year-1
     * (for recently past events).
     */
    private fun computeIslamicCalendarEvents(): List<IslamicEventInfo> {
        val today     = todayEpochDay()
        val hijriDate = HijriCalendarHelper.currentHijriDate()
        val results   = mutableListOf<IslamicEventInfo>()

        for (sunnah in IslamicCalendarSunnah.entries) {
            // For monthly-recurring events, only look at the current month's range
            val relevantRanges = if (sunnah.isMonthlyRecurring) {
                listOfNotNull(sunnah.rangeForMonth(hijriDate.month))
            } else {
                sunnah.activeRanges
            }

            for (range in relevantRanges) {
                // Try current year, then next year (upcoming), then last year (recently past)
                val yearOffsets = listOf(0, 1, -1)
                for (yearOffset in yearOffsets) {
                    val year       = hijriDate.year + yearOffset
                    val epochRange = HijriCalendarHelper.epochDayRangeFor(range, year)
                    if (epochRange.isEmpty()) continue

                    val status = when {
                        today in epochRange ->
                            IslamicEventStatus.ACTIVE
                        epochRange.first > today && (epochRange.first - today) <= 30L ->
                            IslamicEventStatus.UPCOMING
                        epochRange.last < today && (today - epochRange.last) <= 90L ->
                            IslamicEventStatus.PAST
                        else -> null
                    } ?: continue

                    results.add(
                        IslamicEventInfo(
                            sunnah              = sunnah,
                            goalSetting         = null, // Attached later when goals are loaded
                            status              = status,
                            periodStartEpochDay = epochRange.first,
                            periodEndEpochDay   = epochRange.last,
                            daysUntilStart      = if (epochRange.first > today)
                                                      (epochRange.first - today).toInt() else 0,
                            daysUntilEnd        = if (epochRange.last >= today)
                                                      (epochRange.last - today).toInt() else 0,
                            hijriYear           = year,
                        )
                    )
                    break // Found a valid year for this range — stop checking offsets
                }
            }
        }

        // Sort: ACTIVE first, then UPCOMING by proximity, then PAST by recency
        return results.sortedWith(
            compareBy(
                {
                    when (it.status) {
                        IslamicEventStatus.ACTIVE   -> 0
                        IslamicEventStatus.UPCOMING -> 1
                        IslamicEventStatus.PAST     -> 2
                    }
                },
                { it.daysUntilStart },
                { -it.daysUntilEnd },
            )
        )
    }
}

package com.familyhome.app.presentation.screens.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.sync.DeletionTracker
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
    val weekLogs: List<PrayerLog> = emptyList(),
    val currentUser: User? = null,
    val allUsers: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    // Active goals are enabled settings assigned to current user (or to all)
    fun activeGoalsFor(userId: String): List<PrayerGoalSetting> =
        goalSettings.filter { it.isEnabled && (it.assignedTo == null || it.assignedTo == userId) }

    // Today's log for a specific sunnah and user
    fun todayLogFor(sunnahKey: String, userId: String): PrayerLog? =
        todayLogs.firstOrNull { it.sunnahKey == sunnahKey && it.userId == userId }

    // Number of completed goals for a user today
    fun completedTodayCount(userId: String): Int {
        val active = activeGoalsFor(userId)
        return active.count { goal ->
            val log = todayLogFor(goal.sunnahKey, userId)
            log?.isCompleted == true
        }
    }
}

@HiltViewModel
class PrayerViewModel @Inject constructor(
    private val prayerRepository: PrayerRepository,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFamilyMembersUseCase: GetFamilyMembersUseCase,
    private val deletionTracker: DeletionTracker,
) : ViewModel() {
    private val _state = MutableStateFlow(PrayerUiState())
    val state = _state.asStateFlow()

    init {
        // Load currentUser first, then start collecting goals so isLoading=false
        // is only emitted once we know who the current user is (fixes Today tab for leader).
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
            val todayEpoch = todayEpochDay()
            prayerRepository.getLogsForDay(todayEpoch).collect { logs ->
                _state.update { it.copy(todayLogs = logs) }
            }
        }
        viewModelScope.launch {
            val weekAgo = todayEpochDay() - 6L
            prayerRepository.getLogsSince(weekAgo).collect { logs ->
                _state.update { it.copy(weekLogs = logs) }
            }
        }
    }

    /** Log or increment today's completion for the current user. */
    fun logPrayer(sunnahKey: String) {
        val user = _state.value.currentUser ?: return
        val sunnah = SunnahGoal.entries.firstOrNull { it.name == sunnahKey } ?: return
        val today = todayEpochDay()
        val existing = _state.value.todayLogFor(sunnahKey, user.id)
        viewModelScope.launch {
            val newCount = ((existing?.completedCount ?: 0) + 1).coerceAtMost(sunnah.dailyTarget)
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
        }
    }

    /** Undo / decrement today's completion. */
    fun undoPrayer(sunnahKey: String) {
        val user = _state.value.currentUser ?: return
        val existing = _state.value.todayLogFor(sunnahKey, user.id) ?: return
        viewModelScope.launch {
            val newCount = (existing.completedCount - 1).coerceAtLeast(0)
            prayerRepository.upsertLog(existing.copy(completedCount = newCount, loggedAt = System.currentTimeMillis()))
        }
    }

    /** Enable or disable a sunnah goal (leader only). */
    fun toggleGoal(setting: PrayerGoalSetting) {
        viewModelScope.launch {
            prayerRepository.updateGoalSetting(setting.copy(isEnabled = !setting.isEnabled))
        }
    }

    /** Add a new goal for a sunnah (leader only). assignedTo=null means all family. */
    fun addGoal(sunnahKey: String, assignedTo: String?) {
        val user = _state.value.currentUser ?: return
        // Check if already exists
        if (_state.value.goalSettings.any { it.sunnahKey == sunnahKey }) return
        viewModelScope.launch {
            prayerRepository.insertGoalSetting(
                PrayerGoalSetting(
                    id         = UUID.randomUUID().toString(),
                    sunnahKey  = sunnahKey,
                    isEnabled  = true,
                    assignedTo = assignedTo,
                    createdBy  = user.id,
                    createdAt  = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Remove a goal (leader only). Records the deletion so it propagates to member devices on sync. */
    fun removeGoal(setting: PrayerGoalSetting) {
        viewModelScope.launch {
            prayerRepository.deleteGoalSetting(setting.id)
            deletionTracker.recordPrayerGoalDeletion(setting.id)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun todayEpochDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / (24 * 60 * 60 * 1000L)
    }
}

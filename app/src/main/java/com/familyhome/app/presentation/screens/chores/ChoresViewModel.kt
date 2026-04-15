package com.familyhome.app.presentation.screens.chores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.notification.AlarmScheduler
import com.familyhome.app.data.sync.DeletionTracker
import com.familyhome.app.domain.model.ChoreAssignment
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.Frequency
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.chore.*
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChoresUiState(
    val history: List<ChoreLog>                   = emptyList(),
    val recurringTasks: List<RecurringTask>        = emptyList(),
    val pendingAssignments: List<ChoreAssignment>  = emptyList(),
    val allAssignments: List<ChoreAssignment>      = emptyList(),
    val currentUser: User?                         = null,
    val allUsers: List<User>                       = emptyList(),
    val historyDays: Int                           = 7,
    val isLoading: Boolean                         = true,
    val error: String?                             = null,
)

@HiltViewModel
class ChoresViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFamilyMembersUseCase: GetFamilyMembersUseCase,
    private val logChoreUseCase: LogChoreUseCase,
    private val getChoreHistoryUseCase: GetChoreHistoryUseCase,
    private val getRecurringTasksUseCase: GetRecurringTasksUseCase,
    private val completeRecurringTaskUseCase: CompleteRecurringTaskUseCase,
    private val addRecurringTaskUseCase: AddRecurringTaskUseCase,
    private val updateRecurringTaskUseCase: UpdateRecurringTaskUseCase,
    private val deleteRecurringTaskUseCase: DeleteRecurringTaskUseCase,
    private val deleteChoreLogUseCase: DeleteChoreLogUseCase,
    private val assignChoreUseCase: AssignChoreUseCase,
    private val respondToAssignmentUseCase: RespondToChoreAssignmentUseCase,
    private val getChoreAssignmentsUseCase: GetChoreAssignmentsUseCase,
    private val alarmScheduler: AlarmScheduler,
    private val deletionTracker: DeletionTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(ChoresUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            _state.update { it.copy(currentUser = user) }
            if (user != null) {
                loadHistory(user, 7)
                getChoreAssignmentsUseCase.pendingForUser(user.id).collect { pending ->
                    _state.update { it.copy(pendingAssignments = pending) }
                }
            }
        }

        viewModelScope.launch {
            getFamilyMembersUseCase().collect { members ->
                _state.update { it.copy(allUsers = members) }
            }
        }

        viewModelScope.launch {
            getRecurringTasksUseCase().collect { tasks ->
                _state.update { it.copy(recurringTasks = tasks, isLoading = false) }
            }
        }

        viewModelScope.launch {
            getChoreAssignmentsUseCase.all().collect { all ->
                _state.update { it.copy(allAssignments = all) }
            }
        }
    }

    private fun loadHistory(user: User, days: Int) {
        viewModelScope.launch {
            getChoreHistoryUseCase(user, days).collect { logs ->
                _state.update { it.copy(history = logs, historyDays = days) }
            }
        }
    }

    fun logChore(taskName: String, note: String?) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            logChoreUseCase(user, taskName, user.id, note)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun completeTask(task: RecurringTask) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            completeRecurringTaskUseCase(user, task)
            alarmScheduler.cancel(task.id)
            deletionTracker.recordRecurringTaskDeletion(task.id)
        }
    }

    fun addScheduledTask(
        taskName: String,
        frequency: Frequency,
        assignTo: String?,
        scheduledAt: Long?,
        reminderMinutesBefore: Int?,
    ) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            val result = addRecurringTaskUseCase(
                actor                 = user,
                taskName              = taskName,
                frequency             = frequency,
                assignedTo            = assignTo,
                scheduledAt           = scheduledAt,
                reminderMinutesBefore = reminderMinutesBefore,
            )
            result.fold(
                onSuccess = { task ->
                    if (task.scheduledAt != null && task.reminderMinutesBefore != null) {
                        alarmScheduler.schedule(task)
                    }
                },
                onFailure = { e -> _state.update { it.copy(error = e.message) } },
            )
        }
    }

    fun updateTask(task: RecurringTask) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            updateRecurringTaskUseCase(user, task)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteTask(task: RecurringTask) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            deleteRecurringTaskUseCase(user, task)
                .onSuccess { deletionTracker.recordRecurringTaskDeletion(task.id) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteChoreLog(log: ChoreLog) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            deleteChoreLogUseCase(user, log.id)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun assignTask(task: RecurringTask, userId: String) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            assignChoreUseCase(user, task, userId)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun acceptAssignment(assignment: ChoreAssignment) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            respondToAssignmentUseCase.accept(user, assignment)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun declineAssignment(assignment: ChoreAssignment, reason: String) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            respondToAssignmentUseCase.decline(user, assignment, reason)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun changeHistoryRange(days: Int) {
        val user = _state.value.currentUser ?: return
        loadHistory(user, days)
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

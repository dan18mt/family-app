package com.familyhome.app.presentation.screens.chores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.chore.*
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChoresUiState(
    val history: List<ChoreLog>       = emptyList(),
    val recurringTasks: List<RecurringTask> = emptyList(),
    val currentUser: User?            = null,
    val historyDays: Int              = 7,
    val isLoading: Boolean            = true,
    val error: String?                = null,
)

@HiltViewModel
class ChoresViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logChoreUseCase: LogChoreUseCase,
    private val getChoreHistoryUseCase: GetChoreHistoryUseCase,
    private val getRecurringTasksUseCase: GetRecurringTasksUseCase,
    private val completeRecurringTaskUseCase: CompleteRecurringTaskUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ChoresUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            _state.update { it.copy(currentUser = user) }
            if (user != null) loadHistory(user, 7)
        }
        viewModelScope.launch {
            getRecurringTasksUseCase().collect { tasks ->
                _state.update { it.copy(recurringTasks = tasks, isLoading = false) }
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
            val result = logChoreUseCase(user, taskName, user.id, note)
            result.onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun completeTask(task: RecurringTask) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            completeRecurringTaskUseCase(user, task)
        }
    }

    fun changeHistoryRange(days: Int) {
        val user = _state.value.currentUser ?: return
        loadHistory(user, days)
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

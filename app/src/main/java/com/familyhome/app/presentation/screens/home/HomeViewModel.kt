package com.familyhome.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.expense.CheckBudgetAlertUseCase
import com.familyhome.app.domain.usecase.stock.GetLowStockItemsUseCase
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val currentUser: User? = null,
    val familyMembers: List<User> = emptyList(),
    val lowStockItems: List<StockItem> = emptyList(),
    val budgetAlerts: List<CheckBudgetAlertUseCase.BudgetAlert> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFamilyMembersUseCase: GetFamilyMembersUseCase,
    private val getLowStockItemsUseCase: GetLowStockItemsUseCase,
    private val checkBudgetAlertUseCase: CheckBudgetAlertUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val currentUser = getCurrentUserUseCase()
            _state.update { it.copy(currentUser = currentUser) }

            if (currentUser != null) {
                // Budget alerts (one-shot)
                val alerts = checkBudgetAlertUseCase(currentUser.id)
                _state.update { it.copy(budgetAlerts = alerts) }
            }
        }

        // Reactive streams
        viewModelScope.launch {
            getFamilyMembersUseCase().collect { members ->
                _state.update { it.copy(familyMembers = members, isLoading = false) }
            }
        }

        viewModelScope.launch {
            getLowStockItemsUseCase().collect { items ->
                _state.update { it.copy(lowStockItems = items) }
            }
        }
    }
}

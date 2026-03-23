package com.familyhome.app.presentation.screens.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.expense.CheckBudgetAlertUseCase
import com.familyhome.app.domain.usecase.expense.GetExpensesUseCase
import com.familyhome.app.domain.usecase.expense.LogExpenseUseCase
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpensesUiState(
    val expenses: List<Expense>     = emptyList(),
    val currentUser: User?          = null,
    val allUsers: List<User>        = emptyList(),
    val budgetAlerts: List<CheckBudgetAlertUseCase.BudgetAlert> = emptyList(),
    val totalThisMonth: Long        = 0L,
    val isLoading: Boolean          = true,
    val error: String?              = null,
)

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFamilyMembersUseCase: GetFamilyMembersUseCase,
    private val getExpensesUseCase: GetExpensesUseCase,
    private val logExpenseUseCase: LogExpenseUseCase,
    private val checkBudgetAlertUseCase: CheckBudgetAlertUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpensesUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            _state.update { it.copy(currentUser = user) }

            if (user != null) {
                val alerts = checkBudgetAlertUseCase(user.id)
                _state.update { it.copy(budgetAlerts = alerts) }
            }
        }

        viewModelScope.launch {
            getFamilyMembersUseCase().collect { members ->
                _state.update { it.copy(allUsers = members) }
            }
        }

        viewModelScope.launch {
            // Wait until currentUser is loaded before collecting expenses
            state.filter { it.currentUser != null }.first().let { s ->
                getExpensesUseCase(s.currentUser!!, s.allUsers).collect { expenses ->
                    val visible = expenses.filter { expense ->
                        PermissionFilter.canSee(s.currentUser, expense, s.allUsers)
                    }
                    val total = visible.sumOf { it.amount }
                    _state.update { it.copy(expenses = visible, totalThisMonth = total, isLoading = false) }
                }
            }
        }
    }

    fun logExpense(
        amount: Long,
        category: ExpenseCategory,
        description: String,
        receiptUri: String?,
    ) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            val result = logExpenseUseCase(
                actor       = user,
                amount      = amount,
                category    = category,
                description = description,
                paidByUserId = user.id,
                receiptUri  = receiptUri,
            )
            result.onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

private object PermissionFilter {
    fun canSee(actor: User, expense: Expense, allUsers: List<User>): Boolean {
        return when (actor.role.name) {
            "FATHER" -> true
            "WIFE"   -> {
                val payer = allUsers.find { it.id == expense.paidBy }
                expense.paidBy == actor.id || payer?.role?.name == "KID"
            }
            else -> expense.paidBy == actor.id
        }
    }
}

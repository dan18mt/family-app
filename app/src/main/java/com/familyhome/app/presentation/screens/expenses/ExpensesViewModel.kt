package com.familyhome.app.presentation.screens.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.Budget
import com.familyhome.app.domain.model.BudgetPeriod
import com.familyhome.app.domain.model.CustomExpenseCategory
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.expense.*
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ExpensesUiState(
    val expenses: List<Expense>                                 = emptyList(),
    val currentUser: User?                                      = null,
    val allUsers: List<User>                                    = emptyList(),
    val customCategories: List<CustomExpenseCategory>           = emptyList(),
    val budgetAlerts: List<CheckBudgetAlertUseCase.BudgetAlert> = emptyList(),
    val budgets: List<Budget>                                   = emptyList(),
    val totalThisMonth: Long                                    = 0L,
    val selectedChartPeriod: ChartPeriod                        = ChartPeriod.MONTHLY,
    val selectedMemberId: String?                               = null, // null = current user; FATHER can select
    val isLoading: Boolean                                      = true,
    val error: String?                                          = null,
)

enum class ChartPeriod { MONTHLY, WEEKLY }

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getFamilyMembersUseCase: GetFamilyMembersUseCase,
    private val getExpensesUseCase: GetExpensesUseCase,
    private val logExpenseUseCase: LogExpenseUseCase,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    private val checkBudgetAlertUseCase: CheckBudgetAlertUseCase,
    private val getCustomCategoriesUseCase: GetCustomExpenseCategoriesUseCase,
    private val addCategoryUseCase: AddCustomExpenseCategoryUseCase,
    private val updateCategoryUseCase: UpdateCustomExpenseCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCustomExpenseCategoryUseCase,
    private val getAllBudgetsUseCase: GetAllBudgetsUseCase,
    private val setBudgetUseCase: SetBudgetUseCase,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
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
            getCustomCategoriesUseCase().collect { cats ->
                _state.update { it.copy(customCategories = cats) }
            }
        }

        viewModelScope.launch {
            getAllBudgetsUseCase().collect { budgets ->
                _state.update { it.copy(budgets = budgets) }
            }
        }

        viewModelScope.launch {
            state.filter { it.currentUser != null }.first().let { s ->
                getExpensesUseCase(s.currentUser!!, s.allUsers).collect { expenses ->
                    val visible = expenses.filter { expense ->
                        PermissionFilter.canSee(s.currentUser, expense, s.allUsers)
                    }
                    val total = visible
                        .filter { it.expenseDate >= currentMonthStart() }
                        .sumOf { it.amount }
                    _state.update { it.copy(expenses = visible, totalThisMonth = total, isLoading = false) }
                }
            }
        }
    }

    fun logExpense(
        amount: Long,
        category: ExpenseCategory,
        customCategoryId: String?,
        description: String,
        receiptUri: String?,
    ) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            logExpenseUseCase(
                actor            = user,
                amount           = amount,
                category         = category,
                description      = description,
                paidByUserId     = user.id,
                receiptUri       = receiptUri,
                customCategoryId = customCategoryId,
            ).onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun updateExpense(expense: Expense) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            updateExpenseUseCase(user, expense)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteExpense(expense: Expense) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            deleteExpenseUseCase(user, expense.id)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun addCategory(name: String, iconName: String) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            addCategoryUseCase(user, name, iconName)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun renameCategory(category: CustomExpenseCategory, newName: String) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            updateCategoryUseCase(user, category.copy(name = newName))
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun changeCategoryIcon(category: CustomExpenseCategory, newIcon: String) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            updateCategoryUseCase(user, category.copy(iconName = newIcon))
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteCategory(category: CustomExpenseCategory) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            deleteCategoryUseCase(user, category.id)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun setBudget(
        targetUserId: String?,
        category: ExpenseCategory?,
        limitAmount: Long,
        period: BudgetPeriod,
    ) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            setBudgetUseCase(user, targetUserId, category, limitAmount, period)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteBudget(budget: Budget) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            deleteBudgetUseCase(user, budget.id)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun setChartPeriod(period: ChartPeriod) {
        _state.update { it.copy(selectedChartPeriod = period) }
    }

    fun setSelectedMember(userId: String?) {
        _state.update { it.copy(selectedMemberId = userId) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun currentMonthStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
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

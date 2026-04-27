package com.familyhome.app.presentation.screens.expenses

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    /** Epoch-millis for the start of the active period (payroll-based monthly or week). */
    val currentPeriodStartMillis: Long                          = 0L,
    val selectedChartPeriod: ChartPeriod                        = ChartPeriod.MONTHLY,
    val selectedMemberId: String?                               = null,
    val payrollStartDay: Int                                    = 1,
    /** Inclusive start of custom date range; null = use selectedChartPeriod. */
    val dateRangeFrom: Long?                                    = null,
    /** Inclusive end of custom date range; null = use selectedChartPeriod. */
    val dateRangeTo: Long?                                      = null,
    /**
     * Active category filter key. Matches [Expense.customCategoryId] for custom
     * categories, or [ExpenseCategory.name] for built-in ones. null = all categories.
     */
    val selectedCategoryKey: String?                            = null,
    val isLoading: Boolean                                      = true,
    val error: String?                                          = null,
) {
    /**
     * Total spending that matches the active filters (member, date range or period,
     * but NOT category — the summary card shows all-category totals).
     * Recomputes automatically whenever expenses, filters, or period start change.
     */
    val totalThisMonth: Long
        get() {
            val from = dateRangeFrom
            val to   = dateRangeTo
            return expenses.filter { expense ->
                val memberOk = selectedMemberId == null || expense.paidBy == selectedMemberId
                val dateOk = if (from != null || to != null) {
                    expense.expenseDate >= (from ?: Long.MIN_VALUE) &&
                        expense.expenseDate <= (to ?: Long.MAX_VALUE)
                } else {
                    expense.expenseDate >= currentPeriodStartMillis
                }
                memberOk && dateOk
            }.sumOf { it.amount }
        }

    /**
     * Expenses shown in the list.  Respects member, date range / period, and category
     * filters — all four filter dimensions that the UI exposes.
     */
    val displayedExpenses: List<Expense>
        get() {
            val from = dateRangeFrom
            val to   = dateRangeTo
            return expenses.filter { expense ->
                val memberOk = selectedMemberId == null || expense.paidBy == selectedMemberId
                val dateOk = if (from != null || to != null) {
                    expense.expenseDate >= (from ?: Long.MIN_VALUE) &&
                        expense.expenseDate <= (to ?: Long.MAX_VALUE)
                } else {
                    expense.expenseDate >= currentPeriodStartMillis
                }
                val catOk = selectedCategoryKey == null ||
                    (expense.customCategoryId ?: expense.category.name) == selectedCategoryKey
                memberOk && dateOk && catOk
            }
        }

    val isCustomDateRange: Boolean get() = dateRangeFrom != null || dateRangeTo != null
}

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
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpensesUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val payrollDay = dataStore.data.first()[payrollStartDayKey] ?: 1
            val user = getCurrentUserUseCase()
            val periodStart = computePeriodStart(ChartPeriod.MONTHLY, payrollDay)
            _state.update {
                it.copy(
                    currentUser = user,
                    payrollStartDay = payrollDay,
                    currentPeriodStartMillis = periodStart,
                )
            }
            if (user != null) {
                val alerts = checkBudgetAlertUseCase(user.id, payrollDay)
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
            val initialUser = state.filter { it.currentUser != null }.first().currentUser!!
            getExpensesUseCase(initialUser, emptyList()).collect { expenses ->
                // Always read the latest state so that allUsers (and other filters) are fresh.
                // This prevents a race condition where allUsers is empty at collection start,
                // causing Wife role to incorrectly exclude kids' expenses from the total.
                val current = _state.value
                val user = current.currentUser ?: return@collect
                val visible = expenses.filter { expense ->
                    PermissionFilter.canSee(user, expense, current.allUsers)
                }
                // totalThisMonth is now a computed property on ExpensesUiState — no need to
                // calculate it here; just store the filtered expense list.
                _state.update { it.copy(expenses = visible, isLoading = false) }
            }
        }
    }

    fun logExpense(
        amount: Long,
        category: ExpenseCategory,
        customCategoryId: String?,
        description: String,
        receiptUri: String?,
        expenseDate: Long = System.currentTimeMillis(),
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
                expenseDate      = expenseDate,
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
        // Switching period clears the custom date range and updates the period start so that
        // totalThisMonth and displayedExpenses recompute correctly.
        val periodStart = computePeriodStart(period, _state.value.payrollStartDay)
        _state.update {
            it.copy(
                selectedChartPeriod = period,
                dateRangeFrom = null,
                dateRangeTo = null,
                currentPeriodStartMillis = periodStart,
            )
        }
    }

    fun setSelectedMember(userId: String?) {
        _state.update { it.copy(selectedMemberId = userId) }
        // Refresh budget alerts for the newly visible member so the warning badges reflect the
        // selected person's budgets, not the logged-in user's.
        viewModelScope.launch {
            val user = _state.value.currentUser ?: return@launch
            val targetId = userId ?: user.id
            val alerts = checkBudgetAlertUseCase(targetId, _state.value.payrollStartDay)
            _state.update { it.copy(budgetAlerts = alerts) }
        }
    }

    fun setDateRange(from: Long?, to: Long?) {
        _state.update { it.copy(dateRangeFrom = from, dateRangeTo = to) }
    }

    fun setSelectedCategory(categoryKey: String?) {
        _state.update { it.copy(selectedCategoryKey = categoryKey) }
    }

    /** Persist and apply the new payroll start day (1–31). */
    fun setPayrollStartDay(day: Int) {
        val clamped = day.coerceIn(1, 31)
        // Recompute period start immediately so totalThisMonth and displayedExpenses update in
        // the same frame as the payroll day change, before the async DataStore write completes.
        val periodStart = computePeriodStart(_state.value.selectedChartPeriod, clamped)
        _state.update { it.copy(payrollStartDay = clamped, currentPeriodStartMillis = periodStart) }
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[payrollStartDayKey] = clamped }
            // Refresh budget alerts with the new period
            val user = _state.value.currentUser ?: return@launch
            val alerts = checkBudgetAlertUseCase(user.id, clamped)
            _state.update { it.copy(budgetAlerts = alerts) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /**
     * Computes the epoch-millis for the start of the current period given [period] and
     * [payrollStartDay].  For MONTHLY, the period begins on [payrollStartDay] (clamped to the
     * month's last day) of the current or previous calendar month, whichever is most recent.
     * For WEEKLY, the period begins on the most recent Monday at midnight.
     */
    internal fun computePeriodStart(period: ChartPeriod, payrollStartDay: Int): Long {
        val cal = Calendar.getInstance()
        return when (period) {
            ChartPeriod.WEEKLY -> {
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ChartPeriod.MONTHLY -> {
                val today = cal.get(Calendar.DAY_OF_MONTH)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val effectiveDay = minOf(payrollStartDay, maxDay)
                if (today < effectiveDay) {
                    cal.add(Calendar.MONTH, -1)
                    val prevMaxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, minOf(payrollStartDay, prevMaxDay))
                } else {
                    cal.set(Calendar.DAY_OF_MONTH, effectiveDay)
                }
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
    }

    companion object {
        val payrollStartDayKey = intPreferencesKey("budget_payroll_start_day")
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

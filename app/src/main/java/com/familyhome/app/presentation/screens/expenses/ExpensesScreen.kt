package com.familyhome.app.presentation.screens.expenses

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.*
import com.familyhome.app.presentation.components.LoadingScreen
import com.familyhome.app.presentation.theme.BudgetWarningColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Icon catalogue ────────────────────────────────────────────────────────────
val CATEGORY_ICON_OPTIONS: List<Pair<String, ImageVector>> = listOf(
    "ShoppingCart"    to Icons.Default.ShoppingCart,
    "DirectionsCar"   to Icons.Default.DirectionsCar,
    "School"          to Icons.Default.School,
    "HealthAndSafety" to Icons.Default.HealthAndSafety,
    "Movie"           to Icons.Default.Movie,
    "Home"            to Icons.Default.Home,
    "Restaurant"      to Icons.Default.Restaurant,
    "FlightTakeoff"   to Icons.Default.FlightTakeoff,
    "Pets"            to Icons.Default.Pets,
    "SportsSoccer"    to Icons.Default.SportsSoccer,
    "Checkroom"       to Icons.Default.Checkroom,
    "Savings"         to Icons.Default.Savings,
    "Work"            to Icons.Default.Work,
    "Category"        to Icons.Default.Category,
)

fun iconVectorForName(name: String): ImageVector =
    CATEGORY_ICON_OPTIONS.firstOrNull { it.first == name }?.second ?: Icons.Default.Category

fun expenseCategoryIcon(cat: ExpenseCategory): ImageVector = when (cat) {
    ExpenseCategory.GROCERIES     -> Icons.Default.ShoppingCart
    ExpenseCategory.TRANSPORT     -> Icons.Default.DirectionsCar
    ExpenseCategory.SCHOOL        -> Icons.Default.School
    ExpenseCategory.HEALTH        -> Icons.Default.HealthAndSafety
    ExpenseCategory.ENTERTAINMENT -> Icons.Default.Movie
    ExpenseCategory.HOUSEHOLD     -> Icons.Default.Home
    ExpenseCategory.OTHER         -> Icons.Default.Category
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onAddExpense: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState    = remember { SnackbarHostState() }
    var showAddDialog        by remember { mutableStateOf(false) }
    var showManageCategories by remember { mutableStateOf(false) }
    var showManageBudgets    by remember { mutableStateOf(false) }
    var editExpense          by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(state.error!!)
            viewModel.clearError()
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            customCategories = state.customCategories,
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, category, customCatId, description ->
                viewModel.logExpense(amount, category, customCatId, description, null)
                showAddDialog = false
            },
        )
    }

    editExpense?.let { expense ->
        EditExpenseDialog(
            expense          = expense,
            customCategories = state.customCategories,
            onDismiss        = { editExpense = null },
            onConfirm        = { updated -> viewModel.updateExpense(updated); editExpense = null },
        )
    }

    if (showManageCategories) {
        ManageCategoriesDialog(
            categories   = state.customCategories,
            currentUser  = state.currentUser,
            onDismiss    = { showManageCategories = false },
            onAdd        = { name, icon -> viewModel.addCategory(name, icon) },
            onRename     = { cat, name -> viewModel.renameCategory(cat, name) },
            onChangeIcon = { cat, icon -> viewModel.changeCategoryIcon(cat, icon) },
            onDelete     = { cat -> viewModel.deleteCategory(cat) },
        )
    }

    if (showManageBudgets) {
        ManageBudgetsDialog(
            budgets          = state.budgets,
            allUsers         = state.allUsers,
            currentUser      = state.currentUser,
            payrollStartDay  = state.payrollStartDay,
            onDismiss        = { showManageBudgets = false },
            onAdd            = { targetUserId, category, amount, period ->
                viewModel.setBudget(targetUserId, category, amount, period)
            },
            onDelete         = { budget -> viewModel.deleteBudget(budget) },
            onSetPayrollDay  = { day -> viewModel.setPayrollStartDay(day) },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = {
                    state.budgetAlerts.filter { it.isWarning }.takeIf { it.isNotEmpty() }?.let {
                        IconButton(onClick = {}) {
                            Badge(containerColor = BudgetWarningColor) {
                                Icon(Icons.Default.Warning, "Budget alerts")
                            }
                        }
                    }
                    if (state.currentUser?.role == Role.FATHER || state.currentUser?.role == Role.WIFE) {
                        IconButton(onClick = { showManageBudgets = true }) {
                            Icon(Icons.Default.AccountBalanceWallet, "Manage budgets")
                        }
                        IconButton(onClick = { showManageCategories = true }) {
                            Icon(Icons.Default.Settings, "Manage categories")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add expense")
            }
        }
    ) { padding ->
        if (state.isLoading) { LoadingScreen(); return@Scaffold }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { SummaryCard(totalCents = state.totalThisMonth) }

            item {
                ExpenseChartCard(
                    expenses         = state.expenses,
                    customCategories = state.customCategories,
                    allUsers         = state.allUsers,
                    currentUser      = state.currentUser,
                    selectedPeriod   = state.selectedChartPeriod,
                    selectedMemberId = state.selectedMemberId,
                    payrollStartDay  = state.payrollStartDay,
                    onPeriodChange   = { viewModel.setChartPeriod(it) },
                    onMemberChange   = { viewModel.setSelectedMember(it) },
                )
            }

            state.budgetAlerts.filter { it.isWarning }.forEach { alert ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${alert.budget.category?.displayName ?: "Overall"} budget: " +
                                "${(alert.usageRatio * 100).toInt()}% used",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            if (state.expenses.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No expenses yet.")
                    }
                }
            } else {
                items(state.expenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense          = expense,
                        customCategories = state.customCategories,
                        allUsers         = state.allUsers,
                        currentUser      = state.currentUser,
                        canEdit          = state.currentUser?.role == Role.FATHER ||
                                           (state.currentUser?.role == Role.WIFE && expense.paidBy == state.currentUser?.id),
                        onEdit           = { editExpense = expense },
                        onDelete         = { viewModel.deleteExpense(expense) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ── Expense chart card ────────────────────────────────────────────────────────

private data class ChartEntry(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val amount: Long)

@Composable
private fun ExpenseChartCard(
    expenses: List<Expense>,
    customCategories: List<CustomExpenseCategory>,
    allUsers: List<User>,
    currentUser: User?,
    selectedPeriod: ChartPeriod,
    selectedMemberId: String?,
    payrollStartDay: Int,
    onPeriodChange: (ChartPeriod) -> Unit,
    onMemberChange: (String?) -> Unit,
) {
    val effectiveMemberId = if (currentUser?.role == Role.FATHER) selectedMemberId else currentUser?.id
    val filteredExpenses = expenses.filter { expense ->
        if (effectiveMemberId != null) expense.paidBy == effectiveMemberId else true
    }.filter { expense ->
        val cutoff = if (selectedPeriod == ChartPeriod.WEEKLY) {
            System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        } else {
            val cal = Calendar.getInstance()
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
        expense.expenseDate >= cutoff
    }

    // Group by custom-category ID if set, otherwise by built-in enum name.
    // This ensures custom categories each get their own slice instead of
    // being merged under their parent built-in category (e.g. "Other").
    val byCategory: List<ChartEntry> = filteredExpenses
        .groupBy { expense -> expense.customCategoryId ?: expense.category.name }
        .map { (key, group) ->
            val first = group.first()
            val label: String
            val icon: androidx.compose.ui.graphics.vector.ImageVector
            if (first.customCategoryId != null) {
                val cat = customCategories.find { it.id == first.customCategoryId }
                label = cat?.name ?: first.category.displayName
                icon  = iconVectorForName(cat?.iconName ?: "Category")
            } else {
                label = first.category.displayName
                icon  = expenseCategoryIcon(first.category)
            }
            ChartEntry(label, icon, group.sumOf { it.amount })
        }
        .sortedByDescending { it.amount }
        .take(6)

    val barColors = listOf(
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4),
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Spending by category", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedPeriod == ChartPeriod.MONTHLY,
                        onClick  = { onPeriodChange(ChartPeriod.MONTHLY) },
                        label    = { Text("Month") },
                    )
                    FilterChip(
                        selected = selectedPeriod == ChartPeriod.WEEKLY,
                        onClick  = { onPeriodChange(ChartPeriod.WEEKLY) },
                        label    = { Text("Week") },
                    )
                }
            }

            if (currentUser?.role == Role.FATHER && allUsers.size > 1) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedMemberId == null,
                            onClick  = { onMemberChange(null) },
                            label    = { Text("All") },
                        )
                    }
                    items(allUsers) { user ->
                        FilterChip(
                            selected = selectedMemberId == user.id,
                            onClick  = { onMemberChange(user.id) },
                            label    = { Text(user.name) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (byCategory.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("No expenses in this period", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val total = byCategory.sumOf { it.amount }.toFloat()
                    Canvas(modifier = Modifier.size(160.dp)) {
                        var startAngle = -90f
                        byCategory.forEachIndexed { i, entry ->
                            val sweep = (entry.amount.toFloat() / total) * 360f
                            drawArc(
                                color      = barColors[i % barColors.size],
                                startAngle = startAngle,
                                sweepAngle = sweep - 1f,
                                useCenter  = true,
                            )
                            startAngle += sweep
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val currFmt = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                    byCategory.forEachIndexed { i, entry ->
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(12.dp)) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(barColors[i % barColors.size])
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                entry.icon, null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                entry.label,
                                style    = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                currFmt.format(entry.amount / 100.0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Summary card ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(totalCents: Long) {
    val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(totalCents / 100.0)
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("This month", style = MaterialTheme.typography.labelSmall)
            Text(formatted, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

// ── Expense row ───────────────────────────────────────────────────────────────

@Composable
private fun ExpenseRow(
    expense: Expense,
    customCategories: List<CustomExpenseCategory>,
    allUsers: List<User>,
    currentUser: User?,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val fmt       = SimpleDateFormat("dd MMM", Locale.getDefault())
    val amountFmt = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(expense.amount / 100.0)
    val categoryLabel = if (expense.customCategoryId != null) {
        customCategories.find { it.id == expense.customCategoryId }?.name ?: expense.category.displayName
    } else expense.category.displayName
    val categoryIcon = if (expense.customCategoryId != null) {
        val iconName = customCategories.find { it.id == expense.customCategoryId }?.iconName ?: "Category"
        iconVectorForName(iconName)
    } else {
        expenseCategoryIcon(expense.category)
    }

    val showPayer = currentUser?.role == Role.FATHER ||
        (currentUser?.role == Role.WIFE && expense.paidBy != currentUser.id)
    val payerName = if (showPayer) allUsers.find { it.id == expense.paidBy }?.name else null

    ListItem(
        headlineContent   = { Text(expense.description) },
        supportingContent = {
            Column {
                Text("$categoryLabel · ${fmt.format(Date(expense.expenseDate))}")
                if (payerName != null) {
                    Text("Paid by: $payerName", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(amountFmt, style = MaterialTheme.typography.titleMedium)
                    if (expense.aiExtracted) {
                        Text("via AI", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
                if (canEdit) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        leadingContent = { Icon(categoryIcon, null) },
    )
}

// ── Add expense dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    customCategories: List<CustomExpenseCategory>,
    onDismiss: () -> Unit,
    onConfirm: (Long, ExpenseCategory, String?, String) -> Unit,
) {
    var amountText  by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var builtInCat  by remember { mutableStateOf(ExpenseCategory.OTHER) }
    var customCatId by remember { mutableStateOf<String?>(null) }
    var expanded    by remember { mutableStateOf(false) }

    val selectedLabel = customCatId?.let { id -> customCategories.find { it.id == id }?.name }
        ?: builtInCat.displayName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount (IDR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text        = { Text(cat.displayName) },
                                leadingIcon = { Icon(expenseCategoryIcon(cat), null, Modifier.size(18.dp)) },
                                onClick     = { builtInCat = cat; customCatId = null; expanded = false },
                            )
                        }
                        if (customCategories.isNotEmpty()) {
                            HorizontalDivider()
                            customCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text        = { Text(cat.name) },
                                    leadingIcon = { Icon(iconVectorForName(cat.iconName), null, Modifier.size(18.dp)) },
                                    onClick     = { customCatId = cat.id; expanded = false },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toLongOrNull()?.times(100) ?: return@Button
                onConfirm(amount, builtInCat, customCatId, description)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Edit expense dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseDialog(
    expense: Expense,
    customCategories: List<CustomExpenseCategory>,
    onDismiss: () -> Unit,
    onConfirm: (Expense) -> Unit,
) {
    val amountInIdr = expense.amount / 100
    var amountText  by remember { mutableStateOf(amountInIdr.toString()) }
    var description by remember { mutableStateOf(expense.description) }
    var builtInCat  by remember { mutableStateOf(expense.category) }
    var customCatId by remember { mutableStateOf(expense.customCategoryId) }
    var expanded    by remember { mutableStateOf(false) }

    val selectedLabel = customCatId?.let { id -> customCategories.find { it.id == id }?.name }
        ?: builtInCat.displayName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount (IDR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text        = { Text(cat.displayName) },
                                leadingIcon = { Icon(expenseCategoryIcon(cat), null, Modifier.size(18.dp)) },
                                onClick     = { builtInCat = cat; customCatId = null; expanded = false },
                            )
                        }
                        if (customCategories.isNotEmpty()) {
                            HorizontalDivider()
                            customCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text        = { Text(cat.name) },
                                    leadingIcon = { Icon(iconVectorForName(cat.iconName), null, Modifier.size(18.dp)) },
                                    onClick     = { customCatId = cat.id; expanded = false },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toLongOrNull()?.times(100) ?: return@Button
                onConfirm(expense.copy(amount = amount, description = description,
                    category = builtInCat, customCategoryId = customCatId))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Manage categories dialog ──────────────────────────────────────────────────

@Composable
private fun ManageCategoriesDialog(
    categories: List<CustomExpenseCategory>,
    currentUser: User?,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    onRename: (CustomExpenseCategory, String) -> Unit,
    onChangeIcon: (CustomExpenseCategory, String) -> Unit,
    onDelete: (CustomExpenseCategory) -> Unit,
) {
    var showAddForm by remember { mutableStateOf(false) }
    var editingCat  by remember { mutableStateOf<CustomExpenseCategory?>(null) }

    if (showAddForm) {
        AddCategoryForm(
            onDismiss = { showAddForm = false },
            onConfirm = { name, icon -> onAdd(name, icon); showAddForm = false },
        )
        return
    }

    editingCat?.let { cat ->
        EditCategoryForm(
            category  = cat,
            onDismiss = { editingCat = null },
            onConfirm = { name, icon ->
                if (name != cat.name) onRename(cat, name)
                if (icon != cat.iconName) onChangeIcon(cat, icon)
                editingCat = null
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Expense categories") },
        text = {
            Column {
                if (categories.isEmpty()) {
                    Text("No custom categories yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    categories.forEach { cat ->
                        ListItem(
                            headlineContent = { Text(cat.name) },
                            leadingContent  = { Icon(iconVectorForName(cat.iconName), null) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editingCat = cat }) {
                                        Icon(Icons.Default.Edit, "Edit")
                                    }
                                    IconButton(onClick = { onDelete(cat) }) {
                                        Icon(Icons.Default.Delete, "Delete",
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { showAddForm = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New category")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AddCategoryForm(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name         by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("Category") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Category name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Choose icon:", style = MaterialTheme.typography.labelMedium)
                IconPicker(selected = selectedIcon, onSelect = { selectedIcon = it })
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditCategoryForm(
    category: CustomExpenseCategory,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name         by remember { mutableStateOf(category.name) }
    var selectedIcon by remember { mutableStateOf(category.iconName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Category name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Choose icon:", style = MaterialTheme.typography.labelMedium)
                IconPicker(selected = selectedIcon, onSelect = { selectedIcon = it })
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CATEGORY_ICON_OPTIONS) { (name, vector) ->
            FilterChip(
                selected = selected == name,
                onClick  = { onSelect(name) },
                label    = { Icon(vector, name, Modifier.size(20.dp)) },
            )
        }
    }
}

// ── Manage budgets dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageBudgetsDialog(
    budgets: List<Budget>,
    allUsers: List<User>,
    currentUser: User?,
    payrollStartDay: Int,
    onDismiss: () -> Unit,
    onAdd: (String?, ExpenseCategory?, Long, BudgetPeriod) -> Unit,
    onDelete: (Budget) -> Unit,
    onSetPayrollDay: (Int) -> Unit,
) {
    var showAddForm by remember { mutableStateOf(false) }
    val currFmt = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    if (showAddForm) {
        AddBudgetForm(
            allUsers    = allUsers,
            currentUser = currentUser,
            onDismiss   = { showAddForm = false },
            onConfirm   = { targetUserId, category, amount, period ->
                onAdd(targetUserId, category, amount, period)
                showAddForm = false
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budgets") },
        text = {
            Column {
                // Payroll period start setting
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Payroll start day", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Day $payrollStartDay of each month",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value         = payrollStartDay.toString(),
                            onValueChange = {},
                            readOnly      = true,
                            modifier      = Modifier.width(88.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            textStyle     = MaterialTheme.typography.bodyMedium,
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            (1..31).forEach { day ->
                                DropdownMenuItem(
                                    text    = { Text("Day $day") },
                                    onClick = { onSetPayrollDay(day); expanded = false },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (budgets.isEmpty()) {
                    Text("No budgets set.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    budgets.forEach { budget ->
                        val targetName = when {
                            budget.targetUserId == null -> "All family"
                            else -> allUsers.find { it.id == budget.targetUserId }?.name ?: "Unknown"
                        }
                        ListItem(
                            headlineContent   = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    budget.category?.let { cat ->
                                        Icon(expenseCategoryIcon(cat), null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text("${budget.category?.displayName ?: "Overall"} · $targetName")
                                }
                            },
                            supportingContent = {
                                Text("${currFmt.format(budget.limitAmount / 100.0)} / ${budget.period.displayName}")
                            },
                            trailingContent = {
                                IconButton(onClick = { onDelete(budget) }) {
                                    Icon(Icons.Default.Delete, "Delete",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { showAddForm = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add budget")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetForm(
    allUsers: List<User>,
    currentUser: User?,
    onDismiss: () -> Unit,
    onConfirm: (String?, ExpenseCategory?, Long, BudgetPeriod) -> Unit,
) {
    var amountText      by remember { mutableStateOf("") }
    var category        by remember { mutableStateOf<ExpenseCategory?>(null) }
    var targetUserId    by remember { mutableStateOf<String?>(null) }
    var period          by remember { mutableStateOf(BudgetPeriod.MONTHLY) }
    var catExpanded     by remember { mutableStateOf(false) }
    var memberExpanded  by remember { mutableStateOf(false) }
    var periodExpanded  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("Limit amount (IDR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                    OutlinedTextField(
                        value = category?.displayName ?: "All categories", onValueChange = {}, readOnly = true,
                        label = { Text("Category (optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        DropdownMenuItem(text = { Text("All categories") },
                            onClick = { category = null; catExpanded = false })
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text        = { Text(cat.displayName) },
                                leadingIcon = { Icon(expenseCategoryIcon(cat), null, Modifier.size(18.dp)) },
                                onClick     = { category = cat; catExpanded = false }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = periodExpanded, onExpandedChange = { periodExpanded = it }) {
                    OutlinedTextField(
                        value = period.displayName, onValueChange = {}, readOnly = true,
                        label = { Text("Period") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(periodExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                        BudgetPeriod.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(p.displayName) },
                                onClick = { period = p; periodExpanded = false })
                        }
                    }
                }
                if (currentUser?.role == Role.FATHER && allUsers.size > 1) {
                    ExposedDropdownMenuBox(expanded = memberExpanded, onExpandedChange = { memberExpanded = it }) {
                        OutlinedTextField(
                            value = allUsers.find { it.id == targetUserId }?.name ?: "All family",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Apply to") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(memberExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = memberExpanded, onDismissRequest = { memberExpanded = false }) {
                            DropdownMenuItem(text = { Text("All family") },
                                onClick = { targetUserId = null; memberExpanded = false })
                            allUsers.forEach { user ->
                                DropdownMenuItem(text = { Text(user.name) },
                                    onClick = { targetUserId = user.id; memberExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toLongOrNull()?.times(100) ?: return@Button
                onConfirm(targetUserId, category, amount, period)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

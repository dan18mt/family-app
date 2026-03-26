package com.familyhome.app.presentation.screens.expenses

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.CustomExpenseCategory
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.presentation.components.LoadingScreen
import com.familyhome.app.presentation.theme.BudgetWarningColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Icon catalogue shown in the category picker ──────────────────────────────
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

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onAddExpense: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog       by remember { mutableStateOf(false) }
    var showManageCategories by remember { mutableStateOf(false) }

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

    if (showManageCategories) {
        ManageCategoriesDialog(
            categories    = state.customCategories,
            currentUser   = state.currentUser,
            onDismiss     = { showManageCategories = false },
            onAdd         = { name, icon -> viewModel.addCategory(name, icon) },
            onRename      = { cat, name -> viewModel.renameCategory(cat, name) },
            onChangeIcon  = { cat, icon -> viewModel.changeCategoryIcon(cat, icon) },
            onDelete      = { cat -> viewModel.deleteCategory(cat) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = {
                    // Budget warning badge
                    state.budgetAlerts.filter { it.isWarning }.takeIf { it.isNotEmpty() }?.let {
                        IconButton(onClick = {}) {
                            Badge(containerColor = BudgetWarningColor) {
                                Icon(Icons.Default.Warning, "Budget alerts")
                            }
                        }
                    }
                    // Manage categories (leader/wife only)
                    if (state.currentUser?.role == Role.FATHER || state.currentUser?.role == Role.WIFE) {
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
                    )
                    HorizontalDivider()
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
) {
    val fmt        = SimpleDateFormat("dd MMM", Locale.getDefault())
    val amountFmt  = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(expense.amount / 100.0)
    val categoryLabel = if (expense.customCategoryId != null) {
        customCategories.find { it.id == expense.customCategoryId }?.name ?: expense.category.displayName
    } else {
        expense.category.displayName
    }
    val categoryIcon = if (expense.customCategoryId != null) {
        val iconName = customCategories.find { it.id == expense.customCategoryId }?.iconName ?: "Category"
        iconVectorForName(iconName)
    } else {
        Icons.Default.Receipt
    }

    // Show payer name for FATHER (and WIFE seeing kids' expenses)
    val showPayer = currentUser?.role == Role.FATHER ||
        (currentUser?.role == Role.WIFE && expense.paidBy != currentUser.id)
    val payerName = if (showPayer) allUsers.find { it.id == expense.paidBy }?.name else null

    ListItem(
        headlineContent   = { Text(expense.description) },
        supportingContent = {
            Column {
                Text("$categoryLabel · ${fmt.format(Date(expense.expenseDate))}")
                if (payerName != null) {
                    Text(
                        "Paid by: $payerName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
        trailingContent   = {
            Column(horizontalAlignment = Alignment.End) {
                Text(amountFmt, style = MaterialTheme.typography.titleMedium)
                if (expense.aiExtracted) {
                    Text("via AI", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
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
    var amountText     by remember { mutableStateOf("") }
    var description    by remember { mutableStateOf("") }
    var builtInCat     by remember { mutableStateOf(ExpenseCategory.OTHER) }
    var customCatId    by remember { mutableStateOf<String?>(null) }
    var expanded       by remember { mutableStateOf(false) }

    // Unified label for the selected category
    val selectedLabel = customCatId?.let { id ->
        customCategories.find { it.id == id }?.name
    } ?: builtInCat.displayName

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Add expense") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value           = amountText,
                    onValueChange   = { amountText = it },
                    label           = { Text("Amount (IDR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    modifier        = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Description") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                // Category dropdown (built-in + custom)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value         = selectedLabel,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Category") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        // Built-in categories
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text    = { Text(cat.displayName) },
                                onClick = { builtInCat = cat; customCatId = null; expanded = false },
                            )
                        }
                        // Custom categories
                        if (customCategories.isNotEmpty()) {
                            HorizontalDivider()
                            customCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text         = { Text(cat.name) },
                                    leadingIcon  = { Icon(iconVectorForName(cat.iconName), null, Modifier.size(18.dp)) },
                                    onClick      = { customCatId = cat.id; expanded = false },
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
    var showAddForm   by remember { mutableStateOf(false) }
    var editingCat    by remember { mutableStateOf<CustomExpenseCategory?>(null) }

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
        title            = { Text("Expense categories") },
        text             = {
            Column {
                if (categories.isEmpty()) {
                    Text("No custom categories yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    categories.forEach { cat ->
                        ListItem(
                            headlineContent = { Text(cat.name) },
                            leadingContent  = {
                                Icon(iconVectorForName(cat.iconName), null)
                            },
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
private fun AddCategoryForm(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name         by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("Category") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("New category") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Category name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
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
        title            = { Text("Edit category") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Category name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
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

/** Horizontally scrollable grid of icon chips. */
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

package com.familyhome.app.presentation.screens.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.presentation.components.LoadingScreen
import com.familyhome.app.presentation.theme.BudgetWarningColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onAddExpense: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, category, description ->
                viewModel.logExpense(amount, category, description, null)
                showAddDialog = false
            }
        )
    }

    Scaffold(
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
            // Summary card
            item {
                SummaryCard(totalCents = state.totalThisMonth)
            }

            // Budget warning
            state.budgetAlerts.filter { it.isWarning }.forEach { alert ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(
                            modifier  = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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

            // Expense list
            if (state.expenses.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No expenses yet.")
                    }
                }
            } else {
                items(state.expenses, key = { it.id }) { expense ->
                    ExpenseRow(expense)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(totalCents: Long) {
    val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        .format(totalCents / 100.0)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("This month", style = MaterialTheme.typography.labelSmall)
            Text(formatted, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense) {
    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    val amountFmt = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        .format(expense.amount / 100.0)

    ListItem(
        headlineContent   = { Text(expense.description) },
        supportingContent = { Text("${expense.category.displayName} · ${fmt.format(Date(expense.expenseDate))}") },
        trailingContent   = {
            Column(horizontalAlignment = Alignment.End) {
                Text(amountFmt, style = MaterialTheme.typography.titleMedium)
                if (expense.aiExtracted) {
                    Text("via AI", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        leadingContent = { Icon(Icons.Default.Receipt, null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, ExpenseCategory, String) -> Unit,
) {
    var amountText  by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category    by remember { mutableStateOf(ExpenseCategory.OTHER) }
    var expanded    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Add expense") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = amountText,
                    onValueChange = { amountText = it },
                    label         = { Text("Amount (IDR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Description") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded         = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value         = category.displayName,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Category") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExpenseCategory.values().forEach { cat ->
                            DropdownMenuItem(
                                text    = { Text(cat.displayName) },
                                onClick = { category = cat; expanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toLongOrNull()?.times(100) ?: return@Button
                onConfirm(amount, category, description)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

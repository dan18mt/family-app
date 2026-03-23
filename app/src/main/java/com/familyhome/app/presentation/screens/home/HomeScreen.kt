package com.familyhome.app.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.presentation.components.LowStockBadge
import com.familyhome.app.presentation.components.SectionHeader
import com.familyhome.app.presentation.navigation.Screen
import com.familyhome.app.presentation.theme.BudgetWarningColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateTo: (Screen) -> Unit,
    onNavigateToSync: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FamilyHome", style = MaterialTheme.typography.titleLarge)
                        state.currentUser?.let {
                            Text(
                                "Hello, ${it.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSync) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync settings")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── Quick-access tiles ─────────────────────────────────────────
            item {
                SectionHeader("Quick access")
                Row(
                    modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickTile("Pantry",   Icons.Default.Kitchen,    Modifier.weight(1f)) { onNavigateTo(Screen.Stock) }
                    QuickTile("Chores",   Icons.Default.CleaningServices, Modifier.weight(1f)) { onNavigateTo(Screen.Chores) }
                    QuickTile("Expenses", Icons.Default.AccountBalanceWallet, Modifier.weight(1f)) { onNavigateTo(Screen.Expenses) }
                    QuickTile("Chat",     Icons.AutoMirrored.Filled.Chat, Modifier.weight(1f)) { onNavigateTo(Screen.Chat) }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Low stock alerts ───────────────────────────────────────────
            if (state.lowStockItems.isNotEmpty()) {
                item { SectionHeader("Low stock (${state.lowStockItems.size})") }
                items(state.lowStockItems.take(3)) { item ->
                    ListItem(
                        headlineContent    = { Text(item.name) },
                        supportingContent  = { Text("${item.quantity} ${item.unit} remaining") },
                        trailingContent    = { LowStockBadge() },
                        leadingContent     = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
                    )
                }
                if (state.lowStockItems.size > 3) {
                    item {
                        TextButton(
                            onClick  = { onNavigateTo(Screen.Stock) },
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("See all ${state.lowStockItems.size} low-stock items")
                        }
                    }
                }
            }

            // ── Budget alerts ──────────────────────────────────────────────
            val warnings = state.budgetAlerts.filter { it.isWarning }
            if (warnings.isNotEmpty()) {
                item { SectionHeader("Budget alerts") }
                items(warnings) { alert ->
                    ListItem(
                        headlineContent    = {
                            Text(alert.budget.category?.displayName ?: "Overall budget")
                        },
                        supportingContent  = {
                            val pct = (alert.usageRatio * 100).toInt()
                            Text("$pct% used")
                        },
                        leadingContent = {
                            Icon(Icons.Default.MonetizationOn, null, tint = BudgetWarningColor)
                        },
                    )
                }
            }

            // ── Family members ─────────────────────────────────────────────
            if (state.familyMembers.isNotEmpty()) {
                item { SectionHeader("Family (${state.familyMembers.size})") }
                items(state.familyMembers) { member ->
                    ListItem(
                        headlineContent   = { Text(member.name) },
                        supportingContent = { Text(member.role.displayName) },
                        leadingContent    = {
                            Icon(Icons.Default.Person, null)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick  = onClick,
        modifier = modifier.height(80.dp),
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

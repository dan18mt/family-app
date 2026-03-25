package com.familyhome.app.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.presentation.components.AvatarInitials
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
                        Text(
                            "FamilyHome",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        state.currentUser?.let {
                            Text(
                                "Hello, ${it.name}!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    state.currentUser?.let { user ->
                        AvatarInitials(
                            name     = user.name,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = onNavigateToSync) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding      = PaddingValues(bottom = 24.dp),
        ) {
            // ── Quick-access feature grid ──────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Quick access")
                Spacer(Modifier.height(4.dp))

                Column(
                    modifier            = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureTile(
                            label       = "Pantry",
                            icon        = Icons.Default.Kitchen,
                            gradient    = listOf(Color(0xFF2D6A4F), Color(0xFF52796F)),
                            modifier    = Modifier.weight(1f),
                            onClick     = { onNavigateTo(Screen.Stock) },
                        )
                        FeatureTile(
                            label       = "Chores",
                            icon        = Icons.Default.CheckCircle,
                            gradient    = listOf(Color(0xFF52796F), Color(0xFF74A57F)),
                            modifier    = Modifier.weight(1f),
                            onClick     = { onNavigateTo(Screen.Chores) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureTile(
                            label       = "Budget",
                            icon        = Icons.Default.AccountBalanceWallet,
                            gradient    = listOf(Color(0xFFC77B41), Color(0xFFE8A87C)),
                            modifier    = Modifier.weight(1f),
                            onClick     = { onNavigateTo(Screen.Expenses) },
                        )
                        FeatureTile(
                            label       = "AI Chat",
                            icon        = Icons.AutoMirrored.Filled.Chat,
                            gradient    = listOf(Color(0xFF4A4458), Color(0xFF7B6FA0)),
                            modifier    = Modifier.weight(1f),
                            onClick     = { onNavigateTo(Screen.Chat) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Low-stock alerts ───────────────────────────────────────────
            if (state.lowStockItems.isNotEmpty()) {
                item { SectionHeader("Low stock (${state.lowStockItems.size})") }
                items(state.lowStockItems.take(3)) { item ->
                    ListItem(
                        headlineContent   = { Text(item.name, style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text("${item.quantity} ${item.unit} left") },
                        trailingContent   = { LowStockBadge() },
                        leadingContent    = {
                            Box(
                                modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint     = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
                if (state.lowStockItems.size > 3) {
                    item {
                        TextButton(
                            onClick  = { onNavigateTo(Screen.Stock) },
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("See all ${state.lowStockItems.size} items")
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
                        headlineContent   = {
                            Text(
                                alert.budget.category?.displayName ?: "Overall budget",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text("${(alert.usageRatio * 100).toInt()}% used")
                        },
                        leadingContent = {
                            Box(
                                modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.MonetizationOn,
                                    null,
                                    tint     = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            }

            // ── Family members ─────────────────────────────────────────────
            if (state.familyMembers.isNotEmpty()) {
                item { SectionHeader("Family (${state.familyMembers.size})") }
                items(state.familyMembers) { member ->
                    ListItem(
                        headlineContent   = { Text(member.name, style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text(member.role.displayName) },
                        leadingContent    = {
                            AvatarInitials(
                                name     = member.name,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(
    label: String,
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.height(100.dp),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier            = Modifier.padding(8.dp),
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = label,
                    tint               = Color.White,
                    modifier           = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

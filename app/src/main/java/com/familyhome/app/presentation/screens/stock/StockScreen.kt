package com.familyhome.app.presentation.screens.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.presentation.components.LowStockBadge
import com.familyhome.app.presentation.components.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    onAddItem: () -> Unit,
    viewModel: StockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    state.error?.let { msg ->
        LaunchedEffect(msg) {
            // Snackbar would be shown here; cleared after display
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pantry & Stock") })
        },
        floatingActionButton = {
            if (state.currentUser?.role != Role.KID) {
                FloatingActionButton(onClick = onAddItem) {
                    Icon(Icons.Default.Add, "Add item")
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingScreen()
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Category filter chips
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick  = { viewModel.filterByCategory(null) },
                        label    = { Text("All") },
                    )
                }
                items(StockCategory.values()) { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick  = { viewModel.filterByCategory(cat) },
                        label    = { Text(cat.displayName) },
                    )
                }
            }

            if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No items yet. Add some to get started!")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        StockItemRow(
                            item     = item,
                            canEdit  = state.currentUser?.role != Role.KID,
                            onPlus   = { viewModel.adjustQuantity(item, +1f) },
                            onMinus  = { viewModel.adjustQuantity(item, -1f) },
                            onDelete = { viewModel.deleteItem(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StockItemRow(
    item: StockItem,
    canEdit: Boolean,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name)
                if (item.isLowStock) {
                    Spacer(Modifier.width(8.dp))
                    LowStockBadge()
                }
            }
        },
        supportingContent = {
            Text("${item.category.displayName} · ${item.quantity} ${item.unit}")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMinus, enabled = item.quantity > 0) {
                    Icon(Icons.Default.Remove, "Decrease")
                }
                Text(
                    text  = "%.1f".format(item.quantity),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(min = 36.dp),
                )
                IconButton(onClick = onPlus) {
                    Icon(Icons.Default.Add, "Increase")
                }
                if (canEdit) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    )
}

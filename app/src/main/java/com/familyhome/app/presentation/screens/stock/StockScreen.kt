package com.familyhome.app.presentation.screens.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.CustomStockCategory
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.presentation.components.LowStockBadge
import com.familyhome.app.presentation.components.LoadingScreen

private val STOCK_CATEGORY_ICON_OPTIONS: List<Pair<String, ImageVector>> = listOf(
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

private fun stockIconVectorForName(name: String): ImageVector =
    STOCK_CATEGORY_ICON_OPTIONS.firstOrNull { it.first == name }?.second ?: Icons.Default.Category

private fun builtInCategoryIcon(cat: StockCategory): ImageVector = when (cat) {
    StockCategory.FOOD     -> Icons.Default.Kitchen
    StockCategory.CLEANING -> Icons.Default.CleaningServices
    StockCategory.TOILETRY -> Icons.Default.Soap
    StockCategory.OTHER    -> Icons.Default.Category
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    onAddItem: () -> Unit,
    viewModel: StockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editItem              by remember { mutableStateOf<StockItem?>(null) }
    var selectedCustomCatId   by remember { mutableStateOf<String?>(null) }
    var showManageCategories  by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(state.error!!)
            viewModel.clearError()
        }
    }

    editItem?.let { item ->
        EditStockItemDialog(
            item             = item,
            customCategories = state.customCategories,
            onDismiss        = { editItem = null },
            onConfirm        = { updated -> viewModel.updateItem(updated); editItem = null },
        )
    }

    if (showManageCategories) {
        ManageStockCategoriesDialog(
            categories = state.customCategories,
            onDismiss  = { showManageCategories = false },
            onAdd      = { name, icon -> viewModel.addCategory(name, icon) },
            onUpdate   = { cat -> viewModel.updateCategory(cat) },
            onDelete   = { cat -> viewModel.deleteCategory(cat) },
        )
    }

    val displayedItems = if (selectedCustomCatId != null) {
        state.items.filter { it.customCategoryId == selectedCustomCatId }
    } else {
        state.items
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pantry & Stock") },
                actions = {
                    if (state.currentUser?.role == Role.FATHER || state.currentUser?.role == Role.WIFE) {
                        IconButton(onClick = { showManageCategories = true }) {
                            Icon(Icons.Default.Settings, "Manage categories")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.currentUser?.role != Role.KID) {
                FloatingActionButton(onClick = onAddItem) {
                    Icon(Icons.Default.Add, "Add item")
                }
            }
        }
    ) { padding ->
        if (state.isLoading) { LoadingScreen(); return@Scaffold }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null && selectedCustomCatId == null,
                        onClick  = { viewModel.filterByCategory(null); selectedCustomCatId = null },
                        label    = { Text("All") },
                    )
                }
                items(StockCategory.entries) { cat ->
                    FilterChip(
                        selected     = state.selectedCategory == cat && selectedCustomCatId == null,
                        onClick      = { viewModel.filterByCategory(cat); selectedCustomCatId = null },
                        label        = { Text(cat.displayName) },
                        leadingIcon  = {
                            Icon(builtInCategoryIcon(cat), null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
                items(state.customCategories) { cat ->
                    FilterChip(
                        selected    = selectedCustomCatId == cat.id,
                        onClick     = {
                            viewModel.filterByCategory(null)
                            selectedCustomCatId = if (selectedCustomCatId == cat.id) null else cat.id
                        },
                        label       = { Text(cat.name) },
                        leadingIcon = {
                            Icon(stockIconVectorForName(cat.iconName), null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }

            if (displayedItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No items yet. Add some to get started!")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(displayedItems, key = { it.id }) { item ->
                        StockItemRow(
                            item             = item,
                            customCategories = state.customCategories,
                            canEdit          = state.currentUser?.role != Role.KID,
                            onPlus           = { viewModel.adjustQuantity(item, +1f) },
                            onMinus          = { viewModel.adjustQuantity(item, -1f) },
                            onEdit           = { editItem = item },
                            onDelete         = { viewModel.deleteItem(item) },
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
    customCategories: List<CustomStockCategory>,
    canEdit: Boolean,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val categoryLabel = if (item.customCategoryId != null) {
        customCategories.find { it.id == item.customCategoryId }?.name ?: item.category.displayName
    } else {
        item.category.displayName
    }
    val leadingIcon = if (item.customCategoryId != null) {
        val iconName = customCategories.find { it.id == item.customCategoryId }?.iconName ?: "Category"
        stockIconVectorForName(iconName)
    } else {
        builtInCategoryIcon(item.category)
    }

    ListItem(
        headlineContent = {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = item.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isLowStock) {
                    Spacer(Modifier.width(8.dp))
                    LowStockBadge()
                }
            }
        },
        supportingContent = {
            Text("$categoryLabel · ${item.quantity} ${item.unit}")
        },
        leadingContent = {
            Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMinus, enabled = item.quantity > 0) {
                    Icon(Icons.Default.Remove, "Decrease")
                }
                Text(
                    text     = "%.1f".format(item.quantity),
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(min = 36.dp),
                )
                IconButton(onClick = onPlus) {
                    Icon(Icons.Default.Add, "Increase")
                }
                if (canEdit) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStockItemDialog(
    item: StockItem,
    customCategories: List<CustomStockCategory>,
    onDismiss: () -> Unit,
    onConfirm: (StockItem) -> Unit,
) {
    var name           by remember { mutableStateOf(item.name) }
    var category       by remember { mutableStateOf(item.category) }
    var customCatId    by remember { mutableStateOf(item.customCategoryId) }
    var quantityStr    by remember { mutableStateOf("%.1f".format(item.quantity)) }
    var unit           by remember { mutableStateOf(item.unit) }
    var minQtyStr      by remember { mutableStateOf("%.1f".format(item.minQuantity)) }
    var catExpanded    by remember { mutableStateOf(false) }

    val selectedCatLabel = customCatId?.let { id ->
        customCategories.find { it.id == id }?.name
    } ?: category.displayName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Item name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                    OutlinedTextField(
                        value = selectedCatLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        StockCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                onClick = { category = cat; customCatId = null; catExpanded = false },
                            )
                        }
                        if (customCategories.isNotEmpty()) {
                            HorizontalDivider()
                            customCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text        = { Text(cat.name) },
                                    leadingIcon = { Icon(stockIconVectorForName(cat.iconName), null, Modifier.size(18.dp)) },
                                    onClick     = { customCatId = cat.id; catExpanded = false },
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityStr, onValueChange = { quantityStr = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it },
                        label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = minQtyStr, onValueChange = { minQtyStr = it },
                    label = { Text("Low-stock threshold") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || unit.isBlank()) return@Button
                onConfirm(item.copy(
                    name             = name.trim(),
                    category         = category,
                    customCategoryId = customCatId,
                    quantity         = quantityStr.toFloatOrNull() ?: item.quantity,
                    unit             = unit.trim(),
                    minQuantity      = minQtyStr.toFloatOrNull() ?: item.minQuantity,
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ManageStockCategoriesDialog(
    categories: List<CustomStockCategory>,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    onUpdate: (CustomStockCategory) -> Unit,
    onDelete: (CustomStockCategory) -> Unit,
) {
    var showAddForm  by remember { mutableStateOf(false) }
    var editingCat   by remember { mutableStateOf<CustomStockCategory?>(null) }

    if (showAddForm) {
        AddStockCategoryForm(
            onDismiss = { showAddForm = false },
            onConfirm = { name, icon -> onAdd(name, icon); showAddForm = false },
        )
        return
    }

    editingCat?.let { cat ->
        EditStockCategoryForm(
            category  = cat,
            onDismiss = { editingCat = null },
            onConfirm = { name, icon -> onUpdate(cat.copy(name = name, iconName = icon)); editingCat = null },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stock categories") },
        text = {
            Column {
                if (categories.isEmpty()) {
                    Text("No custom categories yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    categories.forEach { cat ->
                        ListItem(
                            headlineContent = { Text(cat.name) },
                            leadingContent  = { Icon(stockIconVectorForName(cat.iconName), null) },
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
private fun AddStockCategoryForm(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
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
                StockIconPicker(selected = selectedIcon, onSelect = { selectedIcon = it })
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditStockCategoryForm(
    category: CustomStockCategory,
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
                StockIconPicker(selected = selectedIcon, onSelect = { selectedIcon = it })
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StockIconPicker(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(STOCK_CATEGORY_ICON_OPTIONS) { (name, vector) ->
            FilterChip(
                selected = selected == name,
                onClick  = { onSelect(name) },
                label    = { Icon(vector, name, Modifier.size(20.dp)) },
            )
        }
    }
}

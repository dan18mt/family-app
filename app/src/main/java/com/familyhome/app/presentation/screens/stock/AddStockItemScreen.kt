package com.familyhome.app.presentation.screens.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.usecase.stock.AddStockItemUseCase
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class AddStockUiState(
    val isSaving: Boolean = false,
    val saved: Boolean    = false,
    val error: String?    = null,
)

@HiltViewModel
class AddStockItemViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val addStockItemUseCase: AddStockItemUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AddStockUiState())
    val state = _state.asStateFlow()

    fun save(
        name: String,
        category: StockCategory,
        quantity: Float,
        unit: String,
        minQuantity: Float,
    ) {
        if (name.isBlank()) { _state.update { it.copy(error = "Name cannot be empty") }; return }
        if (unit.isBlank())  { _state.update { it.copy(error = "Unit cannot be empty") }; return }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val actor = getCurrentUserUseCase()
            if (actor == null) {
                _state.update { it.copy(isSaving = false, error = "Not logged in") }
                return@launch
            }
            val result = addStockItemUseCase(actor, name.trim(), category, quantity, unit.trim(), minQuantity)
            result.fold(
                onSuccess = { _state.update { it.copy(isSaving = false, saved = true) } },
                onFailure = { e -> _state.update { it.copy(isSaving = false, error = e.message) } },
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStockItemScreen(
    onBack: () -> Unit,
    viewModel: AddStockItemViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate back once saved
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(state.error!!)
            viewModel.clearError()
        }
    }

    var name        by remember { mutableStateOf("") }
    var category    by remember { mutableStateOf(StockCategory.FOOD) }
    var quantityStr by remember { mutableStateOf("1") }
    var unit        by remember { mutableStateOf("pcs") }
    var minQtyStr   by remember { mutableStateOf("1") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Add Pantry Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = {
                            viewModel.save(
                                name        = name,
                                category    = category,
                                quantity    = quantityStr.toFloatOrNull() ?: 1f,
                                unit        = unit,
                                minQuantity = minQtyStr.toFloatOrNull() ?: 1f,
                            )
                        }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Item name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            // Category chips
            Text("Category", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StockCategory.entries) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick  = { category = cat },
                        label    = { Text(cat.displayName) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value           = quantityStr,
                    onValueChange   = { quantityStr = it },
                    label           = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine      = true,
                    modifier        = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value           = unit,
                    onValueChange   = { unit = it },
                    label           = { Text("Unit") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    singleLine      = true,
                    modifier        = Modifier.weight(1f),
                    placeholder     = { Text("pcs / kg / L") },
                )
            }

            OutlinedTextField(
                value           = minQtyStr,
                onValueChange   = { minQtyStr = it },
                label           = { Text("Low-stock threshold") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText  = { Text("A warning badge shows when quantity falls below this") },
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick   = {
                    viewModel.save(
                        name        = name,
                        category    = category,
                        quantity    = quantityStr.toFloatOrNull() ?: 1f,
                        unit        = unit,
                        minQuantity = minQtyStr.toFloatOrNull() ?: 1f,
                    )
                },
                modifier  = Modifier.fillMaxWidth(),
                enabled   = !state.isSaving,
            ) {
                Text("Add to Pantry")
            }
        }
    }
}

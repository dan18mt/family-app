package com.familyhome.app.presentation.screens.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.stock.*
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockUiState(
    val items: List<StockItem>           = emptyList(),
    val currentUser: User?               = null,
    val selectedCategory: StockCategory? = null,
    val isLoading: Boolean               = true,
    val error: String?                   = null,
)

@HiltViewModel
class StockViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getStockItemsUseCase: GetStockItemsUseCase,
    private val updateStockQuantityUseCase: UpdateStockQuantityUseCase,
    private val deleteStockItemUseCase: DeleteStockItemUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(StockUiState())
    val state = _state.asStateFlow()

    /** Currently selected category filter — drives the item list via flatMapLatest. */
    private val selectedCategory = MutableStateFlow<StockCategory?>(null)

    init {
        viewModelScope.launch {
            _state.update { it.copy(currentUser = getCurrentUserUseCase()) }
        }

        // A single coroutine that re-subscribes to the right flow whenever
        // selectedCategory changes, cancelling the previous subscription automatically.
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            selectedCategory
                .flatMapLatest { category ->
                    if (category == null) getStockItemsUseCase()
                    else getStockItemsUseCase.byCategory(category)
                }
                .collect { items ->
                    _state.update { it.copy(items = items, isLoading = false) }
                }
        }
    }

    fun filterByCategory(category: StockCategory?) {
        _state.update { it.copy(selectedCategory = category) }
        selectedCategory.value = category
    }

    fun adjustQuantity(item: StockItem, delta: Float) {
        val actor = _state.value.currentUser ?: return
        viewModelScope.launch {
            val newQty = (item.quantity + delta).coerceAtLeast(0f)
            val result = updateStockQuantityUseCase(actor, item.id, newQty)
            result.onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteItem(item: StockItem) {
        val actor = _state.value.currentUser ?: return
        viewModelScope.launch {
            val result = deleteStockItemUseCase(actor, item.id)
            result.onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

package com.familyhome.app.presentation.screens.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.CustomStockCategory
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
    val items: List<StockItem>                    = emptyList(),
    val currentUser: User?                        = null,
    val selectedCategory: StockCategory?          = null,
    val customCategories: List<CustomStockCategory> = emptyList(),
    val isLoading: Boolean                        = true,
    val error: String?                            = null,
)

@HiltViewModel
class StockViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getStockItemsUseCase: GetStockItemsUseCase,
    private val updateStockQuantityUseCase: UpdateStockQuantityUseCase,
    private val updateStockItemUseCase: UpdateStockItemUseCase,
    private val deleteStockItemUseCase: DeleteStockItemUseCase,
    private val getCustomStockCategoriesUseCase: GetCustomStockCategoriesUseCase,
    private val addCustomStockCategoryUseCase: AddCustomStockCategoryUseCase,
    private val updateCustomStockCategoryUseCase: UpdateCustomStockCategoryUseCase,
    private val deleteCustomStockCategoryUseCase: DeleteCustomStockCategoryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(StockUiState())
    val state = _state.asStateFlow()

    private val selectedCategory = MutableStateFlow<StockCategory?>(null)

    init {
        viewModelScope.launch {
            _state.update { it.copy(currentUser = getCurrentUserUseCase()) }
        }

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

        viewModelScope.launch {
            getCustomStockCategoriesUseCase().collect { cats ->
                _state.update { it.copy(customCategories = cats) }
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
            updateStockQuantityUseCase(actor, item.id, newQty)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun updateItem(item: StockItem) {
        val actor = _state.value.currentUser ?: return
        viewModelScope.launch {
            updateStockItemUseCase(actor, item)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteItem(item: StockItem) {
        val actor = _state.value.currentUser ?: return
        viewModelScope.launch {
            deleteStockItemUseCase(actor, item.id)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun addCategory(name: String, iconName: String) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            addCustomStockCategoryUseCase(user, name, iconName)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun updateCategory(category: CustomStockCategory) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            updateCustomStockCategoryUseCase(user, category)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteCategory(category: CustomStockCategory) {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            deleteCustomStockCategoryUseCase(user, category.id)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

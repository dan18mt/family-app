package com.familyhome.app.domain.usecase.stock

import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.permission.PermissionManager
import com.familyhome.app.domain.repository.StockRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class GetStockItemsUseCase @Inject constructor(
    private val stockRepository: StockRepository,
) {
    operator fun invoke(): Flow<List<StockItem>> = stockRepository.getAllItems()
    fun byCategory(category: StockCategory) = stockRepository.getItemsByCategory(category)
}

class GetLowStockItemsUseCase @Inject constructor(
    private val stockRepository: StockRepository,
) {
    operator fun invoke(): Flow<List<StockItem>> = stockRepository.getLowStockItems()
}

class AddStockItemUseCase @Inject constructor(
    private val stockRepository: StockRepository,
) {
    suspend operator fun invoke(
        actor: User,
        name: String,
        category: StockCategory,
        quantity: Float,
        unit: String,
        minQuantity: Float,
    ): Result<StockItem> {
        if (!PermissionManager.canCreateStockItem(actor)) {
            return Result.failure(IllegalStateException("You don't have permission to add items."))
        }
        val item = StockItem(
            id          = UUID.randomUUID().toString(),
            name        = name,
            category    = category,
            quantity    = quantity,
            unit        = unit,
            minQuantity = minQuantity,
            updatedBy   = actor.id,
            updatedAt   = System.currentTimeMillis(),
        )
        stockRepository.insertItem(item)
        return Result.success(item)
    }
}

class UpdateStockQuantityUseCase @Inject constructor(
    private val stockRepository: StockRepository,
) {
    suspend operator fun invoke(actor: User, itemId: String, newQuantity: Float): Result<Unit> {
        if (!PermissionManager.canUpdateStockQuantity(actor)) {
            return Result.failure(IllegalStateException("You don't have permission to update stock."))
        }
        val item = stockRepository.getItemById(itemId)
            ?: return Result.failure(NoSuchElementException("Stock item not found."))

        stockRepository.updateItem(
            item.copy(
                quantity  = newQuantity,
                updatedBy = actor.id,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return Result.success(Unit)
    }
}

class DeleteStockItemUseCase @Inject constructor(
    private val stockRepository: StockRepository,
) {
    suspend operator fun invoke(actor: User, itemId: String): Result<Unit> {
        if (!PermissionManager.canDeleteStockItem(actor)) {
            return Result.failure(IllegalStateException("You don't have permission to delete items."))
        }
        stockRepository.deleteItem(itemId)
        return Result.success(Unit)
    }
}

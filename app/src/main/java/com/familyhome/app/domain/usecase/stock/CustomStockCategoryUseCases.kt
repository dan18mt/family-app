package com.familyhome.app.domain.usecase.stock

import com.familyhome.app.domain.model.CustomStockCategory
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.CustomStockCategoryRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class GetCustomStockCategoriesUseCase @Inject constructor(
    private val repository: CustomStockCategoryRepository,
) {
    operator fun invoke(): Flow<List<CustomStockCategory>> = repository.getAllCategories()
}

class AddCustomStockCategoryUseCase @Inject constructor(
    private val repository: CustomStockCategoryRepository,
) {
    suspend operator fun invoke(
        actor: User,
        name: String,
        iconName: String,
    ): Result<CustomStockCategory> {
        if (actor.role != Role.FATHER && actor.role != Role.WIFE) {
            return Result.failure(IllegalStateException("Only family leaders can manage categories."))
        }
        val category = CustomStockCategory(
            id       = UUID.randomUUID().toString(),
            name     = name.trim(),
            iconName = iconName,
        )
        repository.insertCategory(category)
        return Result.success(category)
    }
}

class UpdateCustomStockCategoryUseCase @Inject constructor(
    private val repository: CustomStockCategoryRepository,
) {
    suspend operator fun invoke(actor: User, category: CustomStockCategory): Result<Unit> {
        if (actor.role != Role.FATHER && actor.role != Role.WIFE) {
            return Result.failure(IllegalStateException("Only family leaders can manage categories."))
        }
        repository.updateCategory(category)
        return Result.success(Unit)
    }
}

class DeleteCustomStockCategoryUseCase @Inject constructor(
    private val repository: CustomStockCategoryRepository,
) {
    suspend operator fun invoke(actor: User, categoryId: String): Result<Unit> {
        if (actor.role != Role.FATHER && actor.role != Role.WIFE) {
            return Result.failure(IllegalStateException("Only family leaders can manage categories."))
        }
        repository.deleteCategory(categoryId)
        return Result.success(Unit)
    }
}

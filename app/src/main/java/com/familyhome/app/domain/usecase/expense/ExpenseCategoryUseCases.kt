package com.familyhome.app.domain.usecase.expense

import com.familyhome.app.domain.model.CustomExpenseCategory
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.repository.CustomExpenseCategoryRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class GetCustomExpenseCategoriesUseCase @Inject constructor(
    private val repository: CustomExpenseCategoryRepository,
) {
    operator fun invoke(): Flow<List<CustomExpenseCategory>> = repository.getAllCategories()
}

class AddCustomExpenseCategoryUseCase @Inject constructor(
    private val repository: CustomExpenseCategoryRepository,
) {
    suspend operator fun invoke(
        actor: User,
        name: String,
        iconName: String,
    ): Result<CustomExpenseCategory> {
        if (actor.role != Role.FATHER && actor.role != Role.WIFE) {
            return Result.failure(IllegalStateException("Only family leaders can manage categories."))
        }
        val category = CustomExpenseCategory(
            id       = UUID.randomUUID().toString(),
            name     = name.trim(),
            iconName = iconName,
        )
        repository.insertCategory(category)
        return Result.success(category)
    }
}

class UpdateCustomExpenseCategoryUseCase @Inject constructor(
    private val repository: CustomExpenseCategoryRepository,
) {
    suspend operator fun invoke(
        actor: User,
        category: CustomExpenseCategory,
    ): Result<Unit> {
        if (actor.role != Role.FATHER && actor.role != Role.WIFE) {
            return Result.failure(IllegalStateException("Only family leaders can manage categories."))
        }
        repository.updateCategory(category)
        return Result.success(Unit)
    }
}

class DeleteCustomExpenseCategoryUseCase @Inject constructor(
    private val repository: CustomExpenseCategoryRepository,
) {
    suspend operator fun invoke(actor: User, categoryId: String): Result<Unit> {
        if (actor.role != Role.FATHER && actor.role != Role.WIFE) {
            return Result.failure(IllegalStateException("Only family leaders can manage categories."))
        }
        repository.deleteCategory(categoryId)
        return Result.success(Unit)
    }
}

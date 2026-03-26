package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.CustomExpenseCategoryEntity
import com.familyhome.app.domain.model.CustomExpenseCategory

fun CustomExpenseCategoryEntity.toDomain() = CustomExpenseCategory(
    id       = id,
    name     = name,
    iconName = iconName,
)

fun CustomExpenseCategory.toEntity() = CustomExpenseCategoryEntity(
    id       = id,
    name     = name,
    iconName = iconName,
)

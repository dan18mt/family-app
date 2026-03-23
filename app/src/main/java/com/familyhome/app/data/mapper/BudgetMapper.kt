package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.BudgetEntity
import com.familyhome.app.domain.model.Budget
import com.familyhome.app.domain.model.BudgetDto
import com.familyhome.app.domain.model.BudgetPeriod
import com.familyhome.app.domain.model.ExpenseCategory

fun BudgetEntity.toDomain() = Budget(
    id           = id,
    targetUserId = targetUserId,
    category     = category?.let { ExpenseCategory.valueOf(it) },
    limitAmount  = limitAmount,
    period       = BudgetPeriod.valueOf(period),
    setBy        = setBy,
)

fun Budget.toEntity() = BudgetEntity(
    id           = id,
    targetUserId = targetUserId,
    category     = category?.name,
    limitAmount  = limitAmount,
    period       = period.name,
    setBy        = setBy,
)

fun Budget.toDto() = BudgetDto(
    id           = id,
    targetUserId = targetUserId,
    category     = category?.name,
    limitAmount  = limitAmount,
    period       = period.name,
    setBy        = setBy,
)

fun BudgetDto.toDomain() = Budget(
    id           = id,
    targetUserId = targetUserId,
    category     = category?.let { ExpenseCategory.valueOf(it) },
    limitAmount  = limitAmount,
    period       = BudgetPeriod.valueOf(period),
    setBy        = setBy,
)

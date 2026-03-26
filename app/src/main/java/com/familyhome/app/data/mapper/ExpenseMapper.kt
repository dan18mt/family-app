package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.ExpenseEntity
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.ExpenseDto

fun ExpenseEntity.toDomain() = Expense(
    id               = id,
    amount           = amount,
    currency         = currency,
    category         = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.OTHER),
    description      = description,
    paidBy           = paidBy,
    receiptUri       = receiptUri,
    loggedAt         = loggedAt,
    expenseDate      = expenseDate,
    aiExtracted      = aiExtracted,
    customCategoryId = customCategoryId,
)

fun Expense.toEntity() = ExpenseEntity(
    id               = id,
    amount           = amount,
    currency         = currency,
    category         = category.name,
    description      = description,
    paidBy           = paidBy,
    receiptUri       = receiptUri,
    loggedAt         = loggedAt,
    expenseDate      = expenseDate,
    aiExtracted      = aiExtracted,
    customCategoryId = customCategoryId,
)

fun Expense.toDto() = ExpenseDto(
    id               = id,
    amount           = amount,
    currency         = currency,
    category         = category.name,
    description      = description,
    paidBy           = paidBy,
    receiptUri       = receiptUri,
    loggedAt         = loggedAt,
    expenseDate      = expenseDate,
    aiExtracted      = aiExtracted,
    customCategoryId = customCategoryId,
)

fun ExpenseDto.toDomain() = Expense(
    id               = id,
    amount           = amount,
    currency         = currency,
    category         = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.OTHER),
    description      = description,
    paidBy           = paidBy,
    receiptUri       = receiptUri,
    loggedAt         = loggedAt,
    expenseDate      = expenseDate,
    aiExtracted      = aiExtracted,
    customCategoryId = customCategoryId,
)

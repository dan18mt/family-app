package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.ExpenseEntity
import com.familyhome.app.domain.model.Expense
import com.familyhome.app.domain.model.ExpenseCategory
import com.familyhome.app.domain.model.ExpenseDto

fun ExpenseEntity.toDomain() = Expense(
    id          = id,
    amount      = amount,
    currency    = currency,
    category    = ExpenseCategory.valueOf(category),
    description = description,
    paidBy      = paidBy,
    receiptUri  = receiptUri,
    loggedAt    = loggedAt,
    expenseDate = expenseDate,
    aiExtracted = aiExtracted,
)

fun Expense.toEntity() = ExpenseEntity(
    id          = id,
    amount      = amount,
    currency    = currency,
    category    = category.name,
    description = description,
    paidBy      = paidBy,
    receiptUri  = receiptUri,
    loggedAt    = loggedAt,
    expenseDate = expenseDate,
    aiExtracted = aiExtracted,
)

fun Expense.toDto() = ExpenseDto(
    id          = id,
    amount      = amount,
    currency    = currency,
    category    = category.name,
    description = description,
    paidBy      = paidBy,
    receiptUri  = receiptUri,
    loggedAt    = loggedAt,
    expenseDate = expenseDate,
    aiExtracted = aiExtracted,
)

fun ExpenseDto.toDomain() = Expense(
    id          = id,
    amount      = amount,
    currency    = currency,
    category    = ExpenseCategory.valueOf(category),
    description = description,
    paidBy      = paidBy,
    receiptUri  = receiptUri,
    loggedAt    = loggedAt,
    expenseDate = expenseDate,
    aiExtracted = aiExtracted,
)

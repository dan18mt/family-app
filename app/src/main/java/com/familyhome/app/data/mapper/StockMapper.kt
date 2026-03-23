package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.StockItemEntity
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.StockItemDto

fun StockItemEntity.toDomain() = StockItem(
    id          = id,
    name        = name,
    category    = StockCategory.valueOf(category),
    quantity    = quantity,
    unit        = unit,
    minQuantity = minQuantity,
    updatedBy   = updatedBy,
    updatedAt   = updatedAt,
)

fun StockItem.toEntity() = StockItemEntity(
    id          = id,
    name        = name,
    category    = category.name,
    quantity    = quantity,
    unit        = unit,
    minQuantity = minQuantity,
    updatedBy   = updatedBy,
    updatedAt   = updatedAt,
)

fun StockItem.toDto() = StockItemDto(
    id          = id,
    name        = name,
    category    = category.name,
    quantity    = quantity,
    unit        = unit,
    minQuantity = minQuantity,
    updatedBy   = updatedBy,
    updatedAt   = updatedAt,
)

fun StockItemDto.toDomain() = StockItem(
    id          = id,
    name        = name,
    category    = StockCategory.valueOf(category),
    quantity    = quantity,
    unit        = unit,
    minQuantity = minQuantity,
    updatedBy   = updatedBy,
    updatedAt   = updatedAt,
)

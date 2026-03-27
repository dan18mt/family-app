package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.StockItemEntity
import com.familyhome.app.domain.model.StockCategory
import com.familyhome.app.domain.model.StockItem
import com.familyhome.app.domain.model.StockItemDto

fun StockItemEntity.toDomain() = StockItem(
    id               = id,
    name             = name,
    category         = runCatching { StockCategory.valueOf(category) }.getOrDefault(StockCategory.OTHER),
    quantity         = quantity,
    unit             = unit,
    minQuantity      = minQuantity,
    updatedBy        = updatedBy,
    updatedAt        = updatedAt,
    customCategoryId = customCategoryId,
)

fun StockItem.toEntity() = StockItemEntity(
    id               = id,
    name             = name,
    category         = category.name,
    quantity         = quantity,
    unit             = unit,
    minQuantity      = minQuantity,
    updatedBy        = updatedBy,
    updatedAt        = updatedAt,
    customCategoryId = customCategoryId,
)

fun StockItem.toDto() = StockItemDto(
    id               = id,
    name             = name,
    category         = category.name,
    quantity         = quantity,
    unit             = unit,
    minQuantity      = minQuantity,
    updatedBy        = updatedBy,
    updatedAt        = updatedAt,
    customCategoryId = customCategoryId,
)

fun StockItemDto.toDomain() = StockItem(
    id               = id,
    name             = name,
    category         = runCatching { StockCategory.valueOf(category) }.getOrDefault(StockCategory.OTHER),
    quantity         = quantity,
    unit             = unit,
    minQuantity      = minQuantity,
    updatedBy        = updatedBy,
    updatedAt        = updatedAt,
    customCategoryId = customCategoryId,
)

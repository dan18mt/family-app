package com.familyhome.app.domain.model

data class StockItem(
    val id: String,
    val name: String,
    val category: StockCategory,
    val quantity: Float,
    val unit: String,
    /** Quantity at or below which a low-stock alert is shown */
    val minQuantity: Float,
    val updatedBy: String,
    val updatedAt: Long,
) {
    val isLowStock: Boolean get() = quantity <= minQuantity
}

enum class StockCategory {
    FOOD,
    CLEANING,
    TOILETRY,
    OTHER;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

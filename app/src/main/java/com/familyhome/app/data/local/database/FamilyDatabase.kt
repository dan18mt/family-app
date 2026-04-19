package com.familyhome.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.familyhome.app.data.local.dao.*
import com.familyhome.app.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        StockItemEntity::class,
        ChoreLogEntity::class,
        RecurringTaskEntity::class,
        ChoreAssignmentEntity::class,
        ExpenseEntity::class,
        BudgetEntity::class,
        CustomExpenseCategoryEntity::class,
        CustomStockCategoryEntity::class,
        PrayerGoalSettingEntity::class,
        PrayerLogEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class FamilyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun stockItemDao(): StockItemDao
    abstract fun choreLogDao(): ChoreLogDao
    abstract fun recurringTaskDao(): RecurringTaskDao
    abstract fun choreAssignmentDao(): ChoreAssignmentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun customExpenseCategoryDao(): CustomExpenseCategoryDao
    abstract fun customStockCategoryDao(): CustomStockCategoryDao
    abstract fun prayerGoalSettingDao(): PrayerGoalSettingDao
    abstract fun prayerLogDao(): PrayerLogDao

    companion object {
        const val DATABASE_NAME = "family_home.db"
    }
}

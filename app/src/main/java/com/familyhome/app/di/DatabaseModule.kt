package com.familyhome.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.familyhome.app.data.local.dao.BudgetDao
import com.familyhome.app.data.local.dao.ChoreAssignmentDao
import com.familyhome.app.data.local.dao.ChoreLogDao
import com.familyhome.app.data.local.dao.CustomExpenseCategoryDao
import com.familyhome.app.data.local.dao.CustomStockCategoryDao
import com.familyhome.app.data.local.dao.ExpenseDao
import com.familyhome.app.data.local.dao.PrayerGoalSettingDao
import com.familyhome.app.data.local.dao.PrayerLogDao
import com.familyhome.app.data.local.dao.RecurringTaskDao
import com.familyhome.app.data.local.dao.StockItemDao
import com.familyhome.app.data.local.dao.UserDao
import com.familyhome.app.data.local.database.FamilyDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `prayer_goal_settings` (
                    `id` TEXT NOT NULL,
                    `sunnahKey` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL,
                    `assignedTo` TEXT,
                    `createdBy` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `prayer_logs` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `sunnahKey` TEXT NOT NULL,
                    `epochDay` INTEGER NOT NULL,
                    `completedCount` INTEGER NOT NULL,
                    `loggedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_prayer_logs_userId_sunnahKey_epochDay`
                ON `prayer_logs` (`userId`, `sunnahKey`, `epochDay`)
            """.trimIndent())
        }
    }

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FamilyDatabase =
        Room.databaseBuilder(context, FamilyDatabase::class.java, FamilyDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigrationFrom(1, 2)
            .addMigrations(MIGRATION_3_4)
            .build()

    @Provides fun provideUserDao(db: FamilyDatabase):                       UserDao                    = db.userDao()
    @Provides fun provideStockItemDao(db: FamilyDatabase):                  StockItemDao               = db.stockItemDao()
    @Provides fun provideChoreLogDao(db: FamilyDatabase):                   ChoreLogDao                = db.choreLogDao()
    @Provides fun provideRecurringTaskDao(db: FamilyDatabase):              RecurringTaskDao           = db.recurringTaskDao()
    @Provides fun provideChoreAssignmentDao(db: FamilyDatabase):            ChoreAssignmentDao         = db.choreAssignmentDao()
    @Provides fun provideExpenseDao(db: FamilyDatabase):                    ExpenseDao                 = db.expenseDao()
    @Provides fun provideBudgetDao(db: FamilyDatabase):                     BudgetDao                  = db.budgetDao()
    @Provides fun provideCustomExpenseCategoryDao(db: FamilyDatabase):      CustomExpenseCategoryDao   = db.customExpenseCategoryDao()
    @Provides fun provideCustomStockCategoryDao(db: FamilyDatabase):        CustomStockCategoryDao     = db.customStockCategoryDao()
    @Provides fun providePrayerGoalSettingDao(db: FamilyDatabase):          PrayerGoalSettingDao       = db.prayerGoalSettingDao()
    @Provides fun providePrayerLogDao(db: FamilyDatabase):                  PrayerLogDao               = db.prayerLogDao()
}

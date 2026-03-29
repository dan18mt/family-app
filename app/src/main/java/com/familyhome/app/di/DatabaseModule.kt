package com.familyhome.app.di

import android.content.Context
import androidx.room.Room
import com.familyhome.app.data.local.dao.BudgetDao
import com.familyhome.app.data.local.dao.ChoreAssignmentDao
import com.familyhome.app.data.local.dao.ChoreLogDao
import com.familyhome.app.data.local.dao.CustomExpenseCategoryDao
import com.familyhome.app.data.local.dao.CustomStockCategoryDao
import com.familyhome.app.data.local.dao.ExpenseDao
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

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FamilyDatabase =
        Room.databaseBuilder(context, FamilyDatabase::class.java, FamilyDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigrationFrom(1, 2) // only wipe truly old DBs; new version bumps must have migrations
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
}

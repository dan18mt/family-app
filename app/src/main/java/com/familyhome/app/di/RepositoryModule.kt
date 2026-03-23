package com.familyhome.app.di

import com.familyhome.app.data.repository.*
import com.familyhome.app.data.session.SessionRepositoryImpl
import com.familyhome.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindStockRepository(impl: StockRepositoryImpl): StockRepository

    @Binds @Singleton
    abstract fun bindChoreRepository(impl: ChoreRepositoryImpl): ChoreRepository

    @Binds @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}

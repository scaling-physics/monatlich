package com.monatlich.di

import com.monatlich.data.repository.BudgetRepositoryImpl
import com.monatlich.data.repository.CategoryRepositoryImpl
import com.monatlich.data.repository.ExchangeRateRepositoryImpl
import com.monatlich.data.repository.RecurringRepositoryImpl
import com.monatlich.data.repository.SettingsRepositoryImpl
import com.monatlich.data.repository.TransactionRepositoryImpl
import com.monatlich.domain.repository.BudgetRepository
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.ExchangeRateRepository
import com.monatlich.domain.repository.RecurringRepository
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindExchangeRateRepository(impl: ExchangeRateRepositoryImpl): ExchangeRateRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRecurringRepository(impl: RecurringRepositoryImpl): RecurringRepository
}

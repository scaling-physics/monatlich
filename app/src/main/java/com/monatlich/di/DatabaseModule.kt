package com.monatlich.di

import android.content.Context
import androidx.room.Room
import com.monatlich.data.local.BudgetDao
import com.monatlich.data.local.CategoryDao
import com.monatlich.data.local.ExchangeRateDao
import com.monatlich.data.local.Migrations
import com.monatlich.data.local.MonatlichDatabase
import com.monatlich.data.local.RecurringTransactionDao
import com.monatlich.data.local.SeedCallback
import com.monatlich.data.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * No `fallbackToDestructiveMigration`: every schema change ships an explicit `Migration`
     * (collected in [Migrations.ALL]) plus an exported schema in `app/schemas/`.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MonatlichDatabase =
        Room.databaseBuilder(context, MonatlichDatabase::class.java, MonatlichDatabase.NAME)
            .addCallback(SeedCallback())
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideCategoryDao(db: MonatlichDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideBudgetDao(db: MonatlichDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideTransactionDao(db: MonatlichDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideExchangeRateDao(db: MonatlichDatabase): ExchangeRateDao = db.exchangeRateDao()

    @Provides
    fun provideRecurringTransactionDao(db: MonatlichDatabase): RecurringTransactionDao = db.recurringTransactionDao()
}

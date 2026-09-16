package com.monatlich.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app's single Room database.
 *
 * Schema changes bump [VERSION] and ship a `Migration` in [Migrations]; the exported schema JSON
 * lives in `app/schemas/` and must be committed alongside.
 */
@Database(
    entities = [
        CategoryEntity::class,
        BudgetEntity::class,
        TransactionEntity::class,
        ExchangeRateEntity::class,
        RecurringTransactionEntity::class,
    ],
    version = MonatlichDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MonatlichDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun transactionDao(): TransactionDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao

    companion object {
        const val NAME = "monatlich.db"
        /**
         * 1: M2 baseline · 2: M8 recurring transactions ([Migrations.MIGRATION_1_2]) ·
         * 3: budget rollover ([Migrations.MIGRATION_2_3]).
         */
        const val VERSION = 3
    }
}

package com.monatlich.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema step the app has shipped, oldest first. [DatabaseModule] registers [ALL]; each
 * step is covered by `MigrationTest` against the exported schemas in `app/schemas/`.
 */
object Migrations {

    /**
     * v1 → v2 (M8, recurring transactions): new `recurring_transactions` table plus a nullable
     * `transactions.recurringId` column that points at it (`ON DELETE SET NULL`, so a generated
     * entry outlives its rule). Existing rows are untouched and get `recurringId = NULL`.
     *
     * SQLite permits `ADD COLUMN ... REFERENCES` only when the column defaults to NULL, which is
     * exactly what we need, so no table rebuild is required.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_transactions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`categoryId` INTEGER NOT NULL, " +
                    "`amountMinor` INTEGER NOT NULL, " +
                    "`currencyCode` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`note` TEXT, " +
                    "`dayOfMonth` INTEGER NOT NULL, " +
                    "`startMonth` TEXT NOT NULL, " +
                    "`endMonth` TEXT, " +
                    "`active` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_transactions_categoryId` " +
                    "ON `recurring_transactions` (`categoryId`)",
            )
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `recurringId` INTEGER " +
                    "REFERENCES `recurring_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_recurringId` ON `transactions` (`recurringId`)",
            )
        }
    }

    /**
     * v2 → v3 (budget rollover): new `categories.rolloverEnabled` column, defaulting to `0`
     * (false) so every existing category keeps its current, non-cumulative behaviour.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `rolloverEnabled` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}

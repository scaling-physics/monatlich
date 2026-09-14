package com.monatlich.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monatlich.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Runs [Migrations] against a real on-disk database created with the *exported* v1 DDL
 * (`app/schemas/.../1.json`), then opens it through Room. Room validates the migrated schema
 * against the current entities on open, so a mismatch fails the test with
 * "Migration didn't properly handle …" exactly as `MigrationTestHelper.runMigrationsAndValidate`
 * would. The helper itself is not used because `room-testing` is only on the unit-test classpath.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private var db: MonatlichDatabase? = null

    @Before
    fun deleteStaleFile() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2_keepsRowsAndAddsNullRecurringId() = runBlocking {
        createVersion1Database()

        val migrated = openWithMigrations()

        val categories = migrated.categoryDao().observeAll().first()
        assertEquals(listOf("Groceries", "Rent"), categories.map { it.name })

        val transactions = migrated.transactionDao().observeForMonth(YearMonth.of(2026, 9)).first()
        assertEquals(1, transactions.size)
        val tx = transactions.single()
        assertEquals(LocalDate.of(2026, 9, 1), tx.date)
        assertEquals(120_000L, tx.amountMinor)
        assertEquals("EUR", tx.currencyCode)
        assertEquals(BigDecimal("1"), tx.rateToBase)
        assertEquals("Flat", tx.note)
        assertNull(tx.recurringId)

        val budgets = migrated.budgetDao().getForMonth(YearMonth.of(2026, 9))
        assertEquals(listOf(50_000L), budgets.map { it.amountMinor })
        assertEquals(BigDecimal("0.92"), migrated.exchangeRateDao().get("USD")!!.rateToBase)
    }

    @Test
    fun migrate1To2_newTableWorksAndDeletingRuleNullsLink() = runBlocking {
        createVersion1Database()
        val migrated = openWithMigrations()
        val rentId = migrated.categoryDao().observeAll().first().first { it.name == "Rent" }.id

        val ruleId = migrated.recurringTransactionDao().insert(
            RecurringTransactionEntity(
                categoryId = rentId,
                amountMinor = 120_000,
                currencyCode = "EUR",
                type = TransactionType.EXPENSE,
                note = null,
                dayOfMonth = 1,
                startMonth = YearMonth.of(2026, 9),
                endMonth = null,
                active = true,
            ),
        )
        val existing = migrated.transactionDao().observeForMonth(YearMonth.of(2026, 9)).first().single()
        migrated.transactionDao().update(existing.copy(recurringId = ruleId))
        assertEquals(ruleId, migrated.transactionDao().getById(existing.id)!!.recurringId)
        assertEquals(1, migrated.transactionDao().getForRecurring(ruleId, YearMonth.of(2026, 9)).size)

        migrated.recurringTransactionDao().deleteById(ruleId)

        assertNull(migrated.transactionDao().getById(existing.id)!!.recurringId)
        assertTrue(migrated.recurringTransactionDao().observeAll().first().isEmpty())
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** The v1 schema verbatim from `1.json`, plus a few rows and `user_version = 1`. */
    private fun createVersion1Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.use { sql ->
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, `archived` INTEGER NOT NULL)",
            )
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`categoryId` INTEGER NOT NULL, `month` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, " +
                    "`currencyCode` TEXT NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                    "ON UPDATE RESTRICT ON DELETE RESTRICT )",
            )
            sql.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_month` ON `budgets` (`categoryId`, `month`)")
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`categoryId` INTEGER NOT NULL, `month` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                    "`amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, `note` TEXT, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                    "ON UPDATE RESTRICT ON DELETE RESTRICT )",
            )
            sql.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_month` ON `transactions` (`month`)")
            sql.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
            sql.execSQL(
                "CREATE TABLE IF NOT EXISTS `exchange_rates` (`currencyCode` TEXT NOT NULL, `rateToBase` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`currencyCode`))",
            )
            sql.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sql.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V1_IDENTITY_HASH')")

            sql.execSQL("INSERT INTO categories (id, name, icon, color, sortOrder, archived) VALUES (1, 'Groceries', 'ShoppingCart', 4281175926, 0, 0)")
            sql.execSQL("INSERT INTO categories (id, name, icon, color, sortOrder, archived) VALUES (2, 'Rent', 'Home', 4285210410, 1, 0)")
            sql.execSQL("INSERT INTO budgets (categoryId, month, amountMinor, currencyCode) VALUES (1, '2026-09', 50000, 'EUR')")
            sql.execSQL(
                "INSERT INTO transactions (categoryId, month, date, amountMinor, currencyCode, rateToBase, type, note) " +
                    "VALUES (2, '2026-09', '2026-09-01', 120000, 'EUR', '1', 'EXPENSE', 'Flat')",
            )
            sql.execSQL("INSERT INTO exchange_rates (currencyCode, rateToBase, updatedAt) VALUES ('USD', '0.92', 0)")
            sql.version = 1
        }
    }

    private fun openWithMigrations(): MonatlichDatabase =
        Room.databaseBuilder(context, MonatlichDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    private companion object {
        /** `identityHash` of `app/schemas/.../1.json`. */
        const val V1_IDENTITY_HASH = "c0d11c7854601ec21da5c527fb5f6012"
    }
}

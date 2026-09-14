package com.monatlich.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Exercises every DAO against a real (in-memory) SQLite database, including the seed callback,
 * the unique budget index, the RESTRICT / SET NULL foreign keys and the grouped-totals query.
 */
@RunWith(AndroidJUnit4::class)
class MonatlichDatabaseTest {

    private lateinit var db: MonatlichDatabase
    private val seedTime = Instant.parse("2026-09-13T00:00:00Z")
    private val september = YearMonth.of(2026, 9)

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MonatlichDatabase::class.java)
            .addCallback(SeedCallback(now = { seedTime }))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    // ---- seed --------------------------------------------------------------------------------

    @Test
    fun seedInsertsDefaultCategoriesAndRates() = runBlocking {
        val categories = db.categoryDao().observeAll().first()
        assertEquals(Seed.categories.map { it.name }, categories.map { it.name })
        assertEquals(Seed.categories.map { it.icon }, categories.map { it.icon })
        assertEquals(Seed.categories.map { it.color }, categories.map { it.color })
        assertEquals((0 until Seed.categories.size).toList(), categories.map { it.sortOrder })
        assertTrue(categories.none { it.archived })

        val rates = db.exchangeRateDao().observeAll().first()
        assertEquals(Currency.entries.map { it.code }.sorted(), rates.map { it.currencyCode })
        rates.forEach {
            assertEquals(0, BigDecimal.ONE.compareTo(it.rateToBase))
            assertEquals(seedTime, it.updatedAt)
        }
    }

    // ---- categories --------------------------------------------------------------------------

    @Test
    fun categoryDao_insertAtEndAppendsAndActiveFilterHidesArchived() = runBlocking {
        val dao = db.categoryDao()
        val before = dao.count()

        val id = dao.insertAtEnd(CategoryEntity(name = "Pets", icon = "Pets", color = 0xFF000000, sortOrder = -1))
        val inserted = dao.getById(id)!!
        assertEquals(before, inserted.sortOrder)
        assertEquals("Pets", inserted.name)

        dao.setArchived(id, true)
        assertTrue(dao.observeActive().first().none { it.id == id })
        assertTrue(dao.observeAll().first().any { it.id == id && it.archived })
        assertEquals(true, dao.observeById(id).first()?.archived)
    }

    @Test
    fun categoryDao_reorderRewritesSortOrder() = runBlocking {
        val dao = db.categoryDao()
        val ids = dao.observeAll().first().map { it.id }
        dao.reorder(ids.reversed())
        assertEquals(ids.reversed(), dao.observeAll().first().map { it.id })
    }

    @Test
    fun categoryDao_updateRenames() = runBlocking {
        val dao = db.categoryDao()
        val groceries = dao.observeAll().first().first { it.name == "Groceries" }
        dao.update(groceries.copy(name = "Food", color = 0xFF123456))
        val updated = dao.getById(groceries.id)!!
        assertEquals("Food", updated.name)
        assertEquals(0xFF123456, updated.color)
    }

    // ---- budgets -----------------------------------------------------------------------------

    @Test
    fun budgetDao_upsertRespectsUniqueCategoryMonth() = runBlocking {
        val dao = db.budgetDao()
        val categoryId = firstCategoryId()

        val id = dao.upsert(BudgetEntity(categoryId = categoryId, month = september, amountMinor = 40000, currencyCode = "EUR"))
        val same = dao.upsert(BudgetEntity(categoryId = categoryId, month = september, amountMinor = 45000, currencyCode = "USD"))

        assertEquals(id, same)
        val stored = dao.get(categoryId, september)!!
        assertEquals(45000L, stored.amountMinor)
        assertEquals("USD", stored.currencyCode)
        assertEquals(1, dao.observeForMonth(september).first().size)
        assertEquals(stored, dao.observe(categoryId, september).first())
    }

    @Test
    fun budgetDao_duplicateInsertViolatesUniqueIndex() = runBlocking {
        val dao = db.budgetDao()
        val categoryId = firstCategoryId()
        dao.insert(BudgetEntity(categoryId = categoryId, month = september, amountMinor = 1, currencyCode = "EUR"))
        try {
            dao.insert(BudgetEntity(categoryId = categoryId, month = september, amountMinor = 2, currencyCode = "EUR"))
            fail("Expected UNIQUE constraint violation")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
    }

    @Test
    fun budgetDao_deleteAndMonthIsolation() = runBlocking {
        val dao = db.budgetDao()
        val categoryId = firstCategoryId()
        dao.insert(BudgetEntity(categoryId = categoryId, month = september, amountMinor = 1, currencyCode = "EUR"))
        dao.insert(BudgetEntity(categoryId = categoryId, month = september.plusMonths(1), amountMinor = 2, currencyCode = "EUR"))

        assertEquals(1, dao.getForMonth(september).size)
        dao.delete(categoryId, september)
        assertNull(dao.get(categoryId, september))
        assertEquals(1, dao.getForMonth(september.plusMonths(1)).size)
    }

    @Test
    fun budgetDao_rejectsUnknownCategory() = runBlocking {
        try {
            db.budgetDao().insert(BudgetEntity(categoryId = 9999, month = september, amountMinor = 1, currencyCode = "EUR"))
            fail("Expected FOREIGN KEY constraint violation")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
    }

    // ---- transactions ------------------------------------------------------------------------

    @Test
    fun transactionDao_insertQueryUpdateDelete() = runBlocking {
        val dao = db.transactionDao()
        val categoryId = firstCategoryId()

        val id = dao.insert(transaction(categoryId, "2026-09-14", 1250, "EUR", "1"))
        val loaded = dao.getById(id)!!
        assertEquals(LocalDate.of(2026, 9, 14), loaded.date)
        assertEquals(september, loaded.month)
        assertEquals(BigDecimal("1"), loaded.rateToBase)
        assertEquals(TransactionType.EXPENSE, loaded.type)
        assertEquals(loaded, dao.observeById(id).first())

        dao.update(loaded.copy(note = "Bread", amountMinor = 1300))
        assertEquals("Bread", dao.getById(id)!!.note)
        assertEquals(1300L, dao.getById(id)!!.amountMinor)

        dao.deleteById(id)
        assertNull(dao.getById(id))
        assertNull(dao.observeById(id).first())
    }

    @Test
    fun transactionDao_monthQueriesFilterAndOrderNewestFirst() = runBlocking {
        val dao = db.transactionDao()
        val ids = db.categoryDao().observeAll().first().map { it.id }

        dao.insert(transaction(ids[0], "2026-09-01", 100, "EUR", "1"))
        val newest = dao.insert(transaction(ids[0], "2026-09-20", 200, "EUR", "1"))
        val newestSameDay = dao.insert(transaction(ids[1], "2026-09-20", 300, "EUR", "1"))
        dao.insert(transaction(ids[0], "2026-10-01", 400, "EUR", "1"))

        val month = dao.observeForMonth(september).first()
        assertEquals(listOf(300L, 200L, 100L), month.map { it.amountMinor })
        assertEquals(listOf(newestSameDay, newest), month.take(2).map { it.id })

        val category = dao.observeForCategoryAndMonth(ids[0], september).first()
        assertEquals(listOf(200L, 100L), category.map { it.amountMinor })
    }

    @Test
    fun transactionDao_groupedTotalsPerCategoryCurrencyAndRate() = runBlocking {
        val dao = db.transactionDao()
        val ids = db.categoryDao().observeAll().first().map { it.id }
        val groceries = ids[0]
        val travel = ids[2]

        dao.insert(transaction(groceries, "2026-09-02", 5530, "EUR", "1"))
        dao.insert(transaction(groceries, "2026-09-10", 123456, "INR", "0.0108"))
        dao.insert(transaction(groceries, "2026-09-11", 200000, "INR", "0.0108"))
        dao.insert(transaction(groceries, "2026-09-12", 100000, "INR", "0.0110"))
        dao.insert(transaction(travel, "2026-09-20", 10000, "USD", "0.90"))
        dao.insert(transaction(travel, "2026-08-20", 10000, "USD", "0.90")) // other month
        dao.insert(transaction(groceries, "2026-09-25", 250000, "EUR", "1", TransactionType.INCOME))

        val spent = dao.observeTotalsByCategory(september, TransactionType.EXPENSE).first()
        assertEquals(
            listOf(
                CategoryTotalRow(groceries, "EUR", BigDecimal("1"), 5530),
                CategoryTotalRow(groceries, "INR", BigDecimal("0.0108"), 323456),
                CategoryTotalRow(groceries, "INR", BigDecimal("0.0110"), 100000),
                CategoryTotalRow(travel, "USD", BigDecimal("0.90"), 10000),
            ),
            spent,
        )

        val income = dao.observeTotalsByCategory(september, TransactionType.INCOME).first()
        assertEquals(listOf(CategoryTotalRow(groceries, "EUR", BigDecimal("1"), 250000)), income)

        assertTrue(dao.observeTotalsByCategory(YearMonth.of(2027, 1), TransactionType.EXPENSE).first().isEmpty())
    }

    @Test
    fun transactionDao_rejectsUnknownCategory() = runBlocking {
        try {
            db.transactionDao().insert(transaction(9999, "2026-09-01", 1, "EUR", "1"))
            fail("Expected FOREIGN KEY constraint violation")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
    }

    @Test
    fun transactionDao_getForRecurringFiltersByRuleAndMonth() = runBlocking {
        val dao = db.transactionDao()
        val categoryId = firstCategoryId()
        val rule = db.recurringTransactionDao().insert(recurringRule(categoryId))
        val other = db.recurringTransactionDao().insert(recurringRule(categoryId, day = 2))

        dao.insert(transaction(categoryId, "2026-09-01", 100, "EUR", "1").copy(recurringId = rule))
        dao.insert(transaction(categoryId, "2026-10-01", 100, "EUR", "1").copy(recurringId = rule))
        dao.insert(transaction(categoryId, "2026-09-02", 100, "EUR", "1").copy(recurringId = other))
        dao.insert(transaction(categoryId, "2026-09-03", 100, "EUR", "1")) // manual

        val rows = dao.getForRecurring(rule, september)
        assertEquals(listOf(LocalDate.of(2026, 9, 1)), rows.map { it.date })
        assertEquals(rule, rows.single().recurringId)
        assertTrue(dao.getForRecurring(rule, YearMonth.of(2026, 11)).isEmpty())
        assertEquals(4, dao.observeForMonth(september).first().size + dao.observeForMonth(september.plusMonths(1)).first().size)
    }

    @Test
    fun transactionDao_rejectsUnknownRecurringRule() = runBlocking {
        try {
            db.transactionDao().insert(transaction(firstCategoryId(), "2026-09-01", 1, "EUR", "1").copy(recurringId = 9999))
            fail("Expected FOREIGN KEY constraint violation")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
    }

    // ---- recurring transactions --------------------------------------------------------------

    @Test
    fun recurringDao_insertQueryUpdateSetActiveDelete() = runBlocking {
        val dao = db.recurringTransactionDao()
        val categoryId = firstCategoryId()

        val id = dao.insert(recurringRule(categoryId, day = 15, endMonth = YearMonth.of(2027, 8)))
        val loaded = dao.getById(id)!!
        assertEquals(15, loaded.dayOfMonth)
        assertEquals(september, loaded.startMonth)
        assertEquals(YearMonth.of(2027, 8), loaded.endMonth)
        assertEquals(TransactionType.EXPENSE, loaded.type)
        assertTrue(loaded.active)
        assertEquals(loaded, dao.observeById(id).first())

        dao.update(loaded.copy(amountMinor = 1_499, note = "Streaming", endMonth = null))
        val updated = dao.getById(id)!!
        assertEquals(1_499L, updated.amountMinor)
        assertEquals("Streaming", updated.note)
        assertNull(updated.endMonth)

        dao.setActive(id, false)
        assertEquals(false, dao.getById(id)!!.active)

        dao.deleteById(id)
        assertNull(dao.getById(id))
        assertNull(dao.observeById(id).first())
    }

    @Test
    fun recurringDao_observeAllOrdersByDayThenId() = runBlocking {
        val dao = db.recurringTransactionDao()
        val categoryId = firstCategoryId()
        val late = dao.insert(recurringRule(categoryId, day = 28))
        val early = dao.insert(recurringRule(categoryId, day = 1))
        val earlyAgain = dao.insert(recurringRule(categoryId, day = 1))

        assertEquals(listOf(early, earlyAgain, late), dao.observeAll().first().map { it.id })
    }

    @Test
    fun recurringDao_deleteNullsRecurringIdOnGeneratedTransactions() = runBlocking {
        val categoryId = firstCategoryId()
        val rule = db.recurringTransactionDao().insert(recurringRule(categoryId))
        val txId = db.transactionDao().insert(transaction(categoryId, "2026-09-01", 100, "EUR", "1").copy(recurringId = rule))

        db.recurringTransactionDao().deleteById(rule)

        val survivor = db.transactionDao().getById(txId)!!
        assertNull(survivor.recurringId)
        assertEquals(100L, survivor.amountMinor)
    }

    @Test
    fun recurringDao_rejectsUnknownCategory() = runBlocking {
        try {
            db.recurringTransactionDao().insert(recurringRule(categoryId = 9999))
            fail("Expected FOREIGN KEY constraint violation")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
    }

    // ---- exchange rates ----------------------------------------------------------------------

    @Test
    fun exchangeRateDao_upsertReplacesByCurrency() = runBlocking {
        val dao = db.exchangeRateDao()
        val later = seedTime.plusSeconds(60)

        dao.upsert(ExchangeRateEntity("INR", BigDecimal("0.0108"), later))

        assertEquals(Currency.entries.size, dao.count())
        val inr = dao.get("INR")!!
        assertEquals(BigDecimal("0.0108"), inr.rateToBase)
        assertEquals(later, inr.updatedAt)
        assertEquals(inr, dao.observe("INR").first())
        assertNull(dao.get("XXX"))
    }

    // ---- helpers -----------------------------------------------------------------------------

    private suspend fun firstCategoryId(): Long = db.categoryDao().observeAll().first().first().id

    private fun recurringRule(
        categoryId: Long,
        day: Int = 1,
        endMonth: YearMonth? = null,
    ) = RecurringTransactionEntity(
        categoryId = categoryId,
        amountMinor = 120_000,
        currencyCode = "EUR",
        type = TransactionType.EXPENSE,
        note = null,
        dayOfMonth = day,
        startMonth = september,
        endMonth = endMonth,
        active = true,
    )

    private fun transaction(
        categoryId: Long,
        date: String,
        amountMinor: Long,
        currencyCode: String,
        rate: String,
        type: TransactionType = TransactionType.EXPENSE,
    ): TransactionEntity {
        val localDate = LocalDate.parse(date)
        return TransactionEntity(
            categoryId = categoryId,
            month = YearMonth.from(localDate),
            date = localDate,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            rateToBase = BigDecimal(rate),
            type = type,
            note = null,
        )
    }
}

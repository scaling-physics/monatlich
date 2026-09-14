package com.monatlich.data.repository

import app.cash.turbine.test
import com.monatlich.data.local.CategoryTotalRow
import com.monatlich.data.local.TransactionDao
import com.monatlich.data.local.TransactionEntity
import com.monatlich.domain.model.Currency.EUR
import com.monatlich.domain.model.Currency.INR
import com.monatlich.domain.model.Currency.USD
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** In-memory [TransactionDao] that reproduces the SQL semantics the repository relies on. */
private class FakeTransactionDao : TransactionDao {
    val rows = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = 1L

    private fun ordered(list: List<TransactionEntity>) =
        list.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })

    override fun observeForMonth(month: YearMonth): Flow<List<TransactionEntity>> =
        rows.map { list -> ordered(list.filter { it.month == month }) }

    override fun observeForCategoryAndMonth(categoryId: Long, month: YearMonth): Flow<List<TransactionEntity>> =
        rows.map { list -> ordered(list.filter { it.month == month && it.categoryId == categoryId }) }

    override fun observeById(id: Long): Flow<TransactionEntity?> = rows.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getById(id: Long): TransactionEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun insert(entity: TransactionEntity): Long {
        check(entity.id == 0L) { "autoGenerate expects id 0" }
        val id = nextId++
        rows.update { it + entity.copy(id = id) }
        return id
    }

    override suspend fun update(entity: TransactionEntity) =
        rows.update { list -> list.map { if (it.id == entity.id) entity else it } }

    override suspend fun deleteById(id: Long) = rows.update { list -> list.filterNot { it.id == id } }

    override suspend fun getForRecurring(recurringId: Long, month: YearMonth): List<TransactionEntity> =
        rows.value.filter { it.recurringId == recurringId && it.month == month }.sortedWith(compareBy({ it.date }, { it.id }))

    override fun observeTotalsByCategory(month: YearMonth, type: TransactionType): Flow<List<CategoryTotalRow>> =
        rows.map { list ->
            list.filter { it.month == month && it.type == type }
                .groupBy { Triple(it.categoryId, it.currencyCode, it.rateToBase) }
                .map { (key, group) -> CategoryTotalRow(key.first, key.second, key.third, group.sumOf { it.amountMinor }) }
                .sortedWith(compareBy({ it.categoryId }, { it.currencyCode }, { it.rateToBase }))
        }
}

class TransactionRepositoryImplTest {

    private val dao = FakeTransactionDao()
    private val repo = TransactionRepositoryImpl(dao)
    private val september = YearMonth.of(2026, 9)

    private fun expense(categoryId: Long, date: String, amount: Money, rate: String = "1", note: String? = null) =
        Transaction(
            categoryId = categoryId,
            date = LocalDate.parse(date),
            amount = amount,
            rateToBase = BigDecimal(rate),
            type = TransactionType.EXPENSE,
            note = note,
        )

    @Test
    fun `add stores the entity with month derived from date and round-trips`() = runTest {
        val id = repo.add(expense(1, "2026-09-14", Money(1250, EUR), note = "Bread"))

        val stored = dao.rows.value.single()
        assertEquals(id, stored.id)
        assertEquals(september, stored.month)
        assertEquals(LocalDate.of(2026, 9, 14), stored.date)
        assertEquals("EUR", stored.currencyCode)
        assertEquals(1250L, stored.amountMinor)

        val loaded = repo.get(id)!!
        assertEquals(expense(1, "2026-09-14", Money(1250, EUR), note = "Bread").copy(id = id), loaded)
        assertEquals(september, loaded.month)
    }

    @Test
    fun `add ignores a caller-supplied id`() = runTest {
        val id = repo.add(expense(1, "2026-09-14", Money(100, EUR)).copy(id = 999L))
        assertEquals(1L, id)
    }

    @Test
    fun `observeForMonth filters by month and orders newest first`() = runTest {
        repo.add(expense(1, "2026-09-01", Money(100, EUR)))
        repo.add(expense(1, "2026-09-20", Money(200, EUR)))
        repo.add(expense(1, "2026-09-20", Money(300, EUR)))
        repo.add(expense(1, "2026-10-01", Money(400, EUR)))

        val list = repo.observeForMonth(september).first()
        assertEquals(listOf(300L, 200L, 100L), list.map { it.amount.amountMinor })
    }

    @Test
    fun `observeForCategory narrows to one category`() = runTest {
        repo.add(expense(1, "2026-09-01", Money(100, EUR)))
        repo.add(expense(2, "2026-09-02", Money(200, EUR)))

        assertEquals(listOf(200L), repo.observeForCategory(2, september).first().map { it.amount.amountMinor })
    }

    @Test
    fun `update rewrites the row and moves it across months when the date changes`() = runTest {
        val id = repo.add(expense(1, "2026-09-14", Money(1250, EUR)))
        val moved = repo.get(id)!!.copy(date = LocalDate.of(2026, 10, 2), amount = Money(9999, EUR))
        repo.update(moved)

        assertEquals(YearMonth.of(2026, 10), dao.rows.value.single().month)
        assertEquals(emptyList<Transaction>(), repo.observeForMonth(september).first())
        assertEquals(moved, repo.observeForMonth(YearMonth.of(2026, 10)).first().single())
    }

    @Test
    fun `delete removes the row and observe emits null`() = runTest {
        val id = repo.add(expense(1, "2026-09-14", Money(1250, EUR)))
        repo.observe(id).test {
            assertEquals(id, awaitItem()!!.id)
            repo.delete(id)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals convert each currency group with its own stored rate and sum per category`() = runTest {
        repo.add(expense(1, "2026-09-02", Money(5530, EUR)))
        repo.add(expense(1, "2026-09-10", Money(123456, INR), rate = "0.0108"))
        repo.add(expense(1, "2026-09-11", Money(200000, INR), rate = "0.0108"))
        repo.add(expense(1, "2026-09-12", Money(100000, INR), rate = "0.0110")) // same currency, later rate
        repo.add(expense(2, "2026-09-20", Money(10000, USD), rate = "0.90"))
        repo.add(expense(3, "2026-08-20", Money(10000, USD), rate = "0.90")) // other month
        repo.add(
            expense(1, "2026-09-25", Money(250000, EUR)).copy(type = TransactionType.INCOME), // income, not spent
        )

        val spent = repo.observeTotalsByCategoryInBase(september, TransactionType.EXPENSE, EUR).first()
        // Groceries: 55.30 + 3234.56×0.0108 (=34.93) + 1000.00×0.0110 (=11.00) = 101.23
        assertEquals(Money(10123, EUR), spent[1L])
        assertEquals(Money(9000, EUR), spent[2L])
        assertEquals(setOf(1L, 2L), spent.keys)

        val income = repo.observeTotalsByCategoryInBase(september, TransactionType.INCOME, EUR).first()
        assertEquals(mapOf(1L to Money(250000, EUR)), income)
    }

    @Test
    fun `totals in a different base re-label amounts already in that currency without conversion`() = runTest {
        repo.add(expense(2, "2026-09-20", Money(10000, USD), rate = "0.90"))
        val spent = repo.observeTotalsByCategoryInBase(september, TransactionType.EXPENSE, USD).first()
        assertEquals(Money(10000, USD), spent[2L])
    }

    @Test
    fun `totals are empty for a month without transactions`() = runTest {
        assertEquals(emptyMap<Long, Money>(), repo.observeTotalsByCategoryInBase(september, TransactionType.EXPENSE, EUR).first())
    }
}

package com.monatlich.data.repository

import com.monatlich.data.local.BudgetDao
import com.monatlich.data.local.BudgetEntity
import com.monatlich.domain.model.Currency.EUR
import com.monatlich.domain.model.Currency.USD
import com.monatlich.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.YearMonth

/** In-memory [BudgetDao] honouring the unique (categoryId, month) index. */
private class FakeBudgetDao : BudgetDao {
    val rows = MutableStateFlow<List<BudgetEntity>>(emptyList())
    private var nextId = 1L

    override fun observeForMonth(month: YearMonth): Flow<List<BudgetEntity>> = rows.map { list -> list.filter { it.month == month } }
    override suspend fun getForMonth(month: YearMonth): List<BudgetEntity> = rows.value.filter { it.month == month }
    override fun observe(categoryId: Long, month: YearMonth): Flow<BudgetEntity?> =
        rows.map { list -> list.firstOrNull { it.categoryId == categoryId && it.month == month } }
    override suspend fun get(categoryId: Long, month: YearMonth): BudgetEntity? =
        rows.value.firstOrNull { it.categoryId == categoryId && it.month == month }

    override suspend fun insert(entity: BudgetEntity): Long {
        if (get(entity.categoryId, entity.month) != null) throw IllegalStateException("UNIQUE constraint failed")
        val id = nextId++
        rows.update { it + entity.copy(id = id) }
        return id
    }

    override suspend fun update(entity: BudgetEntity) = rows.update { list -> list.map { if (it.id == entity.id) entity else it } }
    override suspend fun delete(categoryId: Long, month: YearMonth) =
        rows.update { list -> list.filterNot { it.categoryId == categoryId && it.month == month } }
}

class BudgetRepositoryImplTest {

    private val dao = FakeBudgetDao()
    private val repo = BudgetRepositoryImpl(dao)
    private val aug = YearMonth.of(2026, 8)
    private val sep = YearMonth.of(2026, 9)

    @Test
    fun `set inserts once then updates in place keeping the id`() = runTest {
        val id = repo.set(1, sep, Money(40000, EUR))
        val again = repo.set(1, sep, Money(45000, USD))

        assertEquals(id, again)
        val budget = repo.get(1, sep)!!
        assertEquals(id, budget.id)
        assertEquals(Money(45000, USD), budget.amount)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `the DAO rejects a duplicate (category, month) insert`() = runTest {
        dao.insert(BudgetEntity(categoryId = 1, month = sep, amountMinor = 1, currencyCode = "EUR"))
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.insert(BudgetEntity(categoryId = 1, month = sep, amountMinor = 2, currencyCode = "EUR"))
            }
        }
    }

    @Test
    fun `remove deletes and observe emits null`() = runTest {
        repo.set(1, sep, Money(40000, EUR))
        repo.remove(1, sep)
        assertNull(repo.observe(1, sep).first())
    }

    @Test
    fun `copy carries budgets forward without overwriting existing ones`() = runTest {
        repo.set(1, aug, Money(40000, EUR))
        repo.set(2, aug, Money(120000, EUR))
        repo.set(3, aug, Money(5000, USD))
        repo.set(2, sep, Money(130000, EUR)) // already set for September

        val created = repo.copy(from = aug, to = sep)

        assertEquals(2, created)
        val september = repo.observeForMonth(sep).first().associateBy { it.categoryId }
        assertEquals(Money(40000, EUR), september.getValue(1).amount)
        assertEquals(Money(130000, EUR), september.getValue(2).amount)
        assertEquals(Money(5000, USD), september.getValue(3).amount)
        assertEquals(3, repo.observeForMonth(aug).first().size) // source untouched
        assertEquals(0, repo.copy(from = sep, to = sep))
    }
}

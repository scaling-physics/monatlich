package com.monatlich.data.repository

import com.monatlich.data.local.BudgetDao
import com.monatlich.data.local.BudgetEntity
import com.monatlich.domain.model.Budget
import com.monatlich.domain.model.Money
import com.monatlich.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
) : BudgetRepository {

    override fun observeForMonth(month: YearMonth): Flow<List<Budget>> =
        dao.observeForMonth(month).map { list -> list.map { it.toDomain() } }

    override fun observe(categoryId: Long, month: YearMonth): Flow<Budget?> =
        dao.observe(categoryId, month).map { it?.toDomain() }

    override suspend fun get(categoryId: Long, month: YearMonth): Budget? =
        dao.get(categoryId, month)?.toDomain()

    override suspend fun set(categoryId: Long, month: YearMonth, amount: Money): Long =
        dao.upsert(
            BudgetEntity(
                categoryId = categoryId,
                month = month,
                amountMinor = amount.amountMinor,
                currencyCode = amount.currency.code,
            ),
        )

    override suspend fun remove(categoryId: Long, month: YearMonth) = dao.delete(categoryId, month)

    override suspend fun copy(from: YearMonth, to: YearMonth): Int {
        if (from == to) return 0
        val existing = dao.getForMonth(to).map { it.categoryId }.toSet()
        return dao.getForMonth(from)
            .filter { it.categoryId !in existing }
            .onEach { dao.insert(it.copy(id = 0L, month = to)) }
            .size
    }
}

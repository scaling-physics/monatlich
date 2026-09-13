package com.monatlich.data.repository

import com.monatlich.data.local.TransactionDao
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {

    override fun observeForMonth(month: YearMonth): Flow<List<Transaction>> =
        dao.observeForMonth(month).map { list -> list.map { it.toDomain() } }

    override fun observeForCategory(categoryId: Long, month: YearMonth): Flow<List<Transaction>> =
        dao.observeForCategoryAndMonth(categoryId, month).map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Transaction?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun get(id: Long): Transaction? = dao.getById(id)?.toDomain()

    override suspend fun add(transaction: Transaction): Long =
        dao.insert(transaction.copy(id = 0L).toEntity())

    override suspend fun update(transaction: Transaction) = dao.update(transaction.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * Each DAO row is a (category, currency, rateToBase) group; the group sum is converted with its
     * own rate and rounded half-even to the base minor unit, then groups are summed per category.
     */
    override fun observeTotalsByCategoryInBase(
        month: YearMonth,
        type: TransactionType,
        base: Currency,
    ): Flow<Map<Long, Money>> =
        dao.observeTotalsByCategory(month, type).map { rows ->
            val totals = LinkedHashMap<Long, Money>()
            rows.forEach { row ->
                val currency = Currency.fromCode(row.currencyCode)
                val amount = Money(row.totalMinor, currency)
                val inBase = if (currency == base) amount else amount.convertTo(base, row.rateToBase)
                totals[row.categoryId] = totals[row.categoryId]?.plus(inBase) ?: inBase
            }
            totals
        }
}

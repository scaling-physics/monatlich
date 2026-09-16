package com.monatlich.domain.usecase

import com.monatlich.domain.model.Budget
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.ExchangeRate
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.repository.BudgetRepository
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.ExchangeRateRepository
import com.monatlich.domain.repository.RecurringRepository
import com.monatlich.domain.repository.SecurityRepository
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

/** In-memory repositories backed by `MutableStateFlow`, so tests can push changes and observe. */

class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<Category>> = state.map { it.sortedWith(compareBy({ c -> c.sortOrder }, { c -> c.id })) }
    override fun observeActive(): Flow<List<Category>> = observeAll().map { list -> list.filter { !it.archived } }
    override fun observe(id: Long): Flow<Category?> = state.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun get(id: Long): Category? = state.value.firstOrNull { it.id == id }

    override suspend fun add(category: Category): Long {
        val id = nextId++
        state.update { it + category.copy(id = id, sortOrder = it.size) }
        return id
    }

    override suspend fun update(category: Category) =
        state.update { list -> list.map { if (it.id == category.id) category else it } }

    override suspend fun reorder(orderedIds: List<Long>) = state.update { list ->
        list.map { c -> orderedIds.indexOf(c.id).takeIf { it >= 0 }?.let { c.copy(sortOrder = it) } ?: c }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) =
        state.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
}

class FakeBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeForMonth(month: YearMonth): Flow<List<Budget>> = state.map { list -> list.filter { it.month == month } }
    override fun observe(categoryId: Long, month: YearMonth): Flow<Budget?> = state.map { list -> list.firstOrNull { it.categoryId == categoryId && it.month == month } }
    override suspend fun get(categoryId: Long, month: YearMonth): Budget? = state.value.firstOrNull { it.categoryId == categoryId && it.month == month }

    override suspend fun set(categoryId: Long, month: YearMonth, amount: Money): Long {
        val existing = get(categoryId, month)
        val id = existing?.id ?: nextId++
        state.update { list -> list.filterNot { it.id == id } + Budget(id, categoryId, month, amount) }
        return id
    }

    override suspend fun remove(categoryId: Long, month: YearMonth) =
        state.update { list -> list.filterNot { it.categoryId == categoryId && it.month == month } }

    override suspend fun copy(from: YearMonth, to: YearMonth): Int {
        val existing = state.value.filter { it.month == to }.map { it.categoryId }.toSet()
        val copies = state.value.filter { it.month == from && it.categoryId !in existing }
        copies.forEach { set(it.categoryId, to, it.amount) }
        return copies.size
    }
}

class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeForMonth(month: YearMonth): Flow<List<Transaction>> =
        state.map { list -> list.filter { it.month == month }.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id }) }

    override suspend fun getAll(): List<Transaction> =
        state.value.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })

    override fun observeForCategory(categoryId: Long, month: YearMonth): Flow<List<Transaction>> =
        observeForMonth(month).map { list -> list.filter { it.categoryId == categoryId } }

    override fun observe(id: Long): Flow<Transaction?> = state.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun get(id: Long): Transaction? = state.value.firstOrNull { it.id == id }

    override suspend fun add(transaction: Transaction): Long {
        val id = nextId++
        state.update { it + transaction.copy(id = id) }
        return id
    }

    override suspend fun update(transaction: Transaction) =
        state.update { list -> list.map { if (it.id == transaction.id) transaction else it } }

    override suspend fun delete(id: Long) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun getForRecurring(recurringId: Long, month: YearMonth): List<Transaction> =
        state.value.filter { it.recurringId == recurringId && it.month == month }.sortedWith(compareBy({ it.date }, { it.id }))

    /** Mirrors the real implementation: group by (category, currency, rate), convert each group, sum. */
    override fun observeTotalsByCategoryInBase(month: YearMonth, type: TransactionType, base: Currency): Flow<Map<Long, Money>> =
        state.map { list ->
            list.filter { it.month == month && it.type == type }
                .groupBy { Triple(it.categoryId, it.amount.currency, it.rateToBase) }
                .map { (key, group) ->
                    val (categoryId, currency, rate) = key
                    val sum = Money(group.sumOf { it.amount.amountMinor }, currency)
                    categoryId to (if (currency == base) sum else sum.convertTo(base, rate))
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, amounts) -> amounts.reduce { a, b -> a + b } }
        }
}

class FakeExchangeRateRepository(initial: Map<Currency, BigDecimal> = emptyMap()) : ExchangeRateRepository {
    val state = MutableStateFlow(initial.map { (c, r) -> ExchangeRate(c, r, Instant.EPOCH) })

    override fun observeAll(): Flow<List<ExchangeRate>> = state.map { list -> list.sortedBy { it.currency.code } }
    override fun observe(currency: Currency): Flow<ExchangeRate?> = state.map { list -> list.firstOrNull { it.currency == currency } }
    override suspend fun get(currency: Currency): ExchangeRate? = state.value.firstOrNull { it.currency == currency }

    override suspend fun set(currency: Currency, rateToBase: BigDecimal) =
        state.update { list -> list.filterNot { it.currency == currency } + ExchangeRate(currency, rateToBase, Instant.EPOCH) }

    override suspend fun currentRateToBase(currency: Currency, base: Currency): BigDecimal? =
        if (currency == base) BigDecimal.ONE else get(currency)?.rateToBase
}

class FakeRecurringRepository(initial: List<RecurringTransaction> = emptyList()) : RecurringRepository {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<RecurringTransaction>> =
        state.map { list -> list.sortedWith(compareBy({ it.dayOfMonth }, { it.id })) }

    override fun observe(id: Long): Flow<RecurringTransaction?> = state.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun get(id: Long): RecurringTransaction? = state.value.firstOrNull { it.id == id }

    override suspend fun add(rule: RecurringTransaction): Long {
        val id = nextId++
        state.update { it + rule.copy(id = id) }
        return id
    }

    override suspend fun update(rule: RecurringTransaction) =
        state.update { list -> list.map { if (it.id == rule.id) rule else it } }

    override suspend fun setActive(id: Long, active: Boolean) =
        state.update { list -> list.map { if (it.id == id) it.copy(active = active) else it } }

    override suspend fun delete(id: Long) = state.update { list -> list.filterNot { it.id == id } }
}

class FakeSettingsRepository(initial: Currency = Currency.EUR) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val baseCurrency: Flow<Currency> = state
    override suspend fun setBaseCurrency(currency: Currency) { state.value = currency }
}

class FakeSecurityRepository(
    private var storedPin: String? = null,
    lockEnabled: Boolean = false,
    biometricEnabled: Boolean = false,
) : SecurityRepository {
    private val lockEnabledState = MutableStateFlow(lockEnabled)
    private val biometricEnabledState = MutableStateFlow(biometricEnabled)
    private val hasPinState = MutableStateFlow(storedPin != null)

    override val isLockEnabled: Flow<Boolean> = lockEnabledState
    override val isBiometricEnabled: Flow<Boolean> = biometricEnabledState
    override val hasPin: Flow<Boolean> = hasPinState

    override suspend fun setPin(pin: String) {
        storedPin = pin
        hasPinState.value = true
    }

    override suspend fun verifyPin(pin: String): Boolean = storedPin != null && storedPin == pin

    override suspend fun clearPin() {
        storedPin = null
        hasPinState.value = false
        lockEnabledState.value = false
        biometricEnabledState.value = false
    }

    override suspend fun setLockEnabled(enabled: Boolean) { lockEnabledState.value = enabled }
    override suspend fun setBiometricEnabled(enabled: Boolean) { biometricEnabledState.value = enabled }
}

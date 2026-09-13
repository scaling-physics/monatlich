package com.monatlich.ui.budget

import com.monatlich.domain.model.Budget
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.repository.BudgetRepository
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.YearMonth

/** Swaps `Dispatchers.Main` for a test dispatcher so `viewModelScope` runs inside `runTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/**
 * In-memory repositories for the budget ViewModels. Same shape as the fakes under
 * `domain/usecase`, kept separate so this package owns its own test doubles.
 */
class BudgetFakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<Category>> = state.map { it.sortedBy { c -> c.sortOrder } }
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

class BudgetFakeBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    /** Every `set` / `remove` / `copy` call, in order, so tests can assert on repository traffic. */
    val calls = mutableListOf<String>()

    override fun observeForMonth(month: YearMonth): Flow<List<Budget>> =
        state.map { list -> list.filter { it.month == month } }

    override fun observe(categoryId: Long, month: YearMonth): Flow<Budget?> =
        state.map { list -> list.firstOrNull { it.categoryId == categoryId && it.month == month } }

    override suspend fun get(categoryId: Long, month: YearMonth): Budget? =
        state.value.firstOrNull { it.categoryId == categoryId && it.month == month }

    override suspend fun set(categoryId: Long, month: YearMonth, amount: Money): Long {
        calls += "set($categoryId, $month, $amount)"
        val id = get(categoryId, month)?.id ?: nextId++
        state.update { list -> list.filterNot { it.id == id } + Budget(id, categoryId, month, amount) }
        return id
    }

    override suspend fun remove(categoryId: Long, month: YearMonth) {
        calls += "remove($categoryId, $month)"
        state.update { list -> list.filterNot { it.categoryId == categoryId && it.month == month } }
    }

    override suspend fun copy(from: YearMonth, to: YearMonth): Int {
        calls += "copy($from, $to)"
        val existing = state.value.filter { it.month == to }.map { it.categoryId }.toSet()
        val copies = state.value.filter { it.month == from && it.categoryId !in existing }
        copies.forEach { set(it.categoryId, to, it.amount) }
        return copies.size
    }
}

class BudgetFakeSettingsRepository(initial: Currency = Currency.EUR) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val baseCurrency: Flow<Currency> = state
    override suspend fun setBaseCurrency(currency: Currency) {
        state.value = currency
    }
}

fun category(id: Long, name: String = "Category $id") =
    Category(id = id, name = name, icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = id.toInt())

fun budget(id: Long, categoryId: Long, month: YearMonth, minor: Long, currency: Currency = Currency.EUR) =
    Budget(id = id, categoryId = categoryId, month = month, amount = Money(minor, currency))

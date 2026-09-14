package com.monatlich.ui.transactions

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.usecase.FakeCategoryRepository
import com.monatlich.domain.usecase.FakeTransactionRepository
import com.monatlich.ui.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC)
    private val september = YearMonth.of(2026, 9)

    private val groceries = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = 0)
    private val rent = Category(id = 2, name = "Rent", icon = "Home", color = 0xFF6B1F2A, sortOrder = 1)

    private val categories = FakeCategoryRepository(listOf(groceries, rent))
    private val transactions = FakeTransactionRepository()

    private fun expense(categoryId: Long, date: LocalDate, minor: Long, note: String? = null) = Transaction(
        categoryId = categoryId,
        date = date,
        amount = Money(minor, Currency.EUR),
        rateToBase = BigDecimal.ONE,
        type = TransactionType.EXPENSE,
        note = note,
    )

    private fun viewModel(scope: CoroutineScope) = TransactionsViewModel(transactions, categories, clock, scope)

    @Test
    fun `lists this month's transactions newest first`() = runTest {
        transactions.add(expense(groceries.id, LocalDate.of(2026, 9, 1), 1_000))
        transactions.add(expense(groceries.id, LocalDate.of(2026, 9, 10), 2_000))
        transactions.add(expense(rent.id, LocalDate.of(2026, 8, 30), 120_000))

        val vm = viewModel(this)
        vm.uiState.test {
            val state = awaitItemMatching { !it.isLoading }
            assertEquals(2, state.rows.size)
            assertEquals(LocalDate.of(2026, 9, 10), state.rows.first().date)
        }
    }

    @Test
    fun `switching month reloads the list`() = runTest {
        transactions.add(expense(groceries.id, LocalDate.of(2026, 8, 15), 500))
        val vm = viewModel(this)
        vm.uiState.test {
            assertTrue(awaitItemMatching { !it.isLoading }.rows.isEmpty())

            vm.onEvent(TransactionsEvent.PreviousMonth)
            val august = awaitItemMatching { it.month == YearMonth.of(2026, 8) && it.rows.isNotEmpty() }
            assertEquals(1, august.rows.size)

            vm.onEvent(TransactionsEvent.JumpToCurrentMonth)
            assertEquals(september, awaitItemMatching { it.month == september }.month)
        }
    }

    @Test
    fun `search filters by category name or note`() = runTest {
        transactions.add(expense(groceries.id, LocalDate.of(2026, 9, 1), 1_000, note = "Weekly shop"))
        transactions.add(expense(rent.id, LocalDate.of(2026, 9, 1), 120_000, note = "Flat"))
        val vm = viewModel(this)

        vm.uiState.test {
            awaitItemMatching { it.rows.size == 2 }

            vm.onEvent(TransactionsEvent.QueryChanged("rent"))
            assertEquals(listOf(rent.id), awaitItemMatching { it.query == "rent" }.rows.map { it.categoryId })

            vm.onEvent(TransactionsEvent.QueryChanged("weekly"))
            assertEquals(listOf(groceries.id), awaitItemMatching { it.query == "weekly" }.rows.map { it.categoryId })

            vm.onEvent(TransactionsEvent.QueryChanged(""))
            assertEquals(2, awaitItemMatching { it.query == "" }.rows.size)
        }
    }

    @Test
    fun `type and category filters narrow the list`() = runTest {
        transactions.add(expense(groceries.id, LocalDate.of(2026, 9, 1), 1_000))
        transactions.add(
            Transaction(
                categoryId = rent.id,
                date = LocalDate.of(2026, 9, 2),
                amount = Money(300_000, Currency.EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.INCOME,
            ),
        )
        val vm = viewModel(this)

        vm.uiState.test {
            awaitItemMatching { it.rows.size == 2 }

            vm.onEvent(TransactionsEvent.TypeFilterSelected(TransactionType.INCOME))
            val incomeOnly = awaitItemMatching { it.typeFilter == TransactionType.INCOME }
            assertEquals(listOf(rent.id), incomeOnly.rows.map { it.categoryId })

            vm.onEvent(TransactionsEvent.TypeFilterSelected(null))
            awaitItemMatching { it.typeFilter == null && it.rows.size == 2 }
            vm.onEvent(TransactionsEvent.CategoryFilterSelected(groceries.id))
            val groceriesOnly = awaitItemMatching { it.categoryFilter == groceries.id }
            assertEquals(listOf(groceries.id), groceriesOnly.rows.map { it.categoryId })
        }
    }

    @Test
    fun `delete hides the row until confirmed, undo restores it`() = runTest {
        val id = transactions.add(expense(groceries.id, LocalDate.of(2026, 9, 1), 1_000))
        val vm = viewModel(this)

        vm.uiState.test {
            awaitItemMatching { it.rows.size == 1 }

            vm.onEvent(TransactionsEvent.Delete(id))
            val deleted = awaitItemMatching { it.pendingDeletion != null }
            assertTrue(deleted.rows.isEmpty())
            assertEquals(id, deleted.pendingDeletion?.id)
            assertNotNull(transactions.get(id))

            vm.onEvent(TransactionsEvent.UndoDelete)
            val restored = awaitItemMatching { it.pendingDeletion == null && it.rows.isNotEmpty() }
            assertEquals(1, restored.rows.size)
        }
    }

    @Test
    fun `delete confirmed commits the removal`() = runTest {
        val id = transactions.add(expense(groceries.id, LocalDate.of(2026, 9, 1), 1_000))
        val vm = viewModel(this)

        vm.uiState.test {
            awaitItemMatching { it.rows.size == 1 }
            vm.onEvent(TransactionsEvent.Delete(id))
            awaitItemMatching { it.pendingDeletion != null }
            vm.onEvent(TransactionsEvent.DeleteConfirmed)
            awaitItemMatching { it.pendingDeletion == null }
            // The repository delete this triggers reaches `monthData` asynchronously and may
            // still land one more (empty-rows) emission; it carries nothing new to assert on.
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(transactions.get(id))
    }

    @Test
    fun `row click opens the editor for that transaction`() = runTest {
        val id = transactions.add(expense(groceries.id, LocalDate.of(2026, 9, 1), 1_000))
        val vm = viewModel(this)

        vm.uiState.test {
            awaitItemMatching { it.rows.size == 1 }

            vm.onEvent(TransactionsEvent.RowClicked(id))
            assertEquals(id, awaitItemMatching { it.editingTransactionId != null }.editingTransactionId)

            vm.onEvent(TransactionsEvent.EditorDismissed)
            assertNull(awaitItemMatching { it.editingTransactionId == null }.editingTransactionId)
        }
    }

    @Test
    fun `add clicked shows the add sheet`() = runTest {
        val vm = viewModel(this)
        vm.uiState.test {
            awaitItemMatching { !it.isLoading }
            vm.onEvent(TransactionsEvent.AddClicked)
            assertTrue(awaitItemMatching { it.isAddSheetVisible }.isAddSheetVisible)
            vm.onEvent(TransactionsEvent.AddSheetDismissed)
            assertFalse(awaitItemMatching { !it.isAddSheetVisible }.isAddSheetVisible)
        }
    }

    /** Skips intermediate emissions (month/filter changes can land as more than one item). */
    private suspend fun ReceiveTurbine<TransactionsUiState>.awaitItemMatching(
        predicate: (TransactionsUiState) -> Boolean,
    ): TransactionsUiState {
        repeat(10) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching state within 10 emissions")
    }
}

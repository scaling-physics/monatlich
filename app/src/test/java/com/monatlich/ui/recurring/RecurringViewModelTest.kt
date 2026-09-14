package com.monatlich.ui.recurring

import app.cash.turbine.test
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.usecase.ApplyRecurringTransactions
import com.monatlich.domain.usecase.FakeCategoryRepository
import com.monatlich.domain.usecase.FakeExchangeRateRepository
import com.monatlich.domain.usecase.FakeRecurringRepository
import com.monatlich.domain.usecase.FakeSettingsRepository
import com.monatlich.domain.usecase.FakeTransactionRepository
import com.monatlich.ui.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
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

class RecurringViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC)
    private val september = YearMonth.of(2026, 9)

    private val groceries = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = 0)
    private val rent = Category(id = 2, name = "Rent", icon = "Home", color = 0xFF6B1F2A, sortOrder = 1)
    private val old = Category(id = 3, name = "Old", icon = "MoreHoriz", color = 0xFF546E7A, sortOrder = 2, archived = true)

    private val categories = FakeCategoryRepository(listOf(groceries, rent, old))
    private val recurring = FakeRecurringRepository()
    private val transactions = FakeTransactionRepository()
    private val rates = FakeExchangeRateRepository(mapOf(Currency.USD to BigDecimal("0.92")))
    private val settings = FakeSettingsRepository(Currency.EUR)

    private fun viewModel(scope: CoroutineScope) = RecurringViewModel(
        recurring = recurring,
        categories = categories,
        settings = settings,
        applyRecurring = ApplyRecurringTransactions(recurring, transactions, rates, settings, clock),
        clock = clock,
        applicationScope = scope,
    )

    private fun rentRule(id: Long = 0L, active: Boolean = true) = RecurringTransaction(
        id = id,
        categoryId = rent.id,
        amount = Money(120_000, Currency.EUR),
        type = TransactionType.EXPENSE,
        note = "Flat",
        dayOfMonth = 1,
        startMonth = YearMonth.of(2026, 8),
        active = active,
    )

    @Test
    fun listsRulesJoinedWithCategoriesAndOffersActiveCategoriesOnly() = runTest {
        val id = recurring.add(rentRule())
        val vm = viewModel(this)

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            val row = state.rules.single()
            assertEquals(id, row.id)
            assertEquals("Rent", row.categoryName)
            assertEquals("Home", row.categoryIcon)
            assertEquals(120_000L, row.amountMinor)
            assertEquals("EUR", row.currencyCode)
            assertEquals(1, row.dayOfMonth)
            assertEquals(listOf(groceries, rent), state.categories)
        }
    }

    @Test
    fun newRuleFlowOpensEditorWithDefaultsAndSavesThenGeneratesForCurrentMonth() = runTest {
        val vm = viewModel(this)

        vm.onEvent(RecurringEvent.NewRuleClicked)
        val editor = vm.uiState.value.editor
        assertNotNull(editor)
        editor!!
        assertTrue(editor.isNew)
        assertEquals(Currency.EUR, editor.currency)
        assertEquals(september, editor.startMonth)
        assertEquals(groceries.id, editor.categoryId)
        assertEquals(1, editor.dayOfMonth)
        assertFalse(editor.canSave)

        vm.onEvent(RecurringEvent.AmountEdited("12.99"))
        vm.onEvent(RecurringEvent.CurrencySelected(Currency.USD))
        vm.onEvent(RecurringEvent.CategorySelected(rent.id))
        vm.onEvent(RecurringEvent.TypeSelected(TransactionType.INCOME))
        vm.onEvent(RecurringEvent.DaySelected(15))
        vm.onEvent(RecurringEvent.NoteChanged("  Streaming "))
        vm.onEvent(RecurringEvent.EndMonthChanged(YearMonth.of(2027, 8)))
        assertTrue(vm.uiState.value.editor!!.canSave)

        vm.onEvent(RecurringEvent.SaveClicked)
        advanceUntilIdle()

        assertNull(vm.uiState.value.editor)
        val saved = recurring.state.value.single()
        assertEquals(Money(1_299, Currency.USD), saved.amount)
        assertEquals(rent.id, saved.categoryId)
        assertEquals(TransactionType.INCOME, saved.type)
        assertEquals(15, saved.dayOfMonth)
        assertEquals("Streaming", saved.note)
        assertEquals(september, saved.startMonth)
        assertEquals(YearMonth.of(2027, 8), saved.endMonth)
        assertTrue(saved.active)

        val generated = transactions.state.value.single()
        assertEquals(saved.id, generated.recurringId)
        assertEquals(LocalDate.of(2026, 9, 15), generated.date)
        assertEquals(BigDecimal("0.92"), generated.rateToBase)
    }

    @Test
    fun editFlowPrefillsAndUpdatesInPlace() = runTest {
        val id = recurring.add(rentRule())
        val vm = viewModel(this)

        vm.onEvent(RecurringEvent.RuleClicked(id))
        val editor = vm.uiState.value.editor!!
        assertEquals(id, editor.id)
        assertEquals("1200", editor.amountText)
        assertEquals(120_000L, editor.amountMinor)
        assertEquals(rent.id, editor.categoryId)
        assertEquals("Flat", editor.note)
        assertEquals(YearMonth.of(2026, 8), editor.startMonth)

        vm.onEvent(RecurringEvent.AmountCleared)
        vm.onEvent(RecurringEvent.AmountEdited("1250"))
        vm.onEvent(RecurringEvent.DaySelected(3))
        vm.onEvent(RecurringEvent.SaveClicked)
        advanceUntilIdle()

        val updated = recurring.state.value.single()
        assertEquals(id, updated.id)
        assertEquals(Money(125_000, Currency.EUR), updated.amount)
        assertEquals(3, updated.dayOfMonth)
        assertNull(vm.uiState.value.editor)
        // Edit generated September's entry (rule active, covers September).
        assertEquals(1, transactions.state.value.count { it.recurringId == id })
    }

    @Test
    fun startMonthMovingPastEndMonthClearsEnd() = runTest {
        val vm = viewModel(this)
        vm.onEvent(RecurringEvent.NewRuleClicked)
        vm.onEvent(RecurringEvent.EndMonthChanged(YearMonth.of(2026, 10)))
        vm.onEvent(RecurringEvent.StartMonthChanged(YearMonth.of(2026, 11)))

        val editor = vm.uiState.value.editor!!
        assertEquals(YearMonth.of(2026, 11), editor.startMonth)
        assertNull(editor.endMonth)

        vm.onEvent(RecurringEvent.EndMonthChanged(YearMonth.of(2026, 1)))
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.editor!!.endMonth)
    }

    @Test
    fun toggleActivePersistsAndReactivationGeneratesCurrentMonth() = runTest {
        val id = recurring.add(rentRule(active = false))
        val vm = viewModel(this)

        vm.onEvent(RecurringEvent.ActiveToggled(id, true))
        advanceUntilIdle()
        assertTrue(recurring.state.value.single().active)
        assertTrue(vm.uiState.value.rules.single().active)
        assertEquals(1, transactions.state.value.count { it.recurringId == id })

        vm.onEvent(RecurringEvent.ActiveToggled(id, false))
        advanceUntilIdle()
        assertFalse(recurring.state.value.single().active)
        assertFalse(vm.uiState.value.rules.single().active)
    }

    @Test
    fun deleteHidesRowUntilConfirmedAndUndoRestoresIt() = runTest {
        val id = recurring.add(rentRule())
        val vm = viewModel(this)

        vm.onEvent(RecurringEvent.Delete(id))
        assertTrue(vm.uiState.value.rules.isEmpty())
        assertEquals(id, vm.uiState.value.pendingDeletion?.id)
        assertEquals(1, recurring.state.value.size)

        vm.onEvent(RecurringEvent.UndoDelete)
        assertNull(vm.uiState.value.pendingDeletion)
        assertEquals(listOf(id), vm.uiState.value.rules.map { it.id })
        assertEquals(1, recurring.state.value.size)
    }

    @Test
    fun deleteConfirmedRemovesRuleFromRepository() = runTest {
        val id = recurring.add(rentRule())
        val vm = viewModel(this)

        vm.onEvent(RecurringEvent.Delete(id))
        vm.onEvent(RecurringEvent.DeleteConfirmed)
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingDeletion)
        assertTrue(recurring.state.value.isEmpty())
        assertTrue(vm.uiState.value.rules.isEmpty())
    }

    @Test
    fun secondSwipeCommitsTheFirstPendingDeletion() = runTest {
        val first = recurring.add(rentRule())
        val second = recurring.add(rentRule().copy(dayOfMonth = 2))
        val vm = viewModel(this)

        vm.onEvent(RecurringEvent.Delete(first))
        vm.onEvent(RecurringEvent.Delete(second))
        advanceUntilIdle()

        assertEquals(listOf(second), recurring.state.value.map { it.id })
        assertEquals(second, vm.uiState.value.pendingDeletion?.id)
        assertTrue(vm.uiState.value.rules.isEmpty())
    }
}

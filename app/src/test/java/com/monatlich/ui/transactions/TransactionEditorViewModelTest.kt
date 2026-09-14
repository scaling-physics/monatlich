package com.monatlich.ui.transactions

import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.usecase.FakeCategoryRepository
import com.monatlich.domain.usecase.FakeExchangeRateRepository
import com.monatlich.domain.usecase.FakeSettingsRepository
import com.monatlich.domain.usecase.FakeTransactionRepository
import com.monatlich.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class TransactionEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC)
    private val september = YearMonth.of(2026, 9)

    private val groceries = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = 0)
    private val rent = Category(id = 2, name = "Rent", icon = "Home", color = 0xFF6B1F2A, sortOrder = 1)

    private val categories = FakeCategoryRepository(listOf(groceries, rent))
    private val transactions = FakeTransactionRepository()
    private val rates = FakeExchangeRateRepository(mapOf(Currency.USD to BigDecimal("0.92")))
    private val settings = FakeSettingsRepository(Currency.EUR)

    private fun viewModel() = TransactionEditorViewModel(transactions, categories, settings, rates, clock)

    @Test
    fun `loadNew in the current month defaults to today, base currency and first category`() = runTest {
        val vm = viewModel()
        vm.loadNew(september)
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.isNew)
        assertEquals(LocalDate.of(2026, 9, 13), state.date)
        assertEquals(Currency.EUR, state.currency)
        assertEquals(groceries.id, state.categoryId)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertFalse(state.canSave)
    }

    @Test
    fun `loadNew in a past month defaults to the first of that month`() = runTest {
        val vm = viewModel()
        vm.loadNew(YearMonth.of(2026, 7))
        assertEquals(LocalDate.of(2026, 7, 1), vm.uiState.value.date)
    }

    @Test
    fun `save creates an expense with the current rate to base`() = runTest {
        val vm = viewModel()
        vm.loadNew(september)
        vm.onEvent(TransactionEditorEvent.AmountEdited("50"))
        vm.onEvent(TransactionEditorEvent.CurrencySelected(Currency.USD))
        vm.onEvent(TransactionEditorEvent.NoteChanged("Snacks"))

        assertTrue(vm.uiState.value.canSave)
        vm.onEvent(TransactionEditorEvent.Save)

        assertTrue(vm.uiState.value.isDone)
        val saved = transactions.state.value.single()
        assertEquals(groceries.id, saved.categoryId)
        assertEquals(Money(5_000, Currency.USD), saved.amount)
        assertEquals(BigDecimal("0.92"), saved.rateToBase)
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals("Snacks", saved.note)
        assertEquals(LocalDate.of(2026, 9, 13), saved.date)
    }

    @Test
    fun `save with no category or zero amount does nothing`() = runTest {
        val vm = viewModel()
        vm.loadNew(september)
        vm.onEvent(TransactionEditorEvent.Save)
        assertFalse(vm.uiState.value.isDone)
        assertTrue(transactions.state.value.isEmpty())
    }

    @Test
    fun `loadExisting prefills amount, category, type, date and note`() = runTest {
        val id = transactions.add(
            Transaction(
                categoryId = rent.id,
                date = LocalDate.of(2026, 9, 5),
                amount = Money(120_000, Currency.EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.EXPENSE,
                note = "Flat",
            ),
        )
        val vm = viewModel()
        vm.loadExisting(id)
        val state = vm.uiState.value

        assertFalse(state.isNew)
        assertEquals(rent.id, state.categoryId)
        assertEquals("1200", state.amountText)
        assertEquals(Currency.EUR, state.currency)
        assertEquals(LocalDate.of(2026, 9, 5), state.date)
        assertEquals("Flat", state.note)
    }

    @Test
    fun `save on an existing transaction updates it in place`() = runTest {
        val id = transactions.add(
            Transaction(
                categoryId = rent.id,
                date = LocalDate.of(2026, 9, 5),
                amount = Money(120_000, Currency.EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.EXPENSE,
            ),
        )
        val vm = viewModel()
        vm.loadExisting(id)
        vm.onEvent(TransactionEditorEvent.AmountEdited("1250"))
        vm.onEvent(TransactionEditorEvent.Save)

        assertEquals(1, transactions.state.value.size)
        val updated = transactions.state.value.single()
        assertEquals(id, updated.id)
        assertEquals(125_000L, updated.amount.amountMinor)
    }

    @Test
    fun `delete removes an existing transaction`() = runTest {
        val id = transactions.add(
            Transaction(
                categoryId = rent.id,
                date = LocalDate.of(2026, 9, 5),
                amount = Money(120_000, Currency.EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.EXPENSE,
            ),
        )
        val vm = viewModel()
        vm.loadExisting(id)
        vm.onEvent(TransactionEditorEvent.Delete)

        assertTrue(vm.uiState.value.isDone)
        assertNull(transactions.get(id))
    }

    @Test
    fun `type can be switched to income`() = runTest {
        val vm = viewModel()
        vm.loadNew(september)
        vm.onEvent(TransactionEditorEvent.TypeSelected(TransactionType.INCOME))
        vm.onEvent(TransactionEditorEvent.AmountEdited("3000"))
        vm.onEvent(TransactionEditorEvent.Save)

        assertEquals(TransactionType.INCOME, transactions.state.value.single().type)
    }
}

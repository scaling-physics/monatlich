package com.monatlich.ui.budget

import app.cash.turbine.test
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class SetBudgetViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val september = YearMonth.of(2026, 9)
    private val groceries = category(1, "Groceries")
    private val rent = category(2, "Rent")

    private val categories = BudgetFakeCategoryRepository(listOf(groceries, rent))
    private val budgets = BudgetFakeBudgetRepository(
        listOf(budget(id = 10, categoryId = 2, month = september, minor = 120_000, currency = Currency.USD)),
    )
    private val settings = BudgetFakeSettingsRepository(Currency.EUR)

    private fun viewModel() = SetBudgetViewModel(categories, budgets, settings)

    @Test
    fun `load without existing budget shows category, base currency and empty amount`() = runTest {
        val vm = viewModel()
        vm.load(groceries.id, september)
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertEquals("Groceries", state.categoryName)
        assertEquals("ShoppingCart", state.categoryIcon)
        assertEquals(0xFF2E7D32, state.categoryColor)
        assertNull(state.existingBudget)
        assertFalse(state.hasExistingBudget)
        assertEquals("", state.amountText)
        assertEquals(0L, state.amountMinor)
        assertEquals(Currency.EUR, state.currency)
        assertFalse(state.canSave)
    }

    @Test
    fun `load with existing budget prefills amount and its currency`() = runTest {
        val vm = viewModel()
        vm.load(rent.id, september)
        val state = vm.uiState.value

        assertEquals("Rent", state.categoryName)
        assertEquals(Money(120_000, Currency.USD), state.existingBudget)
        assertTrue(state.hasExistingBudget)
        assertEquals("1200", state.amountText)
        assertEquals(120_000L, state.amountMinor)
        assertEquals(Currency.USD, state.currency)
        assertTrue(state.canSave)
    }

    @Test
    fun `load emits a loading state first`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().isLoading)
            vm.load(groceries.id, september)
            // With an unconfined dispatcher the load finishes synchronously; the last emission
            // must be the loaded state for this key.
            val loaded = expectMostRecentItem()
            assertFalse(loaded.isLoading)
            assertEquals(groceries.id, loaded.categoryId)
            assertEquals(september, loaded.month)
        }
    }

    @Test
    fun `typing edits the amount and save creates the budget in the base currency`() = runTest {
        val vm = viewModel()
        vm.load(groceries.id, september)

        vm.onEvent(SetBudgetEvent.AmountEdited("4"))
        vm.onEvent(SetBudgetEvent.AmountEdited("45"))
        vm.onEvent(SetBudgetEvent.AmountEdited("450"))
        vm.onEvent(SetBudgetEvent.AmountEdited("450."))
        vm.onEvent(SetBudgetEvent.AmountEdited("450.5"))
        assertEquals("450.5", vm.uiState.value.amountText)
        assertEquals(45_050L, vm.uiState.value.amountMinor)
        assertTrue(vm.uiState.value.canSave)

        vm.onEvent(SetBudgetEvent.Save)

        assertTrue(vm.uiState.value.isDone)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(listOf("set(1, 2026-09, 450.50 EUR)"), budgets.calls)
        assertEquals(Money(45_050, Currency.EUR), budgets.get(groceries.id, september)?.amount)
    }

    @Test
    fun `save replaces an existing budget`() = runTest {
        val vm = viewModel()
        vm.load(rent.id, september)

        vm.onEvent(SetBudgetEvent.AmountCleared)
        assertEquals("", vm.uiState.value.amountText)
        assertFalse(vm.uiState.value.canSave)

        vm.onEvent(SetBudgetEvent.AmountEdited("1300"))
        vm.onEvent(SetBudgetEvent.Save)

        assertTrue(vm.uiState.value.isDone)
        val stored = budgets.get(rent.id, september)
        assertEquals(Money(130_000, Currency.USD), stored?.amount)
        assertEquals(10L, stored?.id)
        assertEquals(1, budgets.state.value.size)
    }

    @Test
    fun `save with a zero amount does nothing`() = runTest {
        val vm = viewModel()
        vm.load(groceries.id, september)
        vm.onEvent(SetBudgetEvent.Save)
        assertFalse(vm.uiState.value.isDone)
        assertTrue(budgets.calls.isEmpty())
    }

    @Test
    fun `remove deletes the existing budget`() = runTest {
        val vm = viewModel()
        vm.load(rent.id, september)
        vm.onEvent(SetBudgetEvent.Remove)

        assertTrue(vm.uiState.value.isDone)
        assertEquals(listOf("remove(2, 2026-09)"), budgets.calls)
        assertNull(budgets.get(rent.id, september))
    }

    @Test
    fun `remove without an existing budget is ignored`() = runTest {
        val vm = viewModel()
        vm.load(groceries.id, september)
        vm.onEvent(SetBudgetEvent.Remove)
        assertFalse(vm.uiState.value.isDone)
        assertTrue(budgets.calls.isEmpty())
    }

    @Test
    fun `currency switch keeps the typed digits and saves in the new currency`() = runTest {
        val vm = viewModel()
        vm.load(groceries.id, september)
        vm.onEvent(SetBudgetEvent.AmountEdited("450.50"))

        vm.onEvent(SetBudgetEvent.CurrencySelected(Currency.INR))
        assertEquals("450.50", vm.uiState.value.amountText)
        assertEquals(45_050L, vm.uiState.value.amountMinor)
        assertEquals(Currency.INR, vm.uiState.value.currency)

        vm.onEvent(SetBudgetEvent.Save)
        assertEquals(Money(45_050, Currency.INR), budgets.get(groceries.id, september)?.amount)
    }

    @Test
    fun `reloading for another category resets input and done flag`() = runTest {
        val vm = viewModel()
        vm.load(rent.id, september)
        vm.onEvent(SetBudgetEvent.Remove)
        assertTrue(vm.uiState.value.isDone)

        vm.load(groceries.id, september)
        val state = vm.uiState.value
        assertFalse(state.isDone)
        assertEquals("Groceries", state.categoryName)
        assertEquals("", state.amountText)
        assertNull(state.existingBudget)
        assertEquals(Currency.EUR, state.currency)
    }

    @Test
    fun `base currency setting is used for new budgets`() = runTest {
        settings.setBaseCurrency(Currency.USD)
        val vm = viewModel()
        vm.load(groceries.id, YearMonth.of(2026, 10))
        assertEquals(Currency.USD, vm.uiState.value.currency)
    }

    @Test
    fun `unknown category loads with an empty name rather than failing`() = runTest {
        val vm = viewModel()
        vm.load(99L, september)
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("", state.categoryName)
    }
}

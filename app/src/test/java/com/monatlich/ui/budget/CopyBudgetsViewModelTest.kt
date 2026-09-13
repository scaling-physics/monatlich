package com.monatlich.ui.budget

import app.cash.turbine.test
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class CopyBudgetsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)

    private val budgets = BudgetFakeBudgetRepository(
        listOf(
            budget(id = 1, categoryId = 1, month = august, minor = 45_000),
            budget(id = 2, categoryId = 2, month = august, minor = 120_000),
            budget(id = 3, categoryId = 3, month = august, minor = 9_000),
            // A different month must not count.
            budget(id = 4, categoryId = 1, month = YearMonth.of(2026, 7), minor = 1),
        ),
    )
    private val settings = BudgetFakeSettingsRepository(Currency.EUR)

    private fun viewModel() = CopyBudgetsViewModel(budgets, settings)

    @Test
    fun `preview counts and sums the previous month's budgets`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().isLoading)
            vm.load(september)
            val state = expectMostRecentItem()

            assertFalse(state.isLoading)
            assertEquals(september, state.month)
            assertEquals(august, state.previousMonth)
            assertEquals(3, state.count)
            assertEquals(listOf(Money(174_000, Currency.EUR)), state.totals)
            assertTrue(state.canCopy)
            assertFalse(state.isDone)
        }
    }

    @Test
    fun `mixed currencies are summed separately with base currency first`() = runTest {
        budgets.set(categoryId = 4, month = august, amount = Money(500_000, Currency.INR))
        budgets.set(categoryId = 5, month = august, amount = Money(10_000, Currency.USD))
        settings.setBaseCurrency(Currency.USD)
        budgets.calls.clear()

        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.load(september)
            val state = expectMostRecentItem()

            assertEquals(5, state.count)
            assertEquals(
                listOf(
                    Money(10_000, Currency.USD),
                    Money(174_000, Currency.EUR),
                    Money(500_000, Currency.INR),
                ),
                state.totals,
            )
        }
    }

    @Test
    fun `empty previous month cannot be copied`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.load(YearMonth.of(2026, 1))
            val state = expectMostRecentItem()
            assertEquals(0, state.count)
            assertTrue(state.totals.isEmpty())
            assertFalse(state.canCopy)

            vm.copy()
            expectNoEvents()
            assertTrue(budgets.calls.isEmpty())
        }
    }

    @Test
    fun `copy calls the repository and marks done`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.load(september)
            assertTrue(expectMostRecentItem().canCopy)

            vm.copy()
            val done = expectMostRecentItem()
            assertTrue(done.isDone)
            assertFalse(done.isCopying)
        }
        assertEquals("copy(2026-08, 2026-09)", budgets.calls.first())
        assertEquals(3, budgets.state.value.count { it.month == september })
    }

    @Test
    fun `preview reacts to repository changes`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.load(september)
            assertEquals(3, expectMostRecentItem().count)

            budgets.remove(categoryId = 3, month = august)
            val updated = expectMostRecentItem()
            assertEquals(2, updated.count)
            assertEquals(listOf(Money(165_000, Currency.EUR)), updated.totals)
        }
    }

    @Test
    fun `reloading for another month resets done`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.load(september)
            expectMostRecentItem()
            vm.copy()
            assertTrue(expectMostRecentItem().isDone)

            vm.load(YearMonth.of(2026, 10))
            val next = expectMostRecentItem()
            assertFalse(next.isDone)
            assertEquals(september, next.previousMonth)
            assertEquals(3, next.count)
        }
    }
}

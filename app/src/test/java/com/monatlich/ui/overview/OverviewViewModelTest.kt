package com.monatlich.ui.overview

import app.cash.turbine.test
import com.monatlich.domain.model.Budget
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.usecase.FakeBudgetRepository
import com.monatlich.domain.usecase.FakeCategoryRepository
import com.monatlich.domain.usecase.FakeExchangeRateRepository
import com.monatlich.domain.usecase.FakeSettingsRepository
import com.monatlich.domain.usecase.FakeTransactionRepository
import com.monatlich.domain.usecase.GetMonthSummary
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

class OverviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC)

    private val september = YearMonth.of(2026, 9)
    private val august = YearMonth.of(2026, 8)

    private val groceries = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = 0)
    private val rent = Category(id = 2, name = "Rent", icon = "Home", color = 0xFF6B1F2A, sortOrder = 1)
    private val fun_ = Category(id = 3, name = "Fun", icon = "Celebration", color = 0xFF7B1FA2, sortOrder = 2)

    private val categories = FakeCategoryRepository(listOf(groceries, rent, fun_))
    private val budgets = FakeBudgetRepository()
    private val transactions = FakeTransactionRepository()
    private val rates = FakeExchangeRateRepository(mapOf(Currency.USD to BigDecimal("0.9"), Currency.INR to BigDecimal("0.011")))
    private val settings = FakeSettingsRepository(Currency.EUR)

    private fun viewModel() = OverviewViewModel(
        getMonthSummary = GetMonthSummary(categories, budgets, transactions, rates, settings),
        budgets = budgets,
        clock = fixedClock,
    )

    private fun eur(major: String) = Money.of(major, Currency.EUR)

    private suspend fun expense(category: Category, date: LocalDate, major: String) {
        transactions.add(
            Transaction(
                categoryId = category.id,
                date = date,
                amount = eur(major),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.EXPENSE,
            ),
        )
    }

    @Test
    fun `initial state is loading, then the current month's summary with every seeded category`() = runTest {
        budgets.set(groceries.id, september, eur("450"))
        expense(groceries, LocalDate.of(2026, 9, 3), "120.50")

        val vm = viewModel()
        assertTrue(vm.uiState.value.isLoading)

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(september, state.month)
            assertTrue(state.isCurrentMonth)
            assertEquals("EUR", state.currencyCode)
            assertEquals(listOf("Groceries", "Rent", "Fun"), state.categories.map { it.name })

            val groceriesRow = state.categories.first { it.id == groceries.id }
            assertTrue(groceriesRow.hasBudget)
            assertEquals(45_000L, groceriesRow.budgetMinor)
            assertEquals(12_050L, groceriesRow.spentMinor)
            assertEquals("ShoppingCart", groceriesRow.icon)
            assertEquals(0xFF2E7D32, groceriesRow.color)

            val rentRow = state.categories.first { it.id == rent.id }
            assertFalse(rentRow.hasBudget)
            assertEquals(0L, rentRow.budgetMinor)
            assertEquals(0f, rentRow.progress)

            assertEquals(45_000L, state.totalBudgetMinor)
            assertEquals(12_050L, state.totalSpentMinor)
            assertTrue(state.hasAnyBudget)
            assertNull(state.selectedCategoryId)
            assertFalse(state.showCopyPrompt)
        }
    }

    @Test
    fun `switching month swaps the underlying data and back`() = runTest {
        budgets.set(groceries.id, september, eur("450"))
        budgets.set(groceries.id, august, eur("400"))
        expense(groceries, LocalDate.of(2026, 9, 3), "100")
        expense(groceries, LocalDate.of(2026, 8, 20), "250")

        val vm = viewModel()
        vm.uiState.test {
            val sept = awaitItem()
            assertEquals(september, sept.month)
            assertEquals(10_000L, sept.totalSpentMinor)
            assertEquals(45_000L, sept.totalBudgetMinor)

            vm.onEvent(OverviewEvent.PreviousMonth)
            val aug = awaitItemMatching { it.month == august && !it.isLoading && it.totalSpentMinor == 25_000L }
            assertFalse(aug.isCurrentMonth)
            assertEquals(40_000L, aug.totalBudgetMinor)

            vm.onEvent(OverviewEvent.JumpToCurrentMonth)
            val back = awaitItemMatching { it.month == september && it.totalSpentMinor == 10_000L }
            assertTrue(back.isCurrentMonth)
        }
    }

    @Test
    fun `summary updates live when a budget is written for the visible month`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertFalse(awaitItem().hasAnyBudget)

            budgets.set(fun_.id, september, Money.of("100", Currency.USD))
            val updated = awaitItemMatching { it.hasAnyBudget }
            val funRow = updated.categories.first { it.id == fun_.id }
            // 100 USD at 0.9 -> 90 EUR in base.
            assertEquals(9_000L, funRow.budgetMinor)
            assertEquals(9_000L, updated.totalBudgetMinor)
        }
    }

    @Test
    fun `category click selects it and dismiss clears it`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertNull(awaitItem().selectedCategoryId)

            vm.onEvent(OverviewEvent.CategoryClicked(rent.id))
            assertEquals(rent.id, awaitItem().selectedCategoryId)

            vm.onEvent(OverviewEvent.CategorySheetDismissed)
            assertNull(awaitItem().selectedCategoryId)
        }
    }

    @Test
    fun `changing month closes an open category sheet`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(OverviewEvent.CategoryClicked(rent.id))
            assertEquals(rent.id, awaitItem().selectedCategoryId)

            vm.onEvent(OverviewEvent.NextMonth)
            val next = awaitItemMatching { it.month == YearMonth.of(2026, 10) }
            assertNull(next.selectedCategoryId)
        }
    }

    @Test
    fun `copy prompt shows only when this month is empty and the previous month has budgets`() = runTest {
        budgets.set(groceries.id, august, eur("400"))

        val vm = viewModel()
        vm.uiState.test {
            // September: no budgets, August has one -> prompt.
            assertTrue(awaitItem().showCopyPrompt)

            // October: no budgets, September also none -> no prompt.
            vm.onEvent(OverviewEvent.NextMonth)
            val october = awaitItemMatching { it.month == YearMonth.of(2026, 10) && !it.isLoading }
            assertFalse(october.showCopyPrompt)

            // August itself has budgets -> no prompt.
            vm.onEvent(OverviewEvent.JumpToCurrentMonth)
            vm.onEvent(OverviewEvent.PreviousMonth)
            val augustState = awaitItemMatching { it.month == august && it.hasAnyBudget }
            assertFalse(augustState.showCopyPrompt)
        }
    }

    @Test
    fun `copy prompt disappears once budgets exist and stays dismissed for that month`() = runTest {
        budgets.set(groceries.id, august, eur("400"))

        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().showCopyPrompt)

            vm.onEvent(OverviewEvent.CopyPromptDismissed)
            assertFalse(awaitItem().showCopyPrompt)

            // Leaving and returning to September does not re-prompt in this session.
            vm.onEvent(OverviewEvent.NextMonth)
            awaitItemMatching { it.month == YearMonth.of(2026, 10) }
            vm.onEvent(OverviewEvent.PreviousMonth)
            val backInSeptember = awaitItemMatching { it.month == september }
            assertFalse(backInSeptember.showCopyPrompt)
        }
    }

    @Test
    fun `copy prompt goes away after budgets are copied`() = runTest {
        budgets.set(groceries.id, august, eur("400"))

        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().showCopyPrompt)

            budgets.copy(from = august, to = september)
            val copied = awaitItemMatching { it.hasAnyBudget }
            assertFalse(copied.showCopyPrompt)
            assertEquals(40_000L, copied.totalBudgetMinor)
        }
    }

    @Test
    fun `add sheet toggles and survives month change`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertFalse(awaitItem().isAddSheetVisible)

            vm.onEvent(OverviewEvent.AddExpenseClicked)
            assertTrue(awaitItem().isAddSheetVisible)

            vm.onEvent(OverviewEvent.NextMonth)
            assertTrue(awaitItemMatching { it.month == YearMonth.of(2026, 10) }.isAddSheetVisible)

            vm.onEvent(OverviewEvent.AddSheetDismissed)
            assertFalse(awaitItem().isAddSheetVisible)
        }
    }

    @Test
    fun `income transactions surface as totalIncomeMinor and net`() = runTest {
        expense(groceries, LocalDate.of(2026, 9, 3), "100")
        transactions.add(
            Transaction(
                categoryId = rent.id,
                date = LocalDate.of(2026, 9, 1),
                amount = eur("3000"),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.INCOME,
            ),
        )

        val vm = viewModel()
        vm.uiState.test {
            val state = awaitItemMatching { it.totalIncomeMinor > 0L }
            assertEquals(300_000L, state.totalIncomeMinor)
            assertEquals(10_000L, state.totalSpentMinor)
            assertEquals(290_000L, state.netMinor)
            assertTrue(state.hasIncome)
        }
    }

    @Test
    fun `previous month crosses the year boundary`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            repeat(9) { vm.onEvent(OverviewEvent.PreviousMonth) }
            val state = awaitItemMatching { it.month == YearMonth.of(2025, 12) }
            assertFalse(state.isCurrentMonth)
        }
    }

    /** Skips intermediate emissions (month switch + summary arrival can be two items). */
    private suspend fun app.cash.turbine.ReceiveTurbine<OverviewUiState>.awaitItemMatching(
        predicate: (OverviewUiState) -> Boolean,
    ): OverviewUiState {
        repeat(10) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching state within 10 emissions")
    }
}

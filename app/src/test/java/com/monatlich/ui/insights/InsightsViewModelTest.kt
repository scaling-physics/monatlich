package com.monatlich.ui.insights

import app.cash.turbine.test
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
import com.monatlich.domain.usecase.GetMonthlyTrend
import com.monatlich.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class InsightsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC)
    private val september = YearMonth.of(2026, 9)

    private val groceries = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = 0)
    private val rent = Category(id = 2, name = "Rent", icon = "Home", color = 0xFF6B1F2A, sortOrder = 1)

    private val categories = FakeCategoryRepository(listOf(groceries, rent))
    private val budgets = FakeBudgetRepository()
    private val transactions = FakeTransactionRepository()
    private val rates = FakeExchangeRateRepository()
    private val settings = FakeSettingsRepository(Currency.EUR)

    private fun viewModel() = InsightsViewModel(
        getMonthSummary = GetMonthSummary(categories, budgets, transactions, rates, settings),
        getMonthlyTrend = GetMonthlyTrend(transactions, settings),
        clock = fixedClock,
    )

    private suspend fun expense(category: Category, date: String, major: String) {
        transactions.add(
            Transaction(
                categoryId = category.id,
                date = LocalDate.parse(date),
                amount = Money.of(major, Currency.EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.EXPENSE,
            ),
        )
    }

    @Test
    fun `slices are sorted by spend descending and fractions sum to one`() = runTest {
        expense(groceries, "2026-09-05", "30.00")
        expense(rent, "2026-09-06", "70.00")

        viewModel().uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf(rent.id, groceries.id), state.categorySlices.map { it.categoryId })
            assertEquals(10000L, state.totalSpentMinor)
            assertEquals(0.7f, state.categorySlices[0].fraction, 0.001f)
            assertEquals(0.3f, state.categorySlices[1].fraction, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `categories with no spend this month are excluded from the donut`() = runTest {
        expense(groceries, "2026-09-05", "10.00")

        viewModel().uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf(groceries.id), state.categorySlices.map { it.categoryId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trend covers six months ending at the current month, oldest first`() = runTest {
        expense(groceries, "2026-04-05", "10.00")
        expense(groceries, "2026-09-05", "20.00")

        viewModel().uiState.test {
            val state = awaitLoaded()
            assertEquals(6, state.trend.size)
            assertEquals(september.minusMonths(5), state.trend.first().month)
            assertEquals(september, state.trend.last().month)
            assertEquals(1000L, state.trend.first().spentMinor)
            assertEquals(2000L, state.trend.last().spentMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty month has no slices but is not stuck loading`() = runTest {
        viewModel().uiState.test {
            val state = awaitLoaded()
            assertTrue(state.categorySlices.isEmpty())
            assertEquals(0L, state.totalSpentMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<InsightsUiState>.awaitLoaded(): InsightsUiState {
        var item = awaitItem()
        while (item.isLoading) item = awaitItem()
        return item
    }
}

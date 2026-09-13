package com.monatlich.domain.usecase

import app.cash.turbine.test
import com.monatlich.domain.model.Budget
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency.EUR
import com.monatlich.domain.model.Currency.INR
import com.monatlich.domain.model.Currency.USD
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class GetMonthSummaryTest {

    private val september = YearMonth.of(2026, 9)

    private val groceries = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 1, sortOrder = 0)
    private val rent = Category(id = 2, name = "Rent", icon = "Home", color = 2, sortOrder = 1)
    private val travel = Category(id = 3, name = "Travel", icon = "Flight", color = 3, sortOrder = 2, archived = true)
    private val salary = Category(id = 4, name = "Salary", icon = "Work", color = 4, sortOrder = 3)
    private val other = Category(id = 5, name = "Other", icon = "MoreHoriz", color = 5, sortOrder = 4)
    private val old = Category(id = 6, name = "Old", icon = "Delete", color = 6, sortOrder = 5, archived = true)

    private val categories = FakeCategoryRepository(listOf(groceries, rent, travel, salary, other, old))
    private val budgets = FakeBudgetRepository(
        listOf(
            Budget(id = 1, categoryId = groceries.id, month = september, amount = Money(40000, EUR)),
            Budget(id = 2, categoryId = rent.id, month = september, amount = Money(120000, EUR)),
            Budget(id = 3, categoryId = travel.id, month = september, amount = Money(50000, USD)),
            Budget(id = 4, categoryId = groceries.id, month = september.minusMonths(1), amount = Money(99900, EUR)),
        ),
    )
    private val transactions = FakeTransactionRepository(
        listOf(
            expense(1, groceries.id, "2026-09-02", Money(5530, EUR), "1"),
            expense(2, groceries.id, "2026-09-10", Money(123456, INR), "0.0108"),
            expense(3, groceries.id, "2026-09-11", Money(200000, INR), "0.0108"),
            expense(4, travel.id, "2026-09-20", Money(10000, USD), "0.90"),
            expense(5, groceries.id, "2026-08-30", Money(99900, EUR), "1"),
            Transaction(6, salary.id, LocalDate.parse("2026-09-01"), Money(250000, EUR), BigDecimal.ONE, TransactionType.INCOME, "Pay"),
        ),
    )
    private val rates = FakeExchangeRateRepository(
        mapOf(EUR to BigDecimal.ONE, USD to BigDecimal("0.92"), INR to BigDecimal("0.0108")),
    )
    private val settings = FakeSettingsRepository(EUR)

    private val getMonthSummary = GetMonthSummary(categories, budgets, transactions, rates, settings)

    @Test
    fun `mixed-currency month converts everything to base to the cent`() = runTest {
        val summary = getMonthSummary(september).first()

        assertEquals(september, summary.month)
        assertEquals(EUR, summary.baseCurrency)

        // Groceries: €55.30 + (₹1234.56 + ₹2000.00) × 0.0108 = €55.30 + €34.93 = €90.23
        val g = summary.categories.single { it.category == groceries }
        assertEquals(Money(40000, EUR), g.budget)
        assertEquals(Money(40000, EUR), g.budgetInBase)
        assertEquals(Money(9023, EUR), g.spentInBase)
        assertEquals(Money(30977, EUR), g.remainingInBase)

        // Rent: budget, nothing spent
        val r = summary.categories.single { it.category == rent }
        assertEquals(Money(0, EUR), r.spentInBase)
        assertEquals(Money(120000, EUR), r.remainingInBase)

        // Travel (archived, but budgeted and used this month): $500 budget via *current* table rate
        // 0.92 = €460.00; $100 spend via *stored* rate 0.90 = €90.00
        val t = summary.categories.single { it.category == travel }
        assertEquals(Money(50000, USD), t.budget)
        assertEquals(Money(46000, EUR), t.budgetInBase)
        assertEquals(Money(9000, EUR), t.spentInBase)
        assertEquals(Money(37000, EUR), t.remainingInBase)

        // Salary and Other: active, no budget -> remaining is null, spent is zero
        val s = summary.categories.single { it.category == salary }
        assertNull(s.budget)
        assertNull(s.remainingInBase)
        assertEquals(Money(0, EUR), s.spentInBase)
        val o = summary.categories.single { it.category == other }
        assertNull(o.budget)
        assertEquals(Money(0, EUR), o.spentInBase)

        // Archived with nothing this month is hidden; rows follow sort order
        assertEquals(listOf(groceries, rent, travel, salary, other), summary.categories.map { it.category })

        assertEquals(Money(206000, EUR), summary.totalBudgetInBase) // 400 + 1200 + 460
        assertEquals(Money(18023, EUR), summary.totalSpentInBase) // 90.23 + 90.00
        assertEquals(Money(250000, EUR), summary.totalIncomeInBase)
        assertEquals(Money(187977, EUR), summary.totalRemainingInBase)
        assertEquals(Money(231977, EUR), summary.netInBase)
    }

    @Test
    fun `an empty month yields zero totals and one row per active category`() = runTest {
        val summary = getMonthSummary(YearMonth.of(2027, 1)).first()

        assertEquals(Money.zero(EUR), summary.totalBudgetInBase)
        assertEquals(Money.zero(EUR), summary.totalSpentInBase)
        assertEquals(Money.zero(EUR), summary.totalIncomeInBase)
        assertEquals(listOf(groceries, rent, salary, other), summary.categories.map { it.category })
        summary.categories.forEach {
            assertNull(it.budget)
            assertNull(it.remainingInBase)
            assertEquals(Money.zero(EUR), it.spentInBase)
        }
    }

    @Test
    fun `summary is live - new expense and rate edits flow through`() = runTest {
        getMonthSummary(september).test {
            assertEquals(Money(18023, EUR), awaitItem().totalSpentInBase)

            transactions.add(expense(0, rent.id, "2026-09-05", Money(120000, EUR), "1"))
            val afterRent = awaitItem()
            assertEquals(Money(138023, EUR), afterRent.totalSpentInBase)
            assertEquals(Money(0, EUR), afterRent.categories.single { it.category == rent }.remainingInBase)

            // Editing the rate table re-values the USD *budget* but never the stored USD *spend*
            rates.set(USD, BigDecimal("1.00"))
            val afterRate = awaitItem()
            val t = afterRate.categories.single { it.category == travel }
            assertEquals(Money(50000, EUR), t.budgetInBase)
            assertEquals(Money(9000, EUR), t.spentInBase)
            assertEquals(Money(210000, EUR), afterRate.totalBudgetInBase)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching base currency re-labels totals using stored rates`() = runTest {
        getMonthSummary(september).test {
            awaitItem()
            settings.setBaseCurrency(USD)
            val summary = awaitItem()
            assertEquals(USD, summary.baseCurrency)
            // Travel spend: $100 already in the new base, no conversion
            assertEquals(Money(10000, USD), summary.categories.single { it.category == travel }.spentInBase)
            // The USD budget is now native, the EUR budgets convert through the table (EUR row = 1)
            assertEquals(Money(50000, USD), summary.categories.single { it.category == travel }.budgetInBase)
            assertEquals(Money(40000, USD), summary.categories.single { it.category == groceries }.budgetInBase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun expense(id: Long, categoryId: Long, date: String, amount: Money, rate: String) = Transaction(
        id = id,
        categoryId = categoryId,
        date = LocalDate.parse(date),
        amount = amount,
        rateToBase = BigDecimal(rate),
        type = TransactionType.EXPENSE,
    )
}

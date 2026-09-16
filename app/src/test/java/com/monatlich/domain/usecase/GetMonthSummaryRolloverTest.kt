package com.monatlich.domain.usecase

import app.cash.turbine.test
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency.EUR
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

class GetMonthSummaryRolloverTest {

    private val jul = YearMonth.of(2026, 7)
    private val aug = YearMonth.of(2026, 8)
    private val sep = YearMonth.of(2026, 9)

    private val travel = Category(id = 1, name = "Travel", icon = "Flight", color = 1, rolloverEnabled = true)
    private val eatingOut = Category(id = 2, name = "Eating out", icon = "Restaurant", color = 2, rolloverEnabled = false)

    private val categories = FakeCategoryRepository(listOf(travel, eatingOut))
    private val budgets = FakeBudgetRepository()
    private val transactions = FakeTransactionRepository()
    private val rates = FakeExchangeRateRepository()
    private val settings = FakeSettingsRepository(EUR)

    private val getMonthSummary = GetMonthSummary(categories, budgets, transactions, rates, settings)

    private suspend fun budget(category: Category, month: YearMonth, major: String) =
        budgets.set(category.id, month, Money.of(major, EUR))

    private suspend fun spend(category: Category, date: String, major: String) {
        transactions.add(
            Transaction(
                categoryId = category.id,
                date = LocalDate.parse(date),
                amount = Money.of(major, EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.EXPENSE,
            ),
        )
    }

    @Test
    fun `unspent budget carries into the next month`() = runTest {
        budget(travel, jul, "100.00")
        spend(travel, "2026-07-15", "30.00")
        budget(travel, sep, "100.00")

        val row = getMonthSummary(sep).first().categories.single { it.category == travel }

        // July leftover 70.00 + September's own 100.00 budget = 170.00 available before spend.
        assertEquals(Money.of("70.00", EUR), row.carriedInBase)
        assertEquals(Money.of("170.00", EUR), row.budgetInBase)
        assertEquals(Money.of("170.00", EUR), row.remainingInBase)
    }

    @Test
    fun `rollover is cumulative across several months`() = runTest {
        budget(travel, jul, "100.00")
        spend(travel, "2026-07-05", "30.00") // +70 carried
        budget(travel, aug, "100.00") // no spending in August -> +100 carried
        budget(travel, sep, "50.00")
        spend(travel, "2026-09-05", "40.00")

        val row = getMonthSummary(sep).first().categories.single { it.category == travel }

        // Carried in: (100-30) from July + (100-0) from August = 170.
        assertEquals(Money.of("170.00", EUR), row.carriedInBase)
        // Available: 170 carried + 50 this month = 220; spent 40 -> remaining 180.
        assertEquals(Money.of("220.00", EUR), row.budgetInBase)
        assertEquals(Money.of("180.00", EUR), row.remainingInBase)
    }

    @Test
    fun `overspending a rollover category carries a negative balance forward`() = runTest {
        budget(travel, jul, "50.00")
        spend(travel, "2026-07-10", "80.00") // overspent by 30
        budget(travel, sep, "100.00")

        val row = getMonthSummary(sep).first().categories.single { it.category == travel }

        assertEquals(Money.of("-30.00", EUR), row.carriedInBase)
        assertEquals(Money.of("70.00", EUR), row.budgetInBase) // 100 - 30
        assertEquals(true, row.remainingInBase!!.isNegative.not()) // still positive this month
    }

    @Test
    fun `a rollover category with no budget this month still shows carried-in as available`() = runTest {
        budget(travel, jul, "100.00")
        spend(travel, "2026-07-10", "20.00")
        // No budget at all in September.

        val row = getMonthSummary(sep).first().categories.single { it.category == travel }

        assertNull(row.budget) // no explicit budget this month
        assertEquals(Money.of("80.00", EUR), row.carriedInBase)
        assertEquals(Money.of("80.00", EUR), row.budgetInBase) // pure carry-in
        assertEquals(Money.of("80.00", EUR), row.remainingInBase)
    }

    @Test
    fun `rollover disabled ignores history entirely`() = runTest {
        budget(eatingOut, jul, "100.00")
        spend(eatingOut, "2026-07-10", "10.00") // 90 unspent, but rollover is off
        budget(eatingOut, sep, "50.00")
        spend(eatingOut, "2026-09-10", "20.00")

        val row = getMonthSummary(sep).first().categories.single { it.category == eatingOut }

        assertEquals(Money.zero(EUR), row.carriedInBase)
        assertEquals(Money.of("50.00", EUR), row.budgetInBase)
        assertEquals(Money.of("30.00", EUR), row.remainingInBase)
    }

    @Test
    fun `a category with no history at all has zero carry, not a crash`() = runTest {
        budget(travel, sep, "100.00")
        val row = getMonthSummary(sep).first().categories.single { it.category == travel }
        assertEquals(Money.zero(EUR), row.carriedInBase)
        assertEquals(Money.of("100.00", EUR), row.remainingInBase)
    }

    @Test
    fun `rollover is live - a new expense in a prior month updates this month's carry`() = runTest {
        budget(travel, jul, "100.00")
        budget(travel, sep, "0.00")

        getMonthSummary(sep).test {
            val before = awaitItem().categories.single { it.category == travel }
            assertEquals(Money.of("100.00", EUR), before.carriedInBase)

            spend(travel, "2026-07-20", "40.00")

            val after = awaitItem().categories.single { it.category == travel }
            assertEquals(Money.of("60.00", EUR), after.carriedInBase)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

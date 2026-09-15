package com.monatlich.domain.usecase

import app.cash.turbine.test
import com.monatlich.domain.model.Currency.EUR
import com.monatlich.domain.model.Currency.USD
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class GetMonthlyTrendTest {

    private val september = YearMonth.of(2026, 9)
    private val categoryId = 1L

    private val transactions = FakeTransactionRepository(
        listOf(
            expense(1, "2026-07-05", Money(5000, EUR)),
            expense(2, "2026-08-05", Money(7000, EUR)),
            income(3, "2026-08-06", Money(200000, EUR)),
            expense(4, "2026-09-05", Money(9000, EUR)),
            expense(5, "2026-09-06", Money(10000, USD), rate = "0.90"),
        ),
    )
    private val settings = FakeSettingsRepository(EUR)

    private val getMonthlyTrend = GetMonthlyTrend(transactions, settings)

    @Test
    fun `builds one point per month, oldest first, converted to base`() = runTest {
        val points = getMonthlyTrend(monthCount = 3, endingAt = september).first()

        assertEquals(listOf(september.minusMonths(2), september.minusMonths(1), september), points.map { it.month })
        assertEquals(Money(5000, EUR), points[0].spentInBase)
        assertEquals(Money.zero(EUR), points[0].incomeInBase)
        assertEquals(Money(7000, EUR), points[1].spentInBase)
        assertEquals(Money(200000, EUR), points[1].incomeInBase)
        // 90.00 EUR + $100 * 0.90 = 90.00 EUR -> 180.00 EUR
        assertEquals(Money(18000, EUR), points[2].spentInBase)
    }

    @Test
    fun `a month with no transactions is zero, not absent`() = runTest {
        val points = getMonthlyTrend(monthCount = 2, endingAt = september.plusMonths(1)).first()
        assertEquals(2, points.size)
        assertEquals(Money.zero(EUR), points[1].spentInBase)
        assertEquals(Money.zero(EUR), points[1].incomeInBase)
    }

    @Test
    fun `is live - a new expense updates the matching month only`() = runTest {
        getMonthlyTrend(monthCount = 2, endingAt = september).test {
            val first = awaitItem()
            // September: 90.00 EUR (id 4) + $100 * 0.90 (id 5) = 90.00 EUR -> 180.00 EUR
            assertEquals(Money(18000, EUR), first[1].spentInBase)

            transactions.add(expense(6, "2026-09-20", Money(1000, EUR)))
            val updated = awaitItem()
            assertEquals(Money(19000, EUR), updated[1].spentInBase)
            assertEquals(first[0], updated[0])

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching base currency re-converts every month`() = runTest {
        getMonthlyTrend(monthCount = 1, endingAt = september).test {
            awaitItem()
            settings.setBaseCurrency(USD)
            val points = awaitItem()
            assertEquals(USD, points.single().spentInBase.currency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun expense(id: Long, date: String, amount: Money, rate: String = "1") = Transaction(
        id = id,
        categoryId = categoryId,
        date = LocalDate.parse(date),
        amount = amount,
        rateToBase = BigDecimal(rate),
        type = TransactionType.EXPENSE,
    )

    private fun income(id: Long, date: String, amount: Money, rate: String = "1") = Transaction(
        id = id,
        categoryId = categoryId,
        date = LocalDate.parse(date),
        amount = amount,
        rateToBase = BigDecimal(rate),
        type = TransactionType.INCOME,
    )
}

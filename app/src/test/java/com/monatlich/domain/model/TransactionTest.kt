package com.monatlich.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class TransactionTest {

    private fun tx(date: LocalDate, amount: Money, rate: String = "1") = Transaction(
        categoryId = 1L,
        date = date,
        amount = amount,
        rateToBase = BigDecimal(rate),
        type = TransactionType.EXPENSE,
    )

    @Test
    fun `month is derived from date`() {
        assertEquals(YearMonth.of(2026, 9), tx(LocalDate.of(2026, 9, 30), Money(1, Currency.EUR)).month)
        assertEquals(YearMonth.of(2026, 10), tx(LocalDate.of(2026, 10, 1), Money(1, Currency.EUR)).month)
    }

    @Test
    fun `amountInBase converts with the stored rate, or returns the amount when already in base`() {
        val rupees = tx(LocalDate.of(2026, 9, 1), Money(123456, Currency.INR), rate = "0.0108")
        assertEquals(Money(1333, Currency.EUR), rupees.amountInBase(Currency.EUR))

        val euros = tx(LocalDate.of(2026, 9, 1), Money(4200, Currency.EUR))
        assertEquals(Money(4200, Currency.EUR), euros.amountInBase(Currency.EUR))
    }

    @Test
    fun `rateToBase must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            tx(LocalDate.of(2026, 9, 1), Money(1, Currency.EUR), rate = "0")
        }
    }

    @Test
    fun `CategorySummary derives budgetInBase, progress and over-budget flag`() {
        val category = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32)
        val eur = Currency.EUR

        val onTrack = CategorySummary(category, Money(40000, eur), Money(10000, eur), Money(30000, eur), Money.zero(eur))
        assertEquals(Money(40000, eur), onTrack.budgetInBase)
        assertEquals(0.25f, onTrack.progress)
        assertEquals(false, onTrack.isOverBudget)

        val over = CategorySummary(category, Money(10000, eur), Money(15000, eur), Money(-5000, eur), Money.zero(eur))
        assertEquals(1f, over.progress)
        assertEquals(true, over.isOverBudget)

        val unbudgeted = CategorySummary(category, null, Money(15000, eur), null, Money.zero(eur))
        assertEquals(null, unbudgeted.budgetInBase)
        assertEquals(0f, unbudgeted.progress)
        assertEquals(false, unbudgeted.isOverBudget)
    }

    @Test
    fun `MonthSummary derives remaining and net`() {
        val eur = Currency.EUR
        val summary = MonthSummary(
            month = YearMonth.of(2026, 9),
            totalBudgetInBase = Money(100000, eur),
            totalSpentInBase = Money(60000, eur),
            totalIncomeInBase = Money(250000, eur),
            categories = emptyList(),
        )
        assertEquals(eur, summary.baseCurrency)
        assertEquals(Money(40000, eur), summary.totalRemainingInBase)
        assertEquals(Money(190000, eur), summary.netInBase)
    }
}

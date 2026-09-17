package com.monatlich.domain.usecase

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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GetSpendingBreakdownTest {

    private val groceries = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 1, sortOrder = 0)
    private val rent = Category(id = 2, name = "Rent", icon = "Home", color = 2, sortOrder = 1)
    private val salary = Category(id = 3, name = "Salary", icon = "Work", color = 3, sortOrder = 2)
    private val unused = Category(id = 4, name = "Unused", icon = "MoreHoriz", color = 4, sortOrder = 3)

    private val categories = FakeCategoryRepository(listOf(groceries, rent, salary, unused))
    private val transactions = FakeTransactionRepository(
        listOf(
            expense(1, groceries.id, "2026-09-02", Money(5530, EUR), "1"),
            expense(2, groceries.id, "2026-09-10", Money(123456, INR), "0.0108"),
            expense(3, rent.id, "2026-09-01", Money(12000, EUR), "1"),
            expense(4, groceries.id, "2026-08-30", Money(99900, EUR), "1"), // out of range
            Transaction(5, salary.id, LocalDate.parse("2026-09-01"), Money(250000, EUR), BigDecimal.ONE, TransactionType.INCOME, "Pay"),
        ),
    )
    private val settings = FakeSettingsRepository(EUR)

    private val getSpendingBreakdown = GetSpendingBreakdown(categories, transactions, settings)

    @Test
    fun `sums expenses within the range, converts to base, sorts descending, drops zero and out-of-range`() = runTest {
        val breakdown = getSpendingBreakdown(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).first()

        assertEquals(EUR, breakdown.baseCurrency)
        assertEquals(2, breakdown.categories.size)

        // Rent: €120.00 is the largest, so it sorts first.
        assertEquals(rent, breakdown.categories[0].category)
        assertEquals(Money(12000, EUR), breakdown.categories[0].spentInBase)

        // Groceries: €55.30 + ₹1234.56 × 0.0108 = €55.30 + €13.33 = €68.63
        assertEquals(groceries, breakdown.categories[1].category)
        assertEquals(Money(6863, EUR), breakdown.categories[1].spentInBase)

        assertEquals(Money(18863, EUR), breakdown.totalSpentInBase)

        // Income and categories with no spend in range never appear.
        assertTrue(breakdown.categories.none { it.category == salary || it.category == unused })
    }

    @Test
    fun `an empty range has no categories and a zero total`() = runTest {
        val breakdown = getSpendingBreakdown(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)).first()

        assertTrue(breakdown.categories.isEmpty())
        assertEquals(Money(0, EUR), breakdown.totalSpentInBase)
    }

    private fun expense(id: Long, categoryId: Long, date: String, amount: Money, rate: String) = Transaction(
        id = id,
        categoryId = categoryId,
        date = LocalDate.parse(date),
        amount = amount,
        rateToBase = BigDecimal(rate),
        type = TransactionType.EXPENSE,
        note = null,
    )
}

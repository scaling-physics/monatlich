package com.monatlich.domain.usecase

import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ExportTransactionsCsvTest {

    private val groceries = Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32)
    private val categories = FakeCategoryRepository(listOf(groceries))
    private val transactions = FakeTransactionRepository()

    private fun export() = ExportTransactionsCsv(transactions, categories)

    @Test
    fun `renders a header even with no transactions`() = runTest {
        assertEquals("Date,Type,Category,Amount,Currency,Note", export()())
    }

    @Test
    fun `renders one row per transaction, newest first, amounts in major units`() = runTest {
        transactions.add(
            Transaction(
                categoryId = groceries.id,
                date = LocalDate.of(2026, 9, 1),
                amount = Money(4_250, Currency.EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.EXPENSE,
                note = "Weekly shop",
            ),
        )
        transactions.add(
            Transaction(
                categoryId = groceries.id,
                date = LocalDate.of(2026, 9, 10),
                amount = Money(300_000, Currency.EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.INCOME,
            ),
        )

        val csv = export()()
        val lines = csv.lines()
        assertEquals("Date,Type,Category,Amount,Currency,Note", lines[0])
        assertEquals("2026-09-10,INCOME,Groceries,3000.00,EUR,", lines[1])
        assertEquals("2026-09-01,EXPENSE,Groceries,42.50,EUR,Weekly shop", lines[2])
    }

    @Test
    fun `quotes a note that contains a comma`() = runTest {
        transactions.add(
            Transaction(
                categoryId = groceries.id,
                date = LocalDate.of(2026, 9, 1),
                amount = Money(1_000, Currency.EUR),
                rateToBase = BigDecimal.ONE,
                type = TransactionType.EXPENSE,
                note = "Milk, eggs",
            ),
        )
        val line = export()().lines()[1]
        assertEquals("2026-09-01,EXPENSE,Groceries,10.00,EUR,\"Milk, eggs\"", line)
    }
}

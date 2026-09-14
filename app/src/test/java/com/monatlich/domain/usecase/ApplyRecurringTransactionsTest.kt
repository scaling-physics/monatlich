package com.monatlich.domain.usecase

import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ApplyRecurringTransactionsTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC)
    private val september = YearMonth.of(2026, 9)

    private val recurring = FakeRecurringRepository()
    private val transactions = FakeTransactionRepository()
    private val rates = FakeExchangeRateRepository(mapOf(Currency.USD to BigDecimal("0.92"), Currency.INR to BigDecimal("0.0108")))
    private val settings = FakeSettingsRepository(Currency.EUR)

    private val apply = ApplyRecurringTransactions(recurring, transactions, rates, settings, clock)

    private fun rule(
        day: Int = 1,
        amount: Money = Money(120_000, Currency.EUR),
        start: YearMonth = september,
        end: YearMonth? = null,
        active: Boolean = true,
        type: TransactionType = TransactionType.EXPENSE,
        note: String? = "Rent",
    ) = RecurringTransaction(
        categoryId = 2,
        amount = amount,
        type = type,
        note = note,
        dayOfMonth = day,
        startMonth = start,
        endMonth = end,
        active = active,
    )

    @Test
    fun generatesOneTransactionPerRuleWithRuleFieldsAndLink() = runTest {
        val id = recurring.add(rule(day = 5, type = TransactionType.INCOME, note = "Salary"))

        assertEquals(1, apply(september))

        val generated = transactions.state.value.single()
        assertEquals(id, generated.recurringId)
        assertEquals(2L, generated.categoryId)
        assertEquals(LocalDate.of(2026, 9, 5), generated.date)
        assertEquals(Money(120_000, Currency.EUR), generated.amount)
        assertEquals(TransactionType.INCOME, generated.type)
        assertEquals("Salary", generated.note)
        assertEquals(0, BigDecimal.ONE.compareTo(generated.rateToBase))
    }

    @Test
    fun secondCallIsIdempotent() = runTest {
        recurring.add(rule())
        recurring.add(rule(day = 15))

        assertEquals(2, apply(september))
        assertEquals(0, apply(september))
        assertEquals(2, transactions.state.value.size)
    }

    @Test
    fun existingLinkedTransactionSuppressesGenerationEvenIfDateDiffers() = runTest {
        val id = recurring.add(rule(day = 1))
        apply(september)
        val generated = transactions.state.value.single()
        // The user moved the generated entry to another day; it still counts for September.
        transactions.update(generated.copy(date = LocalDate.of(2026, 9, 20)))

        assertEquals(0, apply(september))
        assertEquals(listOf(id), transactions.state.value.map { it.recurringId })
    }

    @Test
    fun day31IsClampedToMonthLength() = runTest {
        recurring.add(rule(day = 31, start = YearMonth.of(2027, 1)))
        val leapClock = Clock.fixed(Instant.parse("2028-02-01T00:00:00Z"), ZoneOffset.UTC)
        val applyLater = ApplyRecurringTransactions(recurring, transactions, rates, settings, leapClock)

        applyLater(YearMonth.of(2027, 2))
        applyLater(YearMonth.of(2028, 2))
        applyLater(YearMonth.of(2027, 4))

        val dates = transactions.state.value.map { it.date }.sorted()
        assertEquals(
            listOf(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 4, 30), LocalDate.of(2028, 2, 29)),
            dates,
        )
    }

    @Test
    fun respectsStartMonth() = runTest {
        recurring.add(rule(start = YearMonth.of(2026, 10)))

        assertEquals(0, apply(september))
        assertEquals(1, apply(YearMonth.of(2026, 10)))
    }

    @Test
    fun respectsEndMonthInclusive() = runTest {
        recurring.add(rule(start = YearMonth.of(2026, 7), end = YearMonth.of(2026, 8)))

        assertEquals(1, apply(YearMonth.of(2026, 8)))
        assertEquals(0, apply(september))
    }

    @Test
    fun skipsInactiveRules() = runTest {
        val id = recurring.add(rule(active = false))
        assertEquals(0, apply(september))

        recurring.setActive(id, true)
        assertEquals(1, apply(september))
    }

    @Test
    fun neverGeneratesBeyondNextMonth() = runTest {
        recurring.add(rule(start = YearMonth.of(2026, 1)))

        assertEquals(1, apply(YearMonth.of(2026, 10)))
        assertEquals(0, apply(YearMonth.of(2026, 11)))
        assertEquals(0, apply(YearMonth.of(2027, 3)))
        assertTrue(transactions.state.value.none { it.month.isAfter(YearMonth.of(2026, 10)) })
    }

    @Test
    fun snapshotsCurrentRateToBaseAtGenerationTime() = runTest {
        recurring.add(rule(amount = Money(10_000, Currency.USD)))

        apply(september)
        rates.set(Currency.USD, BigDecimal("0.50"))
        apply(YearMonth.of(2026, 10))

        val byMonth = transactions.state.value.associateBy { it.month }
        assertEquals(BigDecimal("0.92"), byMonth.getValue(september).rateToBase)
        assertEquals(BigDecimal("0.50"), byMonth.getValue(YearMonth.of(2026, 10)).rateToBase)
    }

    @Test
    fun missingRateRowFallsBackToOne() = runTest {
        val noRates = FakeExchangeRateRepository()
        val applyNoRates = ApplyRecurringTransactions(recurring, transactions, noRates, settings, clock)
        recurring.add(rule(amount = Money(10_000, Currency.INR)))

        applyNoRates(september)

        assertEquals(0, BigDecimal.ONE.compareTo(transactions.state.value.single().rateToBase))
    }

    @Test
    fun baseCurrencyRuleUsesRateOne() = runTest {
        settings.setBaseCurrency(Currency.USD)
        recurring.add(rule(amount = Money(10_000, Currency.USD)))

        apply(september)

        assertEquals(0, BigDecimal.ONE.compareTo(transactions.state.value.single().rateToBase))
    }
}

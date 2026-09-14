package com.monatlich.domain.usecase

import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.repository.ExchangeRateRepository
import com.monatlich.domain.repository.RecurringRepository
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * Materialises every active [RecurringTransaction] into [month] as a real [Transaction].
 *
 * Idempotent: a rule that already has a transaction in [month] (matched by
 * [Transaction.recurringId]) is skipped, so the use case can run on every month change. Nothing
 * is generated more than [MAX_MONTHS_AHEAD] past the current month, so a rule cannot pre-fill
 * the far future when the user pages ahead.
 *
 * The generated entry snapshots the rule's amount and the *current* rate to base
 * ([ExchangeRateRepository.currentRateToBase]; `1` when the rate table has no row), matching what
 * a manual entry made that day would store.
 */
class ApplyRecurringTransactions @Inject constructor(
    private val recurring: RecurringRepository,
    private val transactions: TransactionRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** Generates the missing transactions for [month]; returns how many were created. */
    suspend operator fun invoke(month: YearMonth): Int {
        val horizon = YearMonth.now(clock).plusMonths(MAX_MONTHS_AHEAD)
        if (month.isAfter(horizon)) return 0

        val rules = recurring.observeAll().first().filter { it.appliesTo(month) }
        if (rules.isEmpty()) return 0

        val base = settings.baseCurrency.first()
        var created = 0
        for (rule in rules) {
            if (transactions.getForRecurring(rule.id, month).isNotEmpty()) continue
            val rate = exchangeRates.currentRateToBase(rule.amount.currency, base) ?: BigDecimal.ONE
            transactions.add(
                Transaction(
                    categoryId = rule.categoryId,
                    date = rule.dateIn(month),
                    amount = rule.amount,
                    rateToBase = rate,
                    type = rule.type,
                    note = rule.note,
                    recurringId = rule.id,
                ),
            )
            created++
        }
        return created
    }

    companion object {
        /** Months past the current one for which generation is still allowed. */
        const val MAX_MONTHS_AHEAD = 1L
    }
}

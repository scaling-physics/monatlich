package com.monatlich.domain.usecase

import com.monatlich.domain.model.MonthTotal
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.model.sumIn
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.time.YearMonth
import javax.inject.Inject

/**
 * Builds the last [monthCount] months of base-currency spend/income totals, ending at
 * [endingAt] inclusive, and keeps them live.
 *
 * Each month's totals use its transactions' stored `rateToBase`, matching [GetMonthSummary]'s
 * spend total, so the trend chart and the per-month overview never disagree.
 */
class GetMonthlyTrend @Inject constructor(
    private val transactions: TransactionRepository,
    private val settings: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(monthCount: Int, endingAt: YearMonth): Flow<List<MonthTotal>> =
        settings.baseCurrency.flatMapLatest { base ->
            val months = (monthCount - 1).downTo(0).map { endingAt.minusMonths(it.toLong()) }
            val perMonth = months.map { month ->
                combine(
                    transactions.observeTotalsByCategoryInBase(month, TransactionType.EXPENSE, base),
                    transactions.observeTotalsByCategoryInBase(month, TransactionType.INCOME, base),
                ) { spent, income ->
                    MonthTotal(
                        month = month,
                        spentInBase = spent.values.sumIn(base),
                        incomeInBase = income.values.sumIn(base),
                    )
                }
            }
            combine(perMonth) { it.toList() }
        }.distinctUntilChanged()
}

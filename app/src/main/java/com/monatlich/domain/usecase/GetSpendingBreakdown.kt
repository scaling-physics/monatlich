package com.monatlich.domain.usecase

import com.monatlich.domain.model.CategorySpend
import com.monatlich.domain.model.SpendingBreakdown
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject

/**
 * Builds the base-currency expense-by-category [SpendingBreakdown] for [start]..[end] and keeps
 * it live. Backs the Overview chart, which reuses this for both its default (whole-month) view
 * and any custom date range the user picks.
 */
class GetSpendingBreakdown @Inject constructor(
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val settings: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(start: LocalDate, end: LocalDate): Flow<SpendingBreakdown> =
        settings.baseCurrency.flatMapLatest { base ->
            combine(
                categories.observeAll(),
                transactions.observeTotalsByCategoryInRange(start, end, TransactionType.EXPENSE, base),
            ) { allCategories, spentByCategory ->
                val rows = allCategories.mapNotNull { category ->
                    val spent = spentByCategory[category.id] ?: return@mapNotNull null
                    if (spent.amountMinor <= 0L) return@mapNotNull null
                    CategorySpend(category, spent)
                }.sortedByDescending { it.spentInBase.amountMinor }
                SpendingBreakdown(start, end, base, rows)
            }
        }.distinctUntilChanged()
}

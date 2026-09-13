package com.monatlich.domain.usecase

import com.monatlich.domain.model.Budget
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.CategorySummary
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.ExchangeRate
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.MonthSummary
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.model.sumIn
import com.monatlich.domain.repository.BudgetRepository
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.ExchangeRateRepository
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject

/**
 * Builds the base-currency [MonthSummary] for one month and keeps it live.
 *
 * - Spent / income totals use each transaction's stored `rateToBase`, so past months never move.
 * - Budgets entered in a non-base currency are converted with the *current* rate table (they are a
 *   plan, not history). A missing rate row is treated as `1`, matching the seeded placeholder.
 * - A category appears if it is active, or if it is archived but still has a budget or spending in
 *   this month; rows follow the category sort order.
 * - Emissions are de-duplicated: one write can invalidate several source queries at once.
 */
class GetMonthSummary @Inject constructor(
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val settings: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(month: YearMonth): Flow<MonthSummary> =
        settings.baseCurrency.flatMapLatest { base ->
            combine(
                categories.observeAll(),
                budgets.observeForMonth(month),
                exchangeRates.observeAll(),
                transactions.observeTotalsByCategoryInBase(month, TransactionType.EXPENSE, base),
                transactions.observeTotalsByCategoryInBase(month, TransactionType.INCOME, base),
            ) { allCategories, monthBudgets, rates, spent, income ->
                build(month, base, allCategories, monthBudgets, rates, spent, income)
            }
        }.distinctUntilChanged()

    private fun build(
        month: YearMonth,
        base: Currency,
        allCategories: List<Category>,
        monthBudgets: List<Budget>,
        rates: List<ExchangeRate>,
        spentByCategory: Map<Long, Money>,
        incomeByCategory: Map<Long, Money>,
    ): MonthSummary {
        val budgetByCategory = monthBudgets.associateBy { it.categoryId }
        val rateByCurrency = rates.associate { it.currency to it.rateToBase }

        val rows = allCategories.mapNotNull { category ->
            val budget = budgetByCategory[category.id]?.amount
            val spentInBase = spentByCategory[category.id]
            if (category.archived && budget == null && spentInBase == null) return@mapNotNull null

            val spent = spentInBase ?: Money.zero(base)
            val budgetInBase = budget?.toBase(base, rateByCurrency)
            CategorySummary(
                category = category,
                budget = budget,
                spentInBase = spent,
                remainingInBase = budgetInBase?.let { it - spent },
            )
        }

        return MonthSummary(
            month = month,
            totalBudgetInBase = rows.mapNotNull { it.budgetInBase }.sumIn(base),
            totalSpentInBase = spentByCategory.values.sumIn(base),
            totalIncomeInBase = incomeByCategory.values.sumIn(base),
            categories = rows,
        )
    }

    private fun Money.toBase(base: Currency, rateByCurrency: Map<Currency, BigDecimal>): Money =
        if (currency == base) this else convertTo(base, rateByCurrency[currency] ?: BigDecimal.ONE)
}

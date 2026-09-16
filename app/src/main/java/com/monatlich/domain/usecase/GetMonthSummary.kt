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
 * - A [Category.rolloverEnabled] category's available budget also includes every prior month's
 *   unspent budget (or overspend, if negative) — a running balance, not just this month's own
 *   allowance. See [carryInBase].
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
            val monthData = combine(
                categories.observeAll(),
                budgets.observeForMonth(month),
                exchangeRates.observeAll(),
                transactions.observeTotalsByCategoryInBase(month, TransactionType.EXPENSE, base),
                transactions.observeTotalsByCategoryInBase(month, TransactionType.INCOME, base),
            ) { allCategories, monthBudgets, rates, spent, income ->
                MonthData(allCategories, monthBudgets, rates, spent, income)
            }
            val rolloverData = combine(
                budgets.observeAll(),
                transactions.observeAllTotalsByCategoryAndMonthInBase(TransactionType.EXPENSE, base),
            ) { allBudgets, allSpent -> RolloverData(allBudgets, allSpent) }

            monthData.combine(rolloverData) { data, rollover -> build(month, base, data, rollover) }
        }.distinctUntilChanged()

    private fun build(month: YearMonth, base: Currency, data: MonthData, rollover: RolloverData): MonthSummary {
        val budgetByCategory = data.monthBudgets.associateBy { it.categoryId }
        val rateByCurrency = data.rates.associate { it.currency to it.rateToBase }

        val rows = data.allCategories.mapNotNull { category ->
            val budget = budgetByCategory[category.id]?.amount
            val spentInBase = data.spentByCategory[category.id]
            if (category.archived && budget == null && spentInBase == null) return@mapNotNull null

            val spent = spentInBase ?: Money.zero(base)
            val budgetInBaseThisMonth = budget?.toBase(base, rateByCurrency)
            val carry = if (category.rolloverEnabled) {
                carryInBase(category.id, month, base, rollover, rateByCurrency)
            } else {
                Money.zero(base)
            }
            val hasAvailable = budgetInBaseThisMonth != null || !carry.isZero
            val availableInBase = (budgetInBaseThisMonth ?: Money.zero(base)) + carry

            CategorySummary(
                category = category,
                budget = budget,
                spentInBase = spent,
                remainingInBase = if (hasAvailable) availableInBase - spent else null,
                carriedInBase = carry,
            )
        }

        return MonthSummary(
            month = month,
            totalBudgetInBase = rows.mapNotNull { it.budgetInBase }.sumIn(base),
            totalSpentInBase = data.spentByCategory.values.sumIn(base),
            totalIncomeInBase = data.incomeByCategory.values.sumIn(base),
            categories = rows,
        )
    }

    /**
     * Sums (budget − spent) in [base] over every month strictly before [beforeMonth] for
     * [categoryId] — the running balance that a rollover category carries into [beforeMonth].
     * Unbounded by design: once rollover is on, the whole history counts, not just since it was
     * switched on (nothing records *when* that happened).
     */
    private fun carryInBase(
        categoryId: Long,
        beforeMonth: YearMonth,
        base: Currency,
        rollover: RolloverData,
        rateByCurrency: Map<Currency, BigDecimal>,
    ): Money {
        val budgetsByMonth = rollover.allBudgets
            .filter { it.categoryId == categoryId && it.month < beforeMonth }
            .associate { it.month to it.amount.toBase(base, rateByCurrency) }
        val spentByMonth = rollover.allSpentByCategoryAndMonth[categoryId]
            .orEmpty()
            .filterKeys { it < beforeMonth }

        return (budgetsByMonth.keys + spentByMonth.keys).fold(Money.zero(base)) { total, month ->
            val budgetInBase = budgetsByMonth[month] ?: Money.zero(base)
            val spentInBase = spentByMonth[month] ?: Money.zero(base)
            total + (budgetInBase - spentInBase)
        }
    }

    private fun Money.toBase(base: Currency, rateByCurrency: Map<Currency, BigDecimal>): Money =
        if (currency == base) this else convertTo(base, rateByCurrency[currency] ?: BigDecimal.ONE)

    private data class MonthData(
        val allCategories: List<Category>,
        val monthBudgets: List<Budget>,
        val rates: List<ExchangeRate>,
        val spentByCategory: Map<Long, Money>,
        val incomeByCategory: Map<Long, Money>,
    )

    private data class RolloverData(
        val allBudgets: List<Budget>,
        val allSpentByCategoryAndMonth: Map<Long, Map<YearMonth, Money>>,
    )
}

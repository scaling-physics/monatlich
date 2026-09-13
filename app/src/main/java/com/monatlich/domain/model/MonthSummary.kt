package com.monatlich.domain.model

import java.time.YearMonth

/**
 * One row of the month overview. Everything ending in `InBase` is in the base currency; [budget]
 * is the budget exactly as the user entered it (possibly in another currency).
 *
 * [remainingInBase] is `null` when there is no budget for this category this month.
 */
data class CategorySummary(
    val category: Category,
    val budget: Money?,
    val spentInBase: Money,
    val remainingInBase: Money?,
) {
    /** The budget converted to the base currency, or `null` when there is no budget. */
    val budgetInBase: Money? get() = remainingInBase?.let { it + spentInBase }

    val isOverBudget: Boolean get() = remainingInBase?.isNegative == true

    /** Fraction of the budget spent, clamped to `[0, 1]`; `0` when there is no budget. */
    val progress: Float
        get() {
            val budget = budgetInBase ?: return 0f
            if (budget.amountMinor <= 0L) return if (spentInBase.amountMinor > 0L) 1f else 0f
            return (spentInBase.amountMinor.toDouble() / budget.amountMinor.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        }
}

/** The complete, base-currency view of one month that the overview screen renders. */
data class MonthSummary(
    val month: YearMonth,
    val totalBudgetInBase: Money,
    val totalSpentInBase: Money,
    val totalIncomeInBase: Money,
    val categories: List<CategorySummary>,
) {
    val baseCurrency: Currency get() = totalSpentInBase.currency
    val totalRemainingInBase: Money get() = totalBudgetInBase - totalSpentInBase
    val netInBase: Money get() = totalIncomeInBase - totalSpentInBase
}

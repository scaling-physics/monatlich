package com.monatlich.domain.model

import java.time.LocalDate

/** One category's expense total within a [SpendingBreakdown]'s date range. */
data class CategorySpend(
    val category: Category,
    val spentInBase: Money,
)

/**
 * Expense-by-category totals over an arbitrary inclusive date range, in the base currency.
 * [categories] contains only categories with spending in the range, sorted by amount descending
 * — backs the Overview chart, which follows the selected month by default but can be widened to
 * a custom range.
 */
data class SpendingBreakdown(
    val start: LocalDate,
    val end: LocalDate,
    val baseCurrency: Currency,
    val categories: List<CategorySpend>,
) {
    val totalSpentInBase: Money get() = categories.map { it.spentInBase }.sumIn(baseCurrency)
}

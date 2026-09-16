package com.monatlich.ui.overview

import androidx.compose.runtime.Immutable
import java.time.YearMonth

/**
 * Single immutable UI state for the Overview (home) screen.
 *
 * Amounts are minor units in [currencyCode] (the base currency). [categories] are derived from
 * `GetMonthSummary`; [isLoading] is `true` only until the first summary arrives. A month switch
 * emits a new state only once that month's data is in, so [month] and [categories] always agree.
 */
@Immutable
data class OverviewUiState(
    val month: YearMonth,
    val isCurrentMonth: Boolean,
    val currencyCode: String,
    val totalSpentMinor: Long,
    val totalBudgetMinor: Long,
    val totalIncomeMinor: Long,
    val categories: List<CategoryRowUiState>,
    val isLoading: Boolean = false,
    val isAddSheetVisible: Boolean = false,
    /** Category whose budget sheet is open, or `null` when no sheet is shown. */
    val selectedCategoryId: Long? = null,
    /** `true` when this month has no budgets but the previous month has at least one. */
    val showCopyPrompt: Boolean = false,
) {
    val totalRemainingMinor: Long get() = totalBudgetMinor - totalSpentMinor
    val totalProgress: Float get() = progressOf(totalSpentMinor, totalBudgetMinor)
    val isOverBudget: Boolean get() = totalSpentMinor > totalBudgetMinor
    val hasAnyBudget: Boolean get() = categories.any { it.hasBudget }
    val netMinor: Long get() = totalIncomeMinor - totalSpentMinor
    val hasIncome: Boolean get() = totalIncomeMinor != 0L
}

@Immutable
data class CategoryRowUiState(
    val id: Long,
    val name: String,
    /** Material Icons name; resolve with `categoryIcon()`. */
    val icon: String,
    /** ARGB colour; resolve with `categoryColor()`. */
    val color: Long,
    val spentMinor: Long,
    /** Budget in the base currency; `0` when [hasBudget] is `false`. Already includes [carriedMinor]. */
    val budgetMinor: Long,
    val hasBudget: Boolean,
    /** Prior months' unspent budget (or overspend, if negative) rolled in; `0` unless enabled. */
    val carriedMinor: Long = 0L,
) {
    val remainingMinor: Long get() = budgetMinor - spentMinor
    val progress: Float get() = if (hasBudget) progressOf(spentMinor, budgetMinor) else 0f
    val isOverBudget: Boolean get() = hasBudget && spentMinor > budgetMinor
}

sealed interface OverviewEvent {
    data object PreviousMonth : OverviewEvent
    data object NextMonth : OverviewEvent
    data object JumpToCurrentMonth : OverviewEvent
    data object AddExpenseClicked : OverviewEvent
    data object AddSheetDismissed : OverviewEvent
    data class CategoryClicked(val categoryId: Long) : OverviewEvent
    data object CategorySheetDismissed : OverviewEvent
    data object CopyPromptDismissed : OverviewEvent
}

private fun progressOf(spent: Long, budget: Long): Float = when {
    budget <= 0L -> if (spent > 0L) 1f else 0f
    else -> (spent.toDouble() / budget.toDouble()).toFloat().coerceIn(0f, 1f)
}

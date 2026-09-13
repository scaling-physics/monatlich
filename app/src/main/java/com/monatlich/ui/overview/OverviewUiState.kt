package com.monatlich.ui.overview

import androidx.compose.runtime.Immutable
import java.time.YearMonth

/**
 * Single immutable UI state for the Overview (home) screen.
 *
 * Amounts are minor units in [currencyCode] (the base currency). M3 replaces the placeholder
 * [categories] with rows derived from the repository; the shape is meant to survive that swap.
 */
@Immutable
data class OverviewUiState(
    val month: YearMonth,
    val isCurrentMonth: Boolean,
    val currencyCode: String,
    val totalSpentMinor: Long,
    val totalBudgetMinor: Long,
    val categories: List<CategoryRowUiState>,
    val isAddSheetVisible: Boolean = false,
) {
    val totalRemainingMinor: Long get() = totalBudgetMinor - totalSpentMinor
    val totalProgress: Float get() = progressOf(totalSpentMinor, totalBudgetMinor)
    val isOverBudget: Boolean get() = totalSpentMinor > totalBudgetMinor
}

@Immutable
data class CategoryRowUiState(
    val id: Long,
    val name: String,
    val spentMinor: Long,
    val budgetMinor: Long,
) {
    val remainingMinor: Long get() = budgetMinor - spentMinor
    val progress: Float get() = progressOf(spentMinor, budgetMinor)
    val isOverBudget: Boolean get() = spentMinor > budgetMinor
}

sealed interface OverviewEvent {
    data object PreviousMonth : OverviewEvent
    data object NextMonth : OverviewEvent
    data object JumpToCurrentMonth : OverviewEvent
    data object AddExpenseClicked : OverviewEvent
    data object AddSheetDismissed : OverviewEvent
}

private fun progressOf(spent: Long, budget: Long): Float = when {
    budget <= 0L -> if (spent > 0L) 1f else 0f
    else -> (spent.toDouble() / budget.toDouble()).toFloat().coerceIn(0f, 1f)
}

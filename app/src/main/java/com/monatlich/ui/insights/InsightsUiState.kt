package com.monatlich.ui.insights

import java.time.YearMonth

/** One wedge of the "spend by category" donut for the current month. */
data class CategorySliceUiState(
    val categoryId: Long,
    val name: String,
    val color: Long,
    val spentMinor: Long,
    /** Share of this month's total spend, `0f..1f`. */
    val fraction: Float,
)

/** One bar of the month-over-month trend chart. */
data class TrendPointUiState(
    val month: YearMonth,
    val label: String,
    val spentMinor: Long,
    val incomeMinor: Long,
)

data class InsightsUiState(
    val month: YearMonth = YearMonth.now(),
    val currencyCode: String = "EUR",
    val isLoading: Boolean = true,
    val categorySlices: List<CategorySliceUiState> = emptyList(),
    val totalSpentMinor: Long = 0L,
    val trend: List<TrendPointUiState> = emptyList(),
) {
    val hasSpending: Boolean get() = categorySlices.isNotEmpty()
    val trendMaxMinor: Long get() = trend.maxOfOrNull { maxOf(it.spentMinor, it.incomeMinor) } ?: 0L
}

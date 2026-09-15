package com.monatlich.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.model.MonthSummary
import com.monatlich.domain.model.MonthTotal
import com.monatlich.domain.usecase.GetMonthSummary
import com.monatlich.domain.usecase.GetMonthlyTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * Backs the "Insights" drill-down: spend-by-category for the current month (a donut) and the
 * last [TREND_MONTHS] months of spend vs. income (a bar chart). Both are live, like the Overview.
 */
@HiltViewModel
class InsightsViewModel @Inject internal constructor(
    getMonthSummary: GetMonthSummary,
    getMonthlyTrend: GetMonthlyTrend,
    clock: Clock,
) : ViewModel() {

    private val month = YearMonth.now(clock)

    val uiState: StateFlow<InsightsUiState> = combine(
        getMonthSummary(month),
        getMonthlyTrend(TREND_MONTHS, month),
    ) { summary, trend -> buildState(summary, trend) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = InsightsUiState(month = month),
        )

    private fun buildState(summary: MonthSummary, trend: List<MonthTotal>): InsightsUiState {
        val spending = summary.categories
            .filter { it.spentInBase.amountMinor > 0L }
            .sortedByDescending { it.spentInBase.amountMinor }
        val totalMinor = spending.sumOf { it.spentInBase.amountMinor }

        val slices = spending.map { row ->
            CategorySliceUiState(
                categoryId = row.category.id,
                name = row.category.name,
                color = row.category.color,
                spentMinor = row.spentInBase.amountMinor,
                fraction = if (totalMinor <= 0L) 0f else row.spentInBase.amountMinor.toFloat() / totalMinor.toFloat(),
            )
        }

        return InsightsUiState(
            month = summary.month,
            currencyCode = summary.baseCurrency.code,
            isLoading = false,
            categorySlices = slices,
            totalSpentMinor = totalMinor,
            trend = trend.map {
                TrendPointUiState(
                    month = it.month,
                    label = it.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    spentMinor = it.spentInBase.amountMinor,
                    incomeMinor = it.incomeInBase.amountMinor,
                )
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TREND_MONTHS = 6
    }
}

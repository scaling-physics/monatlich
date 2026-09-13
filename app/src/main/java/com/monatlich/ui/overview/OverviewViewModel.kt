package com.monatlich.ui.overview

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * Owns the selected month and the add-sheet visibility for the Overview screen.
 *
 * M1: category rows and totals are deterministic placeholders generated per month so the bars
 * visibly re-animate when the month changes. M3 swaps [placeholderCategories] for a repository
 * `Flow` keyed by [OverviewUiState.month].
 */
@HiltViewModel
class OverviewViewModel internal constructor(
    private val clock: Clock,
) : ViewModel() {

    @Inject
    constructor() : this(Clock.systemDefaultZone())

    private val _uiState = MutableStateFlow(buildState(YearMonth.now(clock)))
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    fun onEvent(event: OverviewEvent) {
        when (event) {
            OverviewEvent.PreviousMonth -> selectMonth(_uiState.value.month.minusMonths(1))
            OverviewEvent.NextMonth -> selectMonth(_uiState.value.month.plusMonths(1))
            OverviewEvent.JumpToCurrentMonth -> selectMonth(YearMonth.now(clock))
            OverviewEvent.AddExpenseClicked -> _uiState.update { it.copy(isAddSheetVisible = true) }
            OverviewEvent.AddSheetDismissed -> _uiState.update { it.copy(isAddSheetVisible = false) }
        }
    }

    private fun selectMonth(month: YearMonth) {
        _uiState.update { current ->
            buildState(month).copy(isAddSheetVisible = current.isAddSheetVisible)
        }
    }

    private fun buildState(month: YearMonth): OverviewUiState {
        val categories = placeholderCategories(month)
        return OverviewUiState(
            month = month,
            isCurrentMonth = month == YearMonth.now(clock),
            currencyCode = PLACEHOLDER_CURRENCY,
            totalSpentMinor = categories.sumOf { it.spentMinor },
            totalBudgetMinor = categories.sumOf { it.budgetMinor },
            categories = categories,
        )
    }

    private companion object {
        const val PLACEHOLDER_CURRENCY = "EUR"

        /** name to budget in minor units. */
        val PLACEHOLDER_BUDGETS = listOf(
            "Groceries" to 45_000L,
            "Rent" to 120_000L,
            "Transport" to 9_000L,
            "Eating out" to 15_000L,
            "Fun" to 10_000L,
        )

        /** Deterministic per-month spend so every month looks different but stable. */
        fun placeholderCategories(month: YearMonth): List<CategoryRowUiState> {
            val seed = month.year * 12 + month.monthValue
            return PLACEHOLDER_BUDGETS.mapIndexed { index, (name, budget) ->
                // 35 %..115 % of budget, varying per category and month.
                val percent = 35 + ((seed * 37 + index * 23) % 81)
                CategoryRowUiState(
                    id = index.toLong() + 1,
                    name = name,
                    spentMinor = budget * percent / 100,
                    budgetMinor = budget,
                )
            }
        }
    }
}

package com.monatlich.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.MonthSummary
import com.monatlich.domain.repository.BudgetRepository
import com.monatlich.domain.usecase.GetMonthSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * Owns the selected month for the Overview screen and keeps its summary live.
 *
 * The month is a `MutableStateFlow`; every change swaps the underlying [GetMonthSummary] flow via
 * `flatMapLatest`, so a write in any repository re-renders the visible month only. Transient UI
 * flags (add sheet, selected category, dismissed copy prompts) live in [Flags] and are combined
 * with the data so the screen sees exactly one [OverviewUiState].
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel @Inject internal constructor(
    private val getMonthSummary: GetMonthSummary,
    private val budgets: BudgetRepository,
    private val clock: Clock,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now(clock))
    private val flags = MutableStateFlow(Flags())

    /**
     * Everything the screen needs for one month, emitted only once that month's data has arrived.
     * Keying the summary and the copy-prompt flag by month means a month switch never renders the
     * new label over the previous month's rows or prompt; label and content move together.
     */
    private val monthData: Flow<MonthData> = selectedMonth.flatMapLatest { month ->
        combine(
            getMonthSummary(month),
            budgets.observeForMonth(month),
            budgets.observeForMonth(month.minusMonths(1)),
        ) { summary, current, previous ->
            MonthData(
                month = month,
                summary = summary,
                canCopyFromPreviousMonth = current.isEmpty() && previous.isNotEmpty(),
            )
        }
    }

    val uiState: StateFlow<OverviewUiState> = combine(monthData, flags) { data, flags ->
        buildState(data.month, data.summary, data.canCopyFromPreviousMonth, flags)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = buildState(selectedMonth.value, summary = null, copyable = false, flags = flags.value),
    )

    fun onEvent(event: OverviewEvent) {
        when (event) {
            OverviewEvent.PreviousMonth -> selectMonth(selectedMonth.value.minusMonths(1))
            OverviewEvent.NextMonth -> selectMonth(selectedMonth.value.plusMonths(1))
            OverviewEvent.JumpToCurrentMonth -> selectMonth(YearMonth.now(clock))
            OverviewEvent.AddExpenseClicked -> flags.update { it.copy(isAddSheetVisible = true) }
            OverviewEvent.AddSheetDismissed -> flags.update { it.copy(isAddSheetVisible = false) }
            is OverviewEvent.CategoryClicked -> flags.update { it.copy(selectedCategoryId = event.categoryId) }
            OverviewEvent.CategorySheetDismissed -> flags.update { it.copy(selectedCategoryId = null) }
            OverviewEvent.CopyPromptDismissed -> flags.update {
                it.copy(dismissedCopyPrompts = it.dismissedCopyPrompts + selectedMonth.value)
            }
        }
    }

    private fun selectMonth(month: YearMonth) {
        // A budget sheet belongs to the month it was opened in; close it when the month changes.
        flags.update { it.copy(selectedCategoryId = null) }
        selectedMonth.value = month
    }

    private fun buildState(
        month: YearMonth,
        summary: MonthSummary?,
        copyable: Boolean,
        flags: Flags,
    ): OverviewUiState {
        val categories = summary?.categories.orEmpty().map { row ->
            CategoryRowUiState(
                id = row.category.id,
                name = row.category.name,
                icon = row.category.icon,
                color = row.category.color,
                spentMinor = row.spentInBase.amountMinor,
                budgetMinor = row.budgetInBase?.amountMinor ?: 0L,
                hasBudget = row.budgetInBase != null,
            )
        }
        return OverviewUiState(
            month = month,
            isCurrentMonth = month == YearMonth.now(clock),
            currencyCode = (summary?.baseCurrency ?: DEFAULT_CURRENCY).code,
            totalSpentMinor = summary?.totalSpentInBase?.amountMinor ?: 0L,
            totalBudgetMinor = summary?.totalBudgetInBase?.amountMinor ?: 0L,
            categories = categories,
            isLoading = summary == null,
            isAddSheetVisible = flags.isAddSheetVisible,
            selectedCategoryId = flags.selectedCategoryId,
            showCopyPrompt = copyable && month !in flags.dismissedCopyPrompts,
        )
    }

    private data class MonthData(
        val month: YearMonth,
        val summary: MonthSummary,
        val canCopyFromPreviousMonth: Boolean,
    )

    private data class Flags(
        val isAddSheetVisible: Boolean = false,
        val selectedCategoryId: Long? = null,
        val dismissedCopyPrompts: Set<YearMonth> = emptySet(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        /** Shown until the first summary reveals the real base currency; matches the settings default. */
        val DEFAULT_CURRENCY = Currency.EUR
    }
}

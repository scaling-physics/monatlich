package com.monatlich.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.MonthSummary
import com.monatlich.domain.model.SpendingBreakdown
import com.monatlich.domain.repository.BudgetRepository
import com.monatlich.domain.usecase.GetMonthSummary
import com.monatlich.domain.usecase.GetSpendingBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    private val getSpendingBreakdown: GetSpendingBreakdown,
    private val budgets: BudgetRepository,
    private val clock: Clock,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now(clock))
    private val flags = MutableStateFlow(Flags())
    private val chartType = MutableStateFlow(ChartType.PIE)
    /** `null` = chart follows [selectedMonth]; non-null = an explicit range overrides it. */
    private val customRange = MutableStateFlow<ClosedRange<LocalDate>?>(null)

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

    /**
     * `null` while the chart follows the selected month; once a custom range is picked, resolves
     * its [SpendingBreakdown] independently of month navigation — so switching months with a
     * custom range active never touches this pipeline, and vice versa. Both halves of a range
     * ([ChartOverride.range] and its data) always come from the same emission, so they can never
     * show mismatched dates.
     */
    private val chartOverride: Flow<ChartOverride> = customRange.flatMapLatest { range ->
        if (range == null) {
            flowOf(ChartOverride(range = null, breakdown = null))
        } else {
            getSpendingBreakdown(range.start, range.endInclusive).map { ChartOverride(range, it) }
        }
    }

    val uiState: StateFlow<OverviewUiState> = combine(monthData, chartOverride, chartType, flags) { data, override, type, flags ->
        buildState(data.month, data.summary, data.canCopyFromPreviousMonth, flags, type, override)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = buildState(
            month = selectedMonth.value,
            summary = null,
            copyable = false,
            flags = flags.value,
            type = chartType.value,
            override = ChartOverride(range = null, breakdown = null),
        ),
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
            is OverviewEvent.ChartTypeSelected -> chartType.value = event.type
            OverviewEvent.RangePickerRequested -> flags.update { it.copy(isRangePickerVisible = true) }
            OverviewEvent.RangePickerDismissed -> flags.update { it.copy(isRangePickerVisible = false) }
            is OverviewEvent.CustomRangeSelected -> {
                customRange.value = event.start..event.end
                flags.update { it.copy(isRangePickerVisible = false) }
            }
            OverviewEvent.CustomRangeCleared -> customRange.value = null
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
        type: ChartType,
        override: ChartOverride,
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
                carriedMinor = row.carriedInBase.amountMinor,
            )
        }
        return OverviewUiState(
            month = month,
            isCurrentMonth = month == YearMonth.now(clock),
            currencyCode = (summary?.baseCurrency ?: DEFAULT_CURRENCY).code,
            totalSpentMinor = summary?.totalSpentInBase?.amountMinor ?: 0L,
            totalBudgetMinor = summary?.totalBudgetInBase?.amountMinor ?: 0L,
            totalIncomeMinor = summary?.totalIncomeInBase?.amountMinor ?: 0L,
            categories = categories,
            isLoading = summary == null,
            isAddSheetVisible = flags.isAddSheetVisible,
            selectedCategoryId = flags.selectedCategoryId,
            showCopyPrompt = copyable && month !in flags.dismissedCopyPrompts,
            chart = buildChartState(month, summary, type, override, flags.isRangePickerVisible),
        )
    }

    /**
     * By default (no custom range) the chart reuses [summary]'s own per-category spend — the same
     * data the budget list below it already has, so a month switch can never leave the chart out
     * of step with the list. A custom range instead draws from [override], resolved independently.
     */
    private fun buildChartState(
        month: YearMonth,
        summary: MonthSummary?,
        type: ChartType,
        override: ChartOverride,
        isRangePickerVisible: Boolean,
    ): CategoryChartUiState {
        val isCustomRange = override.range != null
        val isLoading = if (isCustomRange) override.breakdown == null else summary == null
        val currencyCode = if (isCustomRange) {
            override.breakdown?.baseCurrency ?: DEFAULT_CURRENCY
        } else {
            summary?.baseCurrency ?: DEFAULT_CURRENCY
        }

        val spendByCategory = (
            if (isCustomRange) {
                override.breakdown?.categories.orEmpty().map { it.category to it.spentInBase.amountMinor }
            } else {
                summary?.categories.orEmpty()
                    .filter { it.spentInBase.amountMinor > 0L }
                    .map { it.category to it.spentInBase.amountMinor }
            }
        ).sortedByDescending { it.second }

        val totalMinor = spendByCategory.sumOf { it.second }
        val slices = spendByCategory.map { (category, spentMinor) ->
            CategorySliceUiState(
                categoryId = category.id,
                name = category.name,
                color = category.color,
                spentMinor = spentMinor,
                fraction = if (totalMinor <= 0L) 0f else spentMinor.toFloat() / totalMinor.toFloat(),
            )
        }

        val rangeStart = override.range?.start ?: month.atDay(1)
        val rangeEnd = override.range?.endInclusive ?: month.atEndOfMonth()

        return CategoryChartUiState(
            type = type,
            isLoading = isLoading,
            isCustomRange = isCustomRange,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            rangeLabel = if (isCustomRange) rangeLabel(rangeStart, rangeEnd) else monthLabel(month),
            currencyCode = currencyCode.code,
            totalSpentMinor = totalMinor,
            slices = slices,
            isRangePickerVisible = isRangePickerVisible,
        )
    }

    private fun monthLabel(month: YearMonth): String =
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))

    private fun rangeLabel(start: LocalDate, end: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return "${start.format(formatter)} – ${end.format(formatter)}"
    }

    private data class MonthData(
        val month: YearMonth,
        val summary: MonthSummary,
        val canCopyFromPreviousMonth: Boolean,
    )

    /** `range`/`breakdown` are `null` together (no custom range) or non-null together (resolved). */
    private data class ChartOverride(
        val range: ClosedRange<LocalDate>?,
        val breakdown: SpendingBreakdown?,
    )

    private data class Flags(
        val isAddSheetVisible: Boolean = false,
        val selectedCategoryId: Long? = null,
        val dismissedCopyPrompts: Set<YearMonth> = emptySet(),
        val isRangePickerVisible: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        /** Shown until the first summary reveals the real base currency; matches the settings default. */
        val DEFAULT_CURRENCY = Currency.EUR
    }
}

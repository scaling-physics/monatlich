package com.monatlich.ui.budget

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.model.Money
import com.monatlich.domain.repository.BudgetRepository
import com.monatlich.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class CopyBudgetsUiState(
    val month: YearMonth = YearMonth.of(2000, 1),
    val previousMonth: YearMonth = month.minusMonths(1),
    val isLoading: Boolean = true,
    /** Number of budgets in [previousMonth]. */
    val count: Int = 0,
    /**
     * One sum per currency found in [previousMonth]'s budgets, base currency first, then by code.
     * Currencies are never converted here — a mixed month shows each total separately.
     */
    val totals: List<Money> = emptyList(),
    val isCopying: Boolean = false,
    /** Set once the copy has been persisted; the prompt dismisses itself on it. */
    val isDone: Boolean = false,
) {
    val canCopy: Boolean get() = !isLoading && !isCopying && count > 0
}

/**
 * Backs [CopyBudgetsPrompt]: previews last month's budgets and copies them into [load]'s month.
 * The instance is reused across months, so the prompt calls [load] each time it opens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CopyBudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val month = MutableStateFlow<YearMonth?>(null)
    private val progress = MutableStateFlow(Progress())

    private data class Progress(val isCopying: Boolean = false, val isDone: Boolean = false)

    private val preview = month.filterNotNull().flatMapLatest { target ->
        val previous = target.minusMonths(1)
        combine(budgets.observeForMonth(previous), settings.baseCurrency) { list, base ->
            val totals = list
                .groupBy { it.amount.currency }
                .map { (currency, group) -> Money(group.sumOf { it.amount.amountMinor }, currency) }
                .sortedWith(compareBy<Money> { it.currency != base }.thenBy { it.currency.code })
            CopyBudgetsUiState(
                month = target,
                previousMonth = previous,
                isLoading = false,
                count = list.size,
                totals = totals,
            )
        }
    }

    val uiState: StateFlow<CopyBudgetsUiState> = combine(preview, progress) { state, progress ->
        state.copy(isCopying = progress.isCopying, isDone = progress.isDone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CopyBudgetsUiState())

    /** Targets [month]; the preview is computed from the month before it. */
    fun load(month: YearMonth) {
        progress.value = Progress()
        this.month.value = month
    }

    fun copy() {
        val state = uiState.value
        if (!state.canCopy) return
        progress.update { it.copy(isCopying = true) }
        viewModelScope.launch {
            budgets.copy(from = state.previousMonth, to = state.month)
            progress.value = Progress(isCopying = false, isDone = true)
        }
    }
}

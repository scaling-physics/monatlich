package com.monatlich.ui.budget

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.repository.BudgetRepository
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class SetBudgetUiState(
    val categoryId: Long = 0L,
    val month: YearMonth = YearMonth.of(2000, 1),
    val isLoading: Boolean = true,
    val categoryName: String = "",
    /** Material Icons name from [com.monatlich.domain.model.Category.icon]; unresolved here. */
    val categoryIcon: String = "",
    /** ARGB color as a `Long`; `0` until loaded. */
    val categoryColor: Long = 0L,
    /** The budget already stored for this (category, month), if any. */
    val existingBudget: Money? = null,
    /** Raw text of the amount field (`""` when empty); the cursor always sits at its end. */
    val amountText: String = "",
    /** The typed amount in minor units of [currency]. */
    val amountMinor: Long = 0L,
    val currency: Currency = Currency.EUR,
    val isSaving: Boolean = false,
    /** Set once a save or removal has been persisted; the sheet dismisses itself on it. */
    val isDone: Boolean = false,
) {
    val hasExistingBudget: Boolean get() = existingBudget != null
    val canSave: Boolean get() = !isLoading && !isSaving && amountMinor > 0L
}

sealed interface SetBudgetEvent {
    /** The amount field's new text after a user edit. */
    data class AmountEdited(val text: String) : SetBudgetEvent
    data object AmountCleared : SetBudgetEvent
    data class CurrencySelected(val currency: Currency) : SetBudgetEvent
    data object Save : SetBudgetEvent
    data object Remove : SetBudgetEvent
}

/**
 * Backs [SetBudgetSheet]. One instance lives in the host screen's ViewModel store and is reused
 * across categories, so the sheet calls [load] every time it opens; [load] resets all state.
 */
@HiltViewModel
class SetBudgetViewModel @Inject constructor(
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetBudgetUiState())
    val uiState: StateFlow<SetBudgetUiState> = _uiState.asStateFlow()

    private var amount = AmountInputState(Currency.EUR)
    private var loadJob: Job? = null

    /** Loads category + existing budget for ([categoryId], [month]), discarding any previous input. */
    fun load(categoryId: Long, month: YearMonth) {
        loadJob?.cancel()
        amount = AmountInputState(_uiState.value.currency)
        _uiState.value = SetBudgetUiState(
            categoryId = categoryId,
            month = month,
            isLoading = true,
            currency = amount.currency,
        )
        loadJob = viewModelScope.launch {
            val base = settings.baseCurrency.first()
            val category = categories.get(categoryId)
            val existing = budgets.get(categoryId, month)
            when {
                existing != null -> amount = AmountInputState(existing.amount.currency)
                    .also { it.setMinorUnits(existing.amount.amountMinor) }
                // The field is focused before the load finishes; keep anything typed meanwhile.
                amount.isEmpty -> amount = AmountInputState(base)
                else -> amount.currency = base
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    categoryName = category?.name.orEmpty(),
                    categoryIcon = category?.icon.orEmpty(),
                    categoryColor = category?.color ?: 0L,
                    existingBudget = existing?.amount,
                ).withAmount()
            }
        }
    }

    fun onEvent(event: SetBudgetEvent) {
        when (event) {
            is SetBudgetEvent.AmountEdited -> {
                amount.applyEdit(event.text)
                _uiState.update { it.withAmount() }
            }
            SetBudgetEvent.AmountCleared -> {
                amount.clear()
                _uiState.update { it.withAmount() }
            }
            is SetBudgetEvent.CurrencySelected -> {
                amount.currency = event.currency
                _uiState.update { it.withAmount() }
            }
            SetBudgetEvent.Save -> save()
            SetBudgetEvent.Remove -> remove()
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            budgets.set(state.categoryId, state.month, amount.money)
            _uiState.update { it.copy(isSaving = false, isDone = true) }
        }
    }

    private fun remove() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || !state.hasExistingBudget) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            budgets.remove(state.categoryId, state.month)
            _uiState.update { it.copy(isSaving = false, isDone = true) }
        }
    }

    private fun SetBudgetUiState.withAmount(): SetBudgetUiState = copy(
        amountText = amount.text,
        amountMinor = amount.minorUnits,
        currency = amount.currency,
    )
}

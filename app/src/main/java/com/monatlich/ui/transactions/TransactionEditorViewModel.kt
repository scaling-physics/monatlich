package com.monatlich.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.ExchangeRateRepository
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.domain.repository.TransactionRepository
import com.monatlich.ui.budget.AmountInputState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Backs [TransactionEditorSheet]. One instance lives in the host screen's ViewModel store and is
 * reused across sheet opens, so callers invoke [loadNew] or [loadExisting] every time it opens.
 *
 * The rate stored on save is always the *current* table rate to base ([ExchangeRateRepository
 * .currentRateToBase]), matching how a fresh entry made right now would be stored — for an edit
 * this means correcting an old entry re-prices it at today's rate, which is the expected "I'm
 * fixing this now" behaviour.
 */
@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionEditorUiState())
    val uiState: StateFlow<TransactionEditorUiState> = _uiState.asStateFlow()

    private var amount = AmountInputState(_uiState.value.currency)
    private var loadJob: Job? = null

    /** Opens the sheet for a brand-new entry in [month]; today's date when [month] is the current one. */
    fun loadNew(month: YearMonth) {
        loadJob?.cancel()
        amount = AmountInputState(_uiState.value.currency)
        val defaultDate = if (month == YearMonth.now(clock)) LocalDate.now(clock) else month.atDay(1)
        _uiState.value = TransactionEditorUiState(
            id = null,
            month = month,
            isLoading = true,
            date = defaultDate,
            currency = amount.currency,
        )
        loadJob = viewModelScope.launch {
            val base = settings.baseCurrency.first()
            val activeCategories = categories.observeActive().first()
            if (amount.isEmpty) amount = AmountInputState(base) else amount.currency = base
            _uiState.update {
                it.copy(
                    isLoading = false,
                    categories = activeCategories,
                    categoryId = it.categoryId ?: activeCategories.firstOrNull()?.id,
                ).withAmount()
            }
        }
    }

    /** Opens the sheet to edit the existing transaction [id]. */
    fun loadExisting(id: Long) {
        loadJob?.cancel()
        _uiState.value = TransactionEditorUiState(id = id, isLoading = true)
        loadJob = viewModelScope.launch {
            val activeCategories = categories.observeActive().first()
            val existing = transactions.get(id)
            if (existing == null) {
                _uiState.update { it.copy(isLoading = false, isDone = true) }
                return@launch
            }
            amount = AmountInputState(existing.amount.currency).also { it.setMinorUnits(existing.amount.amountMinor) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    month = existing.month,
                    categories = activeCategories,
                    categoryId = existing.categoryId,
                    type = existing.type,
                    date = existing.date,
                    note = existing.note.orEmpty(),
                ).withAmount()
            }
        }
    }

    fun onEvent(event: TransactionEditorEvent) {
        when (event) {
            is TransactionEditorEvent.AmountEdited -> {
                amount.applyEdit(event.text)
                _uiState.update { it.withAmount() }
            }
            TransactionEditorEvent.AmountCleared -> {
                amount.clear()
                _uiState.update { it.withAmount() }
            }
            is TransactionEditorEvent.CurrencySelected -> {
                amount.currency = event.currency
                _uiState.update { it.withAmount() }
            }
            is TransactionEditorEvent.CategorySelected -> _uiState.update { it.copy(categoryId = event.id) }
            is TransactionEditorEvent.TypeSelected -> _uiState.update { it.copy(type = event.type) }
            is TransactionEditorEvent.DateSelected -> _uiState.update { it.copy(date = event.date) }
            is TransactionEditorEvent.NoteChanged -> _uiState.update { it.copy(note = event.note) }
            TransactionEditorEvent.Save -> save()
            TransactionEditorEvent.Delete -> delete()
        }
    }

    private fun save() {
        val state = _uiState.value
        val categoryId = state.categoryId
        if (!state.canSave || categoryId == null) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val base = settings.baseCurrency.first()
            val rate = exchangeRates.currentRateToBase(amount.currency, base) ?: BigDecimal.ONE
            val draft = Transaction(
                id = state.id ?: 0L,
                categoryId = categoryId,
                date = state.date,
                amount = Money(amount.minorUnits, amount.currency),
                rateToBase = rate,
                type = state.type,
                note = state.note.trim().ifEmpty { null },
            )
            if (state.id == null) transactions.add(draft) else transactions.update(draft)
            _uiState.update { it.copy(isSaving = false, isDone = true) }
        }
    }

    private fun delete() {
        val id = _uiState.value.id ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            transactions.delete(id)
            _uiState.update { it.copy(isSaving = false, isDone = true) }
        }
    }

    private fun TransactionEditorUiState.withAmount(): TransactionEditorUiState = copy(
        amountText = amount.text,
        amountMinor = amount.minorUnits,
        currency = amount.currency,
    )
}

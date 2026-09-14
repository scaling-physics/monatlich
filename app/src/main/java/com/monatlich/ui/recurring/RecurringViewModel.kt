package com.monatlich.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.di.ApplicationScope
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.RecurringRepository
import com.monatlich.domain.repository.SettingsRepository
import com.monatlich.domain.usecase.ApplyRecurringTransactions
import com.monatlich.ui.budget.AmountInputState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * Manages recurring rules and the create/edit sheet.
 *
 * The repository is the source of truth for the list. A swiped row is hidden ([RecurringUiState.pendingDeletion])
 * until the undo snackbar closes; if the screen is left in that window the delete is committed on
 * [applicationScope] so it cannot be lost. Saving or (re)activating a rule immediately runs
 * [ApplyRecurringTransactions] for the current month so the user sees the generated entry at once.
 */
@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val applyRecurring: ApplyRecurringTransactions,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = _uiState.asStateFlow()

    private var amount = AmountInputState(_uiState.value.editor?.currency ?: DEFAULT_CURRENCY)
    /** Domain rules as last emitted, so the editor can be pre-filled without another read. */
    private var rules: List<RecurringTransaction> = emptyList()
    private val hiddenRuleId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            combine(recurring.observeAll(), categories.observeAll(), hiddenRuleId) { all, cats, hidden ->
                Triple(all, cats, hidden)
            }.collect { (all, cats, hidden) ->
                rules = all
                val byId = cats.associateBy { it.id }
                _uiState.update { state ->
                    state.copy(
                        rules = all.filter { it.id != hidden }.map { it.toRow(byId[it.categoryId]) },
                        categories = cats.filter { !it.archived },
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onEvent(event: RecurringEvent) {
        when (event) {
            RecurringEvent.NewRuleClicked -> openNewEditor()
            is RecurringEvent.RuleClicked -> openEditorFor(event.id)
            RecurringEvent.EditorDismissed -> _uiState.update { it.copy(editor = null) }
            is RecurringEvent.AmountEdited -> {
                amount.applyEdit(event.text)
                updateEditor { it.withAmount() }
            }
            RecurringEvent.AmountCleared -> {
                amount.clear()
                updateEditor { it.withAmount() }
            }
            is RecurringEvent.CurrencySelected -> {
                amount.currency = event.currency
                updateEditor { it.withAmount() }
            }
            is RecurringEvent.CategorySelected -> updateEditor { it.copy(categoryId = event.id) }
            is RecurringEvent.TypeSelected -> updateEditor { it.copy(type = event.type) }
            is RecurringEvent.DaySelected -> updateEditor {
                it.copy(dayOfMonth = event.day.coerceIn(RecurringTransaction.MIN_DAY, RecurringTransaction.MAX_DAY))
            }
            is RecurringEvent.StartMonthChanged -> updateEditor {
                val end = it.endMonth?.takeUnless { end -> end.isBefore(event.month) }
                it.copy(startMonth = event.month, endMonth = end)
            }
            is RecurringEvent.EndMonthChanged -> updateEditor {
                it.copy(endMonth = event.month?.let { end -> maxOf(end, it.startMonth) })
            }
            is RecurringEvent.NoteChanged -> updateEditor { it.copy(note = event.note) }
            RecurringEvent.SaveClicked -> save()
            is RecurringEvent.ActiveToggled -> setActive(event.id, event.active)
            is RecurringEvent.Delete -> delete(event.id)
            RecurringEvent.UndoDelete -> undoDelete()
            RecurringEvent.DeleteConfirmed -> confirmDelete()
        }
    }

    private fun openNewEditor() {
        val state = _uiState.value
        viewModelScope.launch {
            val base = settings.baseCurrency.first()
            amount = AmountInputState(base)
            _uiState.update {
                it.copy(
                    editor = RecurringEditorState(
                        currency = base,
                        categoryId = state.categories.firstOrNull()?.id,
                        startMonth = YearMonth.now(clock),
                    ),
                )
            }
        }
    }

    private fun openEditorFor(id: Long) {
        val rule = rules.firstOrNull { it.id == id } ?: return
        amount = AmountInputState(rule.amount.currency).also { it.setMinorUnits(rule.amount.amountMinor) }
        _uiState.update {
            it.copy(
                editor = RecurringEditorState(
                    id = rule.id,
                    categoryId = rule.categoryId,
                    type = rule.type,
                    dayOfMonth = rule.dayOfMonth,
                    startMonth = rule.startMonth,
                    endMonth = rule.endMonth,
                    note = rule.note.orEmpty(),
                ).withAmount(),
            )
        }
    }

    private inline fun updateEditor(transform: (RecurringEditorState) -> RecurringEditorState) =
        _uiState.update { state -> state.copy(editor = state.editor?.let(transform)) }

    private fun save() {
        val editor = _uiState.value.editor ?: return
        val categoryId = editor.categoryId
        if (!editor.canSave || categoryId == null) return
        updateEditor { it.copy(isSaving = true) }
        viewModelScope.launch {
            val draft = RecurringTransaction(
                id = editor.id ?: 0L,
                categoryId = categoryId,
                amount = Money(editor.amountMinor, editor.currency),
                type = editor.type,
                note = editor.note.trim().ifEmpty { null },
                dayOfMonth = editor.dayOfMonth,
                startMonth = editor.startMonth,
                endMonth = editor.endMonth,
                active = editor.id?.let { id -> rules.firstOrNull { it.id == id }?.active } ?: true,
            )
            if (editor.id == null) recurring.add(draft) else recurring.update(draft)
            applyRecurring(YearMonth.now(clock))
            _uiState.update { it.copy(editor = null) }
        }
    }

    private fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch {
            recurring.setActive(id, active)
            if (active) applyRecurring(YearMonth.now(clock))
        }
    }

    private fun delete(id: Long) {
        val rule = rules.firstOrNull { it.id == id } ?: return
        // Only one undo window at a time: a second swipe commits the first deletion.
        confirmDelete()
        hiddenRuleId.value = id
        _uiState.update { it.copy(pendingDeletion = rule) }
    }

    private fun undoDelete() {
        hiddenRuleId.value = null
        _uiState.update { it.copy(pendingDeletion = null) }
    }

    private fun confirmDelete() {
        val rule = _uiState.value.pendingDeletion ?: return
        hiddenRuleId.value = null
        _uiState.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch { recurring.delete(rule.id) }
    }

    override fun onCleared() {
        // The undo snackbar dies with the screen; honour the swipe rather than silently keeping the rule.
        val rule = _uiState.value.pendingDeletion ?: return
        applicationScope.launch { recurring.delete(rule.id) }
    }

    private fun RecurringEditorState.withAmount(): RecurringEditorState = copy(
        amountText = amount.text,
        amountMinor = amount.minorUnits,
        currency = amount.currency,
    )

    private fun RecurringTransaction.toRow(category: Category?) = RecurringRuleUiState(
        id = id,
        categoryName = category?.name.orEmpty(),
        categoryIcon = category?.icon.orEmpty(),
        categoryColor = category?.color ?: 0L,
        amountMinor = amount.amountMinor,
        currencyCode = amount.currency.code,
        type = type,
        note = note,
        dayOfMonth = dayOfMonth,
        startMonth = startMonth,
        endMonth = endMonth,
        active = active,
    )

    private companion object {
        val DEFAULT_CURRENCY = Currency.EUR
    }
}

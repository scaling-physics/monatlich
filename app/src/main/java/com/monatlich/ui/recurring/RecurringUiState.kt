package com.monatlich.ui.recurring

import androidx.compose.runtime.Immutable
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.model.TransactionType
import java.time.YearMonth

/** Single immutable UI state for the recurring-transactions manager. */
@Immutable
data class RecurringUiState(
    /** Rules in display order; a rule pending deletion is already filtered out. */
    val rules: List<RecurringRuleUiState> = emptyList(),
    /** Non-archived categories offered by the editor's chip row. */
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    /** Non-null while the editor sheet is open. */
    val editor: RecurringEditorState? = null,
    /** Rule swiped away and hidden until the undo window closes; committed by [RecurringEvent.DeleteConfirmed]. */
    val pendingDeletion: RecurringTransaction? = null,
)

/** One list row: the rule joined with its category's name/badge. */
@Immutable
data class RecurringRuleUiState(
    val id: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
    val amountMinor: Long,
    val currencyCode: String,
    val type: TransactionType,
    val note: String?,
    val dayOfMonth: Int,
    val startMonth: YearMonth,
    val endMonth: YearMonth?,
    val active: Boolean,
)

/** Draft of the rule being created or edited in the bottom sheet. */
@Immutable
data class RecurringEditorState(
    /** `null` while creating a new rule. */
    val id: Long? = null,
    /** Raw text of the amount field (`""` when empty); the cursor always sits at its end. */
    val amountText: String = "",
    /** The typed amount in minor units of [currency]. */
    val amountMinor: Long = 0L,
    val currency: Currency = Currency.EUR,
    val categoryId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val dayOfMonth: Int = 1,
    val startMonth: YearMonth,
    val endMonth: YearMonth? = null,
    val note: String = "",
    val isSaving: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val canSave: Boolean get() = !isSaving && amountMinor > 0L && categoryId != null
}

sealed interface RecurringEvent {
    data object NewRuleClicked : RecurringEvent
    data class RuleClicked(val id: Long) : RecurringEvent
    data object EditorDismissed : RecurringEvent
    /** The amount field's new text after a user edit. */
    data class AmountEdited(val text: String) : RecurringEvent
    data object AmountCleared : RecurringEvent
    data class CurrencySelected(val currency: Currency) : RecurringEvent
    data class CategorySelected(val id: Long) : RecurringEvent
    data class TypeSelected(val type: TransactionType) : RecurringEvent
    data class DaySelected(val day: Int) : RecurringEvent
    data class StartMonthChanged(val month: YearMonth) : RecurringEvent
    /** `null` clears the end month (open-ended rule). */
    data class EndMonthChanged(val month: YearMonth?) : RecurringEvent
    data class NoteChanged(val note: String) : RecurringEvent
    data object SaveClicked : RecurringEvent
    data class ActiveToggled(val id: Long, val active: Boolean) : RecurringEvent
    /** Row swiped away: hides it and opens the undo window. */
    data class Delete(val id: Long) : RecurringEvent
    data object UndoDelete : RecurringEvent
    /** Undo window closed without undo: the pending rule is removed for good. */
    data object DeleteConfirmed : RecurringEvent
}

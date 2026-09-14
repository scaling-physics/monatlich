package com.monatlich.ui.transactions

import androidx.compose.runtime.Immutable
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Backs [TransactionEditorSheet]: adding a new expense/income (FAB on Overview or Transactions)
 * or editing an existing one (tap a row in the Transactions list). [id] is `null` for a new entry.
 */
@Immutable
data class TransactionEditorUiState(
    val id: Long? = null,
    val month: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
    val amountText: String = "",
    val amountMinor: Long = 0L,
    val currency: Currency = Currency.EUR,
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val type: TransactionType = TransactionType.EXPENSE,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val isSaving: Boolean = false,
    /** Set once a save or delete has been persisted; the sheet dismisses itself on it. */
    val isDone: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val canSave: Boolean get() = !isLoading && !isSaving && amountMinor > 0L && categoryId != null
}

sealed interface TransactionEditorEvent {
    data class AmountEdited(val text: String) : TransactionEditorEvent
    data object AmountCleared : TransactionEditorEvent
    data class CurrencySelected(val currency: Currency) : TransactionEditorEvent
    data class CategorySelected(val id: Long) : TransactionEditorEvent
    data class TypeSelected(val type: TransactionType) : TransactionEditorEvent
    data class DateSelected(val date: LocalDate) : TransactionEditorEvent
    data class NoteChanged(val note: String) : TransactionEditorEvent
    data object Save : TransactionEditorEvent
    data object Delete : TransactionEditorEvent
}

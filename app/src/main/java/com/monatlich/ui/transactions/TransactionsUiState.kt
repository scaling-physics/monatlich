package com.monatlich.ui.transactions

import androidx.compose.runtime.Immutable
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

/** One row of the Transactions list: a [Transaction] joined with its category for display. */
@Immutable
data class TransactionRowUiState(
    val id: Long,
    val date: LocalDate,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
    val amountMinor: Long,
    val currencyCode: String,
    val type: TransactionType,
    val note: String?,
)

/** A category as offered in the filter row; distinct from [com.monatlich.domain.model.Category] so archived categories that still have transactions this month can be offered too. */
@Immutable
data class CategoryFilterUiState(
    val id: Long,
    val name: String,
)

/**
 * Single immutable UI state for the Transactions tab: a month's entries, newest first, with a
 * text search and optional type / category filters applied. [rows] already reflects all three.
 */
@Immutable
data class TransactionsUiState(
    val month: YearMonth,
    val isCurrentMonth: Boolean,
    val isLoading: Boolean = true,
    val rows: List<TransactionRowUiState> = emptyList(),
    val availableCategories: List<CategoryFilterUiState> = emptyList(),
    val query: String = "",
    val typeFilter: TransactionType? = null,
    val categoryFilter: Long? = null,
    val editingTransactionId: Long? = null,
    val isAddSheetVisible: Boolean = false,
    /** Swiped-away row pending the undo snackbar; hidden from [rows] until confirmed or undone. */
    val pendingDeletion: TransactionRowUiState? = null,
) {
    val isEmpty: Boolean get() = !isLoading && rows.isEmpty()
    val hasNoResultsForFilter: Boolean get() = isEmpty && (query.isNotBlank() || typeFilter != null || categoryFilter != null)
    val isFiltering: Boolean get() = query.isNotBlank() || typeFilter != null || categoryFilter != null
}

sealed interface TransactionsEvent {
    data object PreviousMonth : TransactionsEvent
    data object NextMonth : TransactionsEvent
    data object JumpToCurrentMonth : TransactionsEvent
    data class QueryChanged(val query: String) : TransactionsEvent
    data class TypeFilterSelected(val type: TransactionType?) : TransactionsEvent
    data class CategoryFilterSelected(val categoryId: Long?) : TransactionsEvent
    data object AddClicked : TransactionsEvent
    data object AddSheetDismissed : TransactionsEvent
    data class RowClicked(val id: Long) : TransactionsEvent
    data object EditorDismissed : TransactionsEvent
    data class Delete(val id: Long) : TransactionsEvent
    data object UndoDelete : TransactionsEvent
    data object DeleteConfirmed : TransactionsEvent
}

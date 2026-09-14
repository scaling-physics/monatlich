package com.monatlich.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.di.ApplicationScope
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Transaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * Owns the selected month, search text and filters for the Transactions tab, and the add/edit
 * sheet and swipe-to-delete undo, mirroring [com.monatlich.ui.recurring.RecurringViewModel]'s
 * undo-window pattern: a swiped row is hidden until the snackbar closes, and a delete started
 * right before leaving the screen is still committed on [applicationScope].
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now(clock))
    private val queryFlow = MutableStateFlow("")
    private val typeFilterFlow = MutableStateFlow<TransactionType?>(null)
    private val categoryFilterFlow = MutableStateFlow<Long?>(null)
    private val editingTransactionId = MutableStateFlow<Long?>(null)
    private val isAddSheetVisible = MutableStateFlow(false)
    private val hiddenId = MutableStateFlow<Long?>(null)
    private val pendingDeletion = MutableStateFlow<TransactionRowUiState?>(null)

    private val filters: Flow<Filters> =
        combine(queryFlow, typeFilterFlow, categoryFilterFlow, ::Filters)

    /**
     * [filters] is folded into [MonthData] itself (rather than combined a second time alongside
     * it) so a query/filter change and the row list it produces always arrive in the same
     * emission — collecting the same cold [filters] flow twice would let the two collections
     * interleave independently and briefly pair a new filter with stale rows.
     */
    private val monthData: Flow<MonthData> = selectedMonth.flatMapLatest { month ->
        combine(transactions.observeForMonth(month), categories.observeAll(), filters) { txns, cats, f ->
            buildMonthData(month, txns, cats, f)
        }
    }

    private val flags: Flow<Flags> =
        combine(editingTransactionId, isAddSheetVisible, hiddenId, pendingDeletion, ::Flags)

    val uiState: StateFlow<TransactionsUiState> = combine(monthData, flags) { data, flags ->
        buildState(data, flags)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TransactionsUiState(month = selectedMonth.value, isCurrentMonth = true, isLoading = true),
    )

    fun onEvent(event: TransactionsEvent) {
        when (event) {
            TransactionsEvent.PreviousMonth -> selectMonth(selectedMonth.value.minusMonths(1))
            TransactionsEvent.NextMonth -> selectMonth(selectedMonth.value.plusMonths(1))
            TransactionsEvent.JumpToCurrentMonth -> selectMonth(YearMonth.now(clock))
            is TransactionsEvent.QueryChanged -> queryFlow.value = event.query
            is TransactionsEvent.TypeFilterSelected -> typeFilterFlow.value = event.type
            is TransactionsEvent.CategoryFilterSelected -> categoryFilterFlow.value = event.categoryId
            TransactionsEvent.AddClicked -> isAddSheetVisible.value = true
            TransactionsEvent.AddSheetDismissed -> isAddSheetVisible.value = false
            is TransactionsEvent.RowClicked -> editingTransactionId.value = event.id
            TransactionsEvent.EditorDismissed -> editingTransactionId.value = null
            is TransactionsEvent.Delete -> delete(event.id)
            TransactionsEvent.UndoDelete -> undoDelete()
            TransactionsEvent.DeleteConfirmed -> confirmDelete()
        }
    }

    private fun selectMonth(month: YearMonth) {
        selectedMonth.value = month
    }

    private fun delete(id: Long) {
        val row = uiState.value.rows.firstOrNull { it.id == id } ?: return
        // Only one undo window at a time: a second swipe commits the first deletion.
        confirmDelete()
        hiddenId.value = id
        pendingDeletion.value = row
    }

    private fun undoDelete() {
        hiddenId.value = null
        pendingDeletion.value = null
    }

    private fun confirmDelete() {
        val row = pendingDeletion.value ?: return
        hiddenId.value = null
        pendingDeletion.value = null
        viewModelScope.launch { transactions.delete(row.id) }
    }

    override fun onCleared() {
        // The undo snackbar dies with the screen; honour the swipe rather than silently keeping the entry.
        val row = pendingDeletion.value ?: return
        applicationScope.launch { transactions.delete(row.id) }
    }

    private fun buildMonthData(
        month: YearMonth,
        txns: List<Transaction>,
        cats: List<Category>,
        f: Filters,
    ): MonthData {
        val byId = cats.associateBy { it.id }
        val q = f.query.trim()
        val rows = txns
            .filter { f.type == null || it.type == f.type }
            .filter { f.categoryId == null || it.categoryId == f.categoryId }
            .map { txn -> txn.toRow(byId[txn.categoryId]) }
            .filter { row ->
                q.isBlank() ||
                    row.categoryName.contains(q, ignoreCase = true) ||
                    row.note?.contains(q, ignoreCase = true) == true
            }
        val categoriesInMonth = txns.map { it.categoryId }.toSet()
        val availableCategories = cats
            .filter { !it.archived || it.id in categoriesInMonth }
            .map { CategoryFilterUiState(it.id, it.name) }
        return MonthData(month, rows, availableCategories, f)
    }

    private fun buildState(data: MonthData, flags: Flags): TransactionsUiState = TransactionsUiState(
        month = data.month,
        isCurrentMonth = data.month == YearMonth.now(clock),
        isLoading = false,
        rows = data.rows.filter { it.id != flags.hidden },
        availableCategories = data.availableCategories,
        query = data.filters.query,
        typeFilter = data.filters.type,
        categoryFilter = data.filters.categoryId,
        editingTransactionId = flags.editing,
        isAddSheetVisible = flags.addVisible,
        pendingDeletion = flags.pending,
    )

    private fun Transaction.toRow(category: Category?) = TransactionRowUiState(
        id = id,
        date = date,
        categoryId = categoryId,
        categoryName = category?.name.orEmpty(),
        categoryIcon = category?.icon.orEmpty(),
        categoryColor = category?.color ?: 0L,
        amountMinor = amount.amountMinor,
        currencyCode = amount.currency.code,
        type = type,
        note = note,
    )

    private data class Filters(val query: String, val type: TransactionType?, val categoryId: Long?)

    private data class MonthData(
        val month: YearMonth,
        val rows: List<TransactionRowUiState>,
        val availableCategories: List<CategoryFilterUiState>,
        val filters: Filters,
    )

    private data class Flags(
        val editing: Long?,
        val addVisible: Boolean,
        val hidden: Long?,
        val pending: TransactionRowUiState?,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package com.monatlich.ui.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.domain.model.TransactionType
import com.monatlich.ui.common.CategoryBadge
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.Motion
import com.monatlich.ui.common.MotionTokens
import com.monatlich.ui.common.formatMinor
import com.monatlich.ui.theme.AmountTextStyle
import com.monatlich.ui.theme.MonatlichTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TRANSACTIONS_SCREEN_TAG = "transactions_screen"
const val TRANSACTIONS_LIST_TAG = "transactions_list"
const val TRANSACTIONS_FAB_TAG = "transactions_fab"
const val TRANSACTIONS_SEARCH_TAG = "transactions_search"
const val TRANSACTIONS_MONTH_LABEL_TAG = "transactions_month_label"
const val TRANSACTIONS_EMPTY_TAG = "transactions_empty"

/** Test tag of the row for the transaction with [id]. */
fun transactionRowTag(id: Long): String = "transaction_row_$id"

/** Test tag of the filter chip for category [id], or the "All" chip when `null`. */
fun transactionFilterChipTag(id: Long?): String = "transaction_filter_${id ?: "all"}"

/** Hilt entry point for the Transactions destination. */
@Composable
fun TransactionsRoute(
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionsScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    onEvent: (TransactionsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.pendingDeletion?.id) {
        if (state.pendingDeletion == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Transaction deleted",
            actionLabel = "Undo",
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
        onEvent(if (result == SnackbarResult.ActionPerformed) TransactionsEvent.UndoDelete else TransactionsEvent.DeleteConfirmed)
    }

    Scaffold(
        modifier = modifier.testTag(TRANSACTIONS_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MonthTopBar(
                month = state.month,
                isCurrentMonth = state.isCurrentMonth,
                onPrevious = { onEvent(TransactionsEvent.PreviousMonth) },
                onNext = { onEvent(TransactionsEvent.NextMonth) },
                onJumpToCurrent = { onEvent(TransactionsEvent.JumpToCurrentMonth) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(TransactionsEvent.AddClicked) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag(TRANSACTIONS_FAB_TAG),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchAndFilters(state = state, onEvent = onEvent)
            TransactionsList(state = state, onEvent = onEvent)
        }
    }

    if (state.isAddSheetVisible) {
        TransactionEditorSheet(
            month = state.month,
            transactionId = null,
            onDismiss = { onEvent(TransactionsEvent.AddSheetDismissed) },
        )
    }
    state.editingTransactionId?.let { id ->
        TransactionEditorSheet(
            month = state.month,
            transactionId = id,
            onDismiss = { onEvent(TransactionsEvent.EditorDismissed) },
        )
    }
}

// --- Top bar -------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthTopBar(
    month: YearMonth,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpToCurrent: () -> Unit,
) {
    val motion = LocalMotion.current
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                }
                AnimatedContent(
                    targetState = month,
                    transitionSpec = { monthSlide(motion, forward = targetState > initialState) },
                    label = "transactionsMonthLabel",
                ) { shownMonth ->
                    Text(
                        text = monthLabel(shownMonth),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(160.dp)
                            .testTag(TRANSACTIONS_MONTH_LABEL_TAG),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                }
            }
        },
        actions = {
            AnimatedVisibility(
                visible = !isCurrentMonth,
                enter = fadeIn(motion.feedback()) + scaleIn(motion.feedback(), initialScale = 0.8f),
                exit = fadeOut(motion.feedback()) + scaleOut(motion.feedback(), targetScale = 0.8f),
            ) {
                IconButton(onClick = onJumpToCurrent) {
                    Icon(Icons.Outlined.Today, contentDescription = "Jump to current month")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun monthLabel(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    return remember(month, formatter) { month.format(formatter) }
}

private fun monthSlide(motion: Motion, forward: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    val enter = slideInHorizontally(
        animationSpec = motion.tween(MotionTokens.LAYOUT_MS, easing = MotionTokens.Standard),
    ) { width -> sign * width / 3 } + fadeIn(motion.tween(MotionTokens.EMPHASIS_MS, delayMs = 60))
    val exit = slideOutHorizontally(
        animationSpec = motion.tween(MotionTokens.LAYOUT_MS, easing = MotionTokens.Standard),
    ) { width -> -sign * width / 3 } + fadeOut(motion.tween(MotionTokens.FEEDBACK_MS))
    return enter togetherWith exit
}

// --- Search + filters ----------------------------------------------------------------------------

@Composable
private fun SearchAndFilters(state: TransactionsUiState, onEvent: (TransactionsEvent) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(TransactionsEvent.QueryChanged(it)) },
            placeholder = { Text("Search category or note") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onEvent(TransactionsEvent.QueryChanged("")) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TRANSACTIONS_SEARCH_TAG),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = state.typeFilter == null,
                onClick = { onEvent(TransactionsEvent.TypeFilterSelected(null)) },
                label = { Text("All") },
            )
            FilterChip(
                selected = state.typeFilter == TransactionType.EXPENSE,
                onClick = { onEvent(TransactionsEvent.TypeFilterSelected(TransactionType.EXPENSE)) },
                label = { Text("Expenses") },
            )
            FilterChip(
                selected = state.typeFilter == TransactionType.INCOME,
                onClick = { onEvent(TransactionsEvent.TypeFilterSelected(TransactionType.INCOME)) },
                label = { Text("Income") },
            )
        }
        if (state.availableCategories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = state.categoryFilter == null,
                    onClick = { onEvent(TransactionsEvent.CategoryFilterSelected(null)) },
                    label = { Text("All categories") },
                    modifier = Modifier.testTag(transactionFilterChipTag(null)),
                )
                state.availableCategories.forEach { category ->
                    FilterChip(
                        selected = state.categoryFilter == category.id,
                        onClick = { onEvent(TransactionsEvent.CategoryFilterSelected(category.id)) },
                        label = { Text(category.name) },
                        modifier = Modifier.testTag(transactionFilterChipTag(category.id)),
                    )
                }
            }
        }
    }
}

// --- List ----------------------------------------------------------------------------------------

private sealed interface ListItem {
    data class Header(val date: LocalDate) : ListItem
    data class Row(val row: TransactionRowUiState) : ListItem
}

@Composable
private fun TransactionsList(state: TransactionsUiState, onEvent: (TransactionsEvent) -> Unit) {
    val motion = LocalMotion.current
    if (state.isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .testTag(TRANSACTIONS_EMPTY_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (state.hasNoResultsForFilter) "No transactions match" else "No transactions yet this month",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val items = remember(state.rows) {
        buildList {
            var lastDate: LocalDate? = null
            state.rows.forEach { row ->
                if (row.date != lastDate) {
                    add(ListItem.Header(row.date))
                    lastDate = row.date
                }
                add(ListItem.Row(row))
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag(TRANSACTIONS_LIST_TAG),
    ) {
        items(items, key = { if (it is ListItem.Header) "header-${it.date}" else "row-${(it as ListItem.Row).row.id}" }) { item ->
            when (item) {
                is ListItem.Header -> DateHeader(date = item.date, modifier = Modifier.animateItem())
                is ListItem.Row -> TransactionRow(
                    row = item.row,
                    onClick = { onEvent(TransactionsEvent.RowClicked(item.row.id)) },
                    onDelete = { onEvent(TransactionsEvent.Delete(item.row.id)) },
                    modifier = Modifier.animateItem(placementSpec = motion.layout()),
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, modifier: Modifier = Modifier) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH) }
    Text(
        text = date.format(formatter),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionRow(
    row: TransactionRowUiState,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        val description = "${row.categoryName}, ${formatMinor(row.amountMinor, row.currencyCode)}" +
            (row.note?.let { ", $it" } ?: "")
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(transactionRowTag(row.id))
                .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 10.dp),
            ) {
                CategoryBadge(icon = row.categoryIcon, color = row.categoryColor)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.categoryName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (!row.note.isNullOrBlank()) {
                        Text(
                            text = row.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = signedAmount(row.amountMinor, row.currencyCode, row.type),
                    style = AmountTextStyle,
                    color = if (row.type == TransactionType.INCOME) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

private fun signedAmount(amountMinor: Long, currencyCode: String, type: TransactionType): String {
    val formatted = formatMinor(amountMinor, currencyCode)
    return if (type == TransactionType.INCOME) "+$formatted" else formatted
}

// --- Previews ------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun TransactionsScreenPreview() {
    MonatlichTheme {
        TransactionsScreen(
            state = TransactionsUiState(
                month = YearMonth.of(2026, 9),
                isCurrentMonth = true,
                isLoading = false,
                rows = listOf(
                    TransactionRowUiState(
                        id = 1, date = LocalDate.of(2026, 9, 13), categoryId = 1,
                        categoryName = "Groceries", categoryIcon = "ShoppingCart", categoryColor = 0xFF2E7D32,
                        amountMinor = 4_250, currencyCode = "EUR", type = TransactionType.EXPENSE, note = "Weekly shop",
                    ),
                    TransactionRowUiState(
                        id = 2, date = LocalDate.of(2026, 9, 13), categoryId = 2,
                        categoryName = "Salary", categoryIcon = "Payments", categoryColor = 0xFF1B5E20,
                        amountMinor = 320_000, currencyCode = "EUR", type = TransactionType.INCOME, note = null,
                    ),
                    TransactionRowUiState(
                        id = 3, date = LocalDate.of(2026, 9, 12), categoryId = 3,
                        categoryName = "Transport", categoryIcon = "DirectionsCar", categoryColor = 0xFF6B1F2A,
                        amountMinor = 1_200, currencyCode = "EUR", type = TransactionType.EXPENSE, note = null,
                    ),
                ),
                availableCategories = listOf(
                    CategoryFilterUiState(1, "Groceries"),
                    CategoryFilterUiState(2, "Salary"),
                    CategoryFilterUiState(3, "Transport"),
                ),
            ),
            onEvent = {},
        )
    }
}

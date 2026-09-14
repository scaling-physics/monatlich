package com.monatlich.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.TransactionType
import com.monatlich.ui.common.CategoryBadge
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.formatMinor
import com.monatlich.ui.theme.AmountFontFeatures
import com.monatlich.ui.theme.MonatlichTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TRANSACTION_EDITOR_SHEET_TAG = "transaction_editor_sheet"
const val TRANSACTION_AMOUNT_TAG = "transaction_amount"
const val TRANSACTION_NOTE_TAG = "transaction_note"
const val TRANSACTION_SAVE_TAG = "transaction_save"
const val TRANSACTION_DELETE_TAG = "transaction_delete"
const val TRANSACTION_DATE_TAG = "transaction_date"

/** Test tag of the category chip for [id]. */
fun transactionCategoryChipTag(id: Long): String = "transaction_category_$id"

/**
 * Bottom sheet that adds a new expense/income or edits an existing one.
 *
 * Fits the `OverviewScreen.addTransactionSheet` slot and the Transactions screen's FAB / row tap.
 * The amount field is focused with the numeric keyboard up as soon as the sheet opens.
 */
@Composable
fun TransactionEditorSheet(
    month: YearMonth,
    transactionId: Long?,
    onDismiss: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(month, transactionId) {
        if (transactionId == null) viewModel.loadNew(month) else viewModel.loadExisting(transactionId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionEditorSheet(state = state, onEvent = viewModel::onEvent, onDismiss = onDismiss)
}

/** Stateless variant for previews and UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    state: TransactionEditorUiState,
    onEvent: (TransactionEditorEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hideThenDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    LaunchedEffect(state.isDone) { if (state.isDone) hideThenDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(TRANSACTION_EDITOR_SHEET_TAG),
    ) {
        TransactionEditorSheetContent(state = state, onEvent = onEvent)
    }
}

@Composable
private fun TransactionEditorSheetContent(
    state: TransactionEditorUiState,
    onEvent: (TransactionEditorEvent) -> Unit,
) {
    val motion = LocalMotion.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Text(
            text = if (state.isNew) "Add transaction" else "Edit transaction",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(20.dp))

        AmountField(
            text = state.amountText,
            currency = state.currency,
            onTextChange = { onEvent(TransactionEditorEvent.AmountEdited(it)) },
            onClear = { onEvent(TransactionEditorEvent.AmountCleared) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.amountMinor > 0L) formatMinor(state.amountMinor, state.currency.code) else " ",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = AmountFontFeatures),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Currency.entries.forEach { currency ->
                FilterChip(
                    selected = currency == state.currency,
                    onClick = { onEvent(TransactionEditorEvent.CurrencySelected(currency)) },
                    label = { Text("${currency.symbol} ${currency.code}", fontSize = 13.sp) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel("Type")
        TypeToggle(selected = state.type, onSelect = { onEvent(TransactionEditorEvent.TypeSelected(it)) })
        Spacer(Modifier.height(20.dp))

        SectionLabel("Category")
        CategoryChipRow(
            categories = state.categories,
            selectedId = state.categoryId,
            onSelect = { onEvent(TransactionEditorEvent.CategorySelected(it)) },
        )
        Spacer(Modifier.height(20.dp))

        SectionLabel("Date")
        DateRow(date = state.date, onDateSelected = { onEvent(TransactionEditorEvent.DateSelected(it)) })
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = state.note,
            onValueChange = { onEvent(TransactionEditorEvent.NoteChanged(it)) },
            label = { Text("Note") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TRANSACTION_NOTE_TAG),
        )
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AnimatedVisibility(
                visible = !state.isNew,
                enter = fadeIn(motion.feedback()) + expandHorizontally(motion.layout()),
                exit = fadeOut(motion.feedback()) + shrinkHorizontally(motion.layout()),
            ) {
                TextButton(
                    onClick = { onEvent(TransactionEditorEvent.Delete) },
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag(TRANSACTION_DELETE_TAG),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    keyboard?.hide()
                    onEvent(TransactionEditorEvent.Save)
                },
                enabled = state.canSave,
                modifier = Modifier.testTag(TRANSACTION_SAVE_TAG),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
    }
}

/**
 * Calculator-style amount field (same contract as the set-budget and recurring sheets): the text
 * always shows with the cursor at the end and each edit is replayed through `AmountInputState.applyEdit`.
 */
@Composable
private fun AmountField(
    text: String,
    currency: Currency,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val value = remember(text) { TextFieldValue(text, selection = TextRange(text.length)) }
    val motion = LocalMotion.current
    val amountStyle = MaterialTheme.typography.displaySmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = AmountFontFeatures,
        color = MaterialTheme.colorScheme.onSurface,
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = currency.symbol,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(text = "0", style = amountStyle, color = MaterialTheme.colorScheme.outline)
            }
            BasicTextField(
                value = value,
                onValueChange = { onTextChange(it.text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .testTag(TRANSACTION_AMOUNT_TAG),
                textStyle = amountStyle,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
        }
        AnimatedVisibility(
            visible = text.isNotEmpty(),
            enter = fadeIn(motion.feedback()),
            exit = fadeOut(motion.feedback()),
        ) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Outlined.Cancel,
                    contentDescription = "Clear amount",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(
        thickness = if (focused) 2.dp else 1.dp,
        color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun CategoryChipRow(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
                leadingIcon = { CategoryBadge(icon = category.icon, color = category.color, size = 22.dp) },
                modifier = Modifier.testTag(transactionCategoryChipTag(category.id)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeToggle(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val options = listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) },
            )
        }
    }
}

/** "Today" / "Yesterday" quick chips plus a calendar button for anything else — one tap covers the common case. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val yesterday = remember(today) { today.minusDays(1) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = date == today,
            onClick = { onDateSelected(today) },
            label = { Text("Today") },
        )
        FilterChip(
            selected = date == yesterday,
            onClick = { onDateSelected(yesterday) },
            label = { Text("Yesterday") },
        )
        FilterChip(
            selected = date != today && date != yesterday,
            onClick = { showPicker = true },
            label = { Text(dateLabel(date)) },
            leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.width(16.dp)) },
            modifier = Modifier.testTag(TRANSACTION_DATE_TAG),
        )
    }

    if (showPicker) {
        val initialMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun dateLabel(date: LocalDate): String {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH) }
    return remember(date) { date.format(formatter) }
}

// --- Previews ------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun TransactionEditorSheetContentPreview() {
    MonatlichTheme {
        TransactionEditorSheetContent(
            state = TransactionEditorUiState(
                month = YearMonth.of(2026, 9),
                isLoading = false,
                amountText = "24.50",
                amountMinor = 2_450,
                currency = Currency.EUR,
                categoryId = 1,
                categories = listOf(
                    Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32),
                    Category(id = 2, name = "Rent", icon = "Home", color = 0xFF6B1F2A),
                ),
                date = LocalDate.of(2026, 9, 13),
            ),
            onEvent = {},
        )
    }
}

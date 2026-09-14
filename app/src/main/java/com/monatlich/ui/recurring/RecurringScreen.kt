package com.monatlich.ui.recurring

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.R
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.model.TransactionType
import com.monatlich.ui.common.CategoryBadge
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.formatMinor
import com.monatlich.ui.theme.AmountFontFeatures
import com.monatlich.ui.theme.AmountTextStyle
import com.monatlich.ui.theme.MonatlichTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val RECURRING_SCREEN_TAG = "recurring_screen"
const val RECURRING_LIST_TAG = "recurring_list"
const val NEW_RECURRING_FAB_TAG = "new_recurring_fab"
const val RECURRING_SHEET_TAG = "recurring_sheet"
const val RECURRING_AMOUNT_TAG = "recurring_amount"
const val RECURRING_NOTE_TAG = "recurring_note"
const val RECURRING_SAVE_TAG = "recurring_save"

/** Test tag of the row for the rule with [id]. */
fun recurringRowTag(id: Long): String = "recurring_row_$id"

/** Test tag of the active switch in the row for the rule with [id]. */
fun recurringSwitchTag(id: Long): String = "recurring_switch_$id"

/** Test tag of the day-of-month cell for [day] in the editor. */
fun recurringDayTag(day: Int): String = "recurring_day_$day"

/** Hilt entry point for the recurring-transactions manager (drill-down from Settings). */
@Composable
fun RecurringRoute(
    onBack: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onEvent: (RecurringEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.recurring_deleted)
    val undoLabel = stringResource(R.string.recurring_undo)

    // One snackbar per pending deletion; leaving before it closes is handled by the ViewModel.
    LaunchedEffect(state.pendingDeletion?.id) {
        if (state.pendingDeletion == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
        onEvent(if (result == SnackbarResult.ActionPerformed) RecurringEvent.UndoDelete else RecurringEvent.DeleteConfirmed)
    }

    Scaffold(
        modifier = modifier.testTag(RECURRING_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_recurring_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recurring_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(RecurringEvent.NewRuleClicked) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag(NEW_RECURRING_FAB_TAG),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recurring_new))
            }
        },
    ) { innerPadding ->
        RecurringList(state = state, onEvent = onEvent, contentPadding = innerPadding)
    }

    state.editor?.let { editor ->
        RecurringEditorSheet(editor = editor, categories = state.categories, onEvent = onEvent)
    }
}

// --- List ----------------------------------------------------------------------------------------

@Composable
private fun RecurringList(
    state: RecurringUiState,
    onEvent: (RecurringEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val motion = LocalMotion.current
    if (!state.isLoading && state.rules.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.recurring_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            // Leave room for the FAB above the last row.
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        modifier = Modifier
            .fillMaxSize()
            .testTag(RECURRING_LIST_TAG),
    ) {
        items(state.rules, key = { it.id }) { rule ->
            RecurringRow(
                rule = rule,
                onClick = { onEvent(RecurringEvent.RuleClicked(rule.id)) },
                onActiveChange = { onEvent(RecurringEvent.ActiveToggled(rule.id, it)) },
                onDelete = { onEvent(RecurringEvent.Delete(rule.id)) },
                modifier = Modifier.animateItem(placementSpec = motion.layout()),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringRow(
    rule: RecurringRuleUiState,
    onClick: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val deleteLabel = stringResource(R.string.recurring_delete)
    val dismissState = rememberSwipeToDismissBoxState()
    val textColor by animateColorAsState(
        if (rule.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        motion.layout(),
        label = "rowText",
    )

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
                        text = deleteLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(recurringRowTag(rule.id)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            ) {
                CategoryBadge(icon = rule.categoryIcon, color = rule.categoryColor, muted = !rule.active)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.note?.takeIf { it.isNotBlank() } ?: rule.categoryName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                    )
                    if (!rule.note.isNullOrBlank()) {
                        Text(
                            text = rule.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = scheduleLabel(rule.dayOfMonth, rule.endMonth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = signedAmount(rule.amountMinor, rule.currencyCode, rule.type),
                    style = AmountTextStyle,
                    color = when {
                        !rule.active -> MaterialTheme.colorScheme.onSurfaceVariant
                        rule.type == TransactionType.INCOME -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                val switchLabel = stringResource(R.string.recurring_active_switch)
                Switch(
                    checked = rule.active,
                    onCheckedChange = onActiveChange,
                    modifier = Modifier
                        .testTag(recurringSwitchTag(rule.id))
                        .semantics { contentDescription = switchLabel },
                )
            }
        }
    }
}

@Composable
private fun scheduleLabel(dayOfMonth: Int, endMonth: YearMonth?): String {
    val every = stringResource(R.string.recurring_every_month_on, ordinal(dayOfMonth))
    return if (endMonth == null) every else "$every · ${stringResource(R.string.recurring_until, monthLabel(endMonth))}"
}

private fun signedAmount(amountMinor: Long, currencyCode: String, type: TransactionType): String {
    val formatted = formatMinor(amountMinor, currencyCode)
    return if (type == TransactionType.INCOME) "+$formatted" else formatted
}

/** English ordinal (`1st`, `2nd`, `3rd`, `4th`, `11th`, `21st`, …). The app is English-only for now. */
internal fun ordinal(day: Int): String {
    val suffix = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

@Composable
private fun monthLabel(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("LLL yyyy", locale) }
    return month.format(formatter)
}

// --- Editor sheet --------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditorSheet(
    editor: RecurringEditorState,
    categories: List<Category>,
    onEvent: (RecurringEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(
        onDismissRequest = { onEvent(RecurringEvent.EditorDismissed) },
        sheetState = sheetState,
        modifier = Modifier.testTag(RECURRING_SHEET_TAG),
    ) {
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
                text = stringResource(if (editor.isNew) R.string.recurring_new else R.string.recurring_edit),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(20.dp))

            AmountField(
                text = editor.amountText,
                currency = editor.currency,
                onTextChange = { onEvent(RecurringEvent.AmountEdited(it)) },
                onClear = { onEvent(RecurringEvent.AmountCleared) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (editor.amountMinor > 0L) formatMinor(editor.amountMinor, editor.currency.code) else " ",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = AmountFontFeatures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { currency ->
                    FilterChip(
                        selected = currency == editor.currency,
                        onClick = { onEvent(RecurringEvent.CurrencySelected(currency)) },
                        label = { Text("${currency.symbol} ${currency.code}", fontSize = 13.sp) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.recurring_category_label))
            CategoryChipRow(
                categories = categories,
                selectedId = editor.categoryId,
                onSelect = { onEvent(RecurringEvent.CategorySelected(it)) },
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.recurring_type_label))
            TypeToggle(selected = editor.type, onSelect = { onEvent(RecurringEvent.TypeSelected(it)) })
            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.recurring_day_label))
            DayOfMonthPicker(selected = editor.dayOfMonth, onSelect = { onEvent(RecurringEvent.DaySelected(it)) })
            AnimatedVisibility(visible = editor.dayOfMonth > 28) {
                Text(
                    text = stringResource(R.string.recurring_day_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            MonthStepper(
                label = stringResource(R.string.recurring_starts_label),
                month = editor.startMonth,
                onChange = { onEvent(RecurringEvent.StartMonthChanged(it)) },
            )
            Spacer(Modifier.height(12.dp))
            EndMonthRow(
                startMonth = editor.startMonth,
                endMonth = editor.endMonth,
                onChange = { onEvent(RecurringEvent.EndMonthChanged(it)) },
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = editor.note,
                onValueChange = { onEvent(RecurringEvent.NoteChanged(it)) },
                label = { Text(stringResource(R.string.recurring_note_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RECURRING_NOTE_TAG),
            )
            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEvent(RecurringEvent.EditorDismissed) }) {
                    Text(stringResource(R.string.recurring_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        keyboard?.hide()
                        onEvent(RecurringEvent.SaveClicked)
                    },
                    enabled = editor.canSave,
                    modifier = Modifier.testTag(RECURRING_SAVE_TAG),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recurring_save))
                }
            }
        }
    }
}

/**
 * Calculator-style amount field (same contract as the set-budget sheet): the text always shows
 * with the cursor at the end and each edit is replayed through `AmountInputState.applyEdit`.
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
                    .testTag(RECURRING_AMOUNT_TAG),
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
                    contentDescription = stringResource(R.string.recurring_clear_amount),
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
                modifier = Modifier.semantics { contentDescription = "category_${category.id}" },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeToggle(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val options = listOf(
        TransactionType.EXPENSE to stringResource(R.string.recurring_expense_label),
        TransactionType.INCOME to stringResource(R.string.recurring_income_label),
    )
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

private const val DAY_COLUMNS = 7

/** 1–31 laid out like a calendar page; the selected day is filled with the primary colour. */
@Composable
private fun DayOfMonthPicker(selected: Int, onSelect: (Int) -> Unit) {
    val motion = LocalMotion.current
    val days = (RecurringTransaction.MIN_DAY..RecurringTransaction.MAX_DAY).toList()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.chunked(DAY_COLUMNS).forEach { rowDays ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowDays.forEach { day ->
                    val isSelected = day == selected
                    val background by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        motion.feedback(),
                        label = "dayBg",
                    )
                    val tint by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        motion.feedback(),
                        label = "dayText",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(background)
                            .clickable { onSelect(day) }
                            .testTag(recurringDayTag(day)),
                    ) {
                        Text(
                            text = day.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = AmountFontFeatures),
                            color = tint,
                        )
                    }
                }
                repeat(DAY_COLUMNS - rowDays.size) { Spacer(Modifier.size(40.dp)) }
            }
        }
    }
}

@Composable
private fun MonthStepper(
    label: String,
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.recurring_previous_month))
        }
        Text(
            text = monthLabel(month),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(88.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.recurring_next_month))
        }
        trailing?.invoke()
    }
}

/** A freshly set end month defaults to one year of the rule (start + 11 months, inclusive). */
private const val DEFAULT_END_OFFSET_MONTHS = 11L

/** "No end date" as a tappable chip; once set, a stepper with a clear button, both animated in/out. */
@Composable
private fun EndMonthRow(
    startMonth: YearMonth,
    endMonth: YearMonth?,
    onChange: (YearMonth?) -> Unit,
) {
    val motion = LocalMotion.current
    Column {
        AnimatedVisibility(
            visible = endMonth == null,
            enter = fadeIn(motion.feedback()) + expandVertically(motion.layout()),
            exit = fadeOut(motion.feedback()) + shrinkVertically(motion.layout()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.recurring_ends_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = false,
                    onClick = { onChange(startMonth.plusMonths(DEFAULT_END_OFFSET_MONTHS)) },
                    label = { Text(stringResource(R.string.recurring_no_end)) },
                    modifier = Modifier.semantics { contentDescription = "set_end_month" },
                )
            }
        }
        AnimatedVisibility(
            visible = endMonth != null,
            enter = fadeIn(motion.feedback()) + expandVertically(motion.layout()),
            exit = fadeOut(motion.feedback()) + shrinkVertically(motion.layout()),
        ) {
            MonthStepper(
                label = stringResource(R.string.recurring_ends_label),
                month = endMonth ?: startMonth,
                onChange = { onChange(it) },
                trailing = {
                    IconButton(onClick = { onChange(null) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.recurring_clear_end),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

// --- Previews ------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun RecurringScreenPreview() {
    MonatlichTheme {
        RecurringScreen(
            state = RecurringUiState(
                rules = listOf(
                    RecurringRuleUiState(
                        id = 1, categoryName = "Rent", categoryIcon = "Home", categoryColor = 0xFF6B1F2A,
                        amountMinor = 120_000, currencyCode = "EUR", type = TransactionType.EXPENSE,
                        note = "Flat", dayOfMonth = 1, startMonth = YearMonth.of(2026, 9), endMonth = null, active = true,
                    ),
                    RecurringRuleUiState(
                        id = 2, categoryName = "Fun", categoryIcon = "Celebration", categoryColor = 0xFF7B1FA2,
                        amountMinor = 1_299, currencyCode = "USD", type = TransactionType.EXPENSE,
                        note = "Streaming", dayOfMonth = 15, startMonth = YearMonth.of(2026, 9),
                        endMonth = YearMonth.of(2027, 8), active = false,
                    ),
                ),
                isLoading = false,
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

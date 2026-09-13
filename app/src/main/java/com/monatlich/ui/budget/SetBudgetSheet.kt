package com.monatlich.ui.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.formatMinor
import com.monatlich.ui.theme.AmountFontFeatures
import com.monatlich.ui.theme.MonatlichTheme
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val SET_BUDGET_SHEET_TAG = "set_budget_sheet"
const val SET_BUDGET_AMOUNT_TAG = "set_budget_amount"
const val SET_BUDGET_SAVE_TAG = "set_budget_save"
const val SET_BUDGET_REMOVE_TAG = "set_budget_remove"

/**
 * Bottom sheet that sets, changes or removes the budget of one category for one month.
 *
 * Fits the `OverviewScreen.categorySheet` slot. The amount field is focused with the numeric
 * keyboard up as soon as the sheet opens; the sheet dismisses itself after a save or removal.
 */
@Composable
fun SetBudgetSheet(
    categoryId: Long,
    month: YearMonth,
    onDismiss: () -> Unit,
    viewModel: SetBudgetViewModel = hiltViewModel(),
) {
    LaunchedEffect(categoryId, month) { viewModel.load(categoryId, month) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetBudgetSheet(state = state, onEvent = viewModel::onEvent, onDismiss = onDismiss)
}

/** Stateless variant for previews and UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetBudgetSheet(
    state: SetBudgetUiState,
    onEvent: (SetBudgetEvent) -> Unit,
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
        modifier = Modifier.testTag(SET_BUDGET_SHEET_TAG),
    ) {
        SetBudgetSheetContent(state = state, onEvent = onEvent)
    }
}

@Composable
private fun SetBudgetSheetContent(
    state: SetBudgetUiState,
    onEvent: (SetBudgetEvent) -> Unit,
) {
    val motion = LocalMotion.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        CategoryHeader(
            name = state.categoryName,
            color = state.categoryColor,
            month = state.month,
        )
        Spacer(Modifier.height(24.dp))
        AmountField(
            text = state.amountText,
            currency = state.currency,
            onTextChange = { onEvent(SetBudgetEvent.AmountEdited(it)) },
            onClear = { onEvent(SetBudgetEvent.AmountCleared) },
            onDone = { onEvent(SetBudgetEvent.Save) },
        )
        Spacer(Modifier.height(8.dp))
        AmountCaption(state)
        Spacer(Modifier.height(16.dp))
        CurrencyChips(
            selected = state.currency,
            onSelect = { onEvent(SetBudgetEvent.CurrencySelected(it)) },
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = state.hasExistingBudget,
                enter = fadeIn(motion.feedback()) + expandHorizontally(motion.layout()),
                exit = fadeOut(motion.feedback()) + shrinkHorizontally(motion.layout()),
            ) {
                TextButton(
                    onClick = { onEvent(SetBudgetEvent.Remove) },
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag(SET_BUDGET_REMOVE_TAG),
                ) {
                    Text("Remove budget", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onEvent(SetBudgetEvent.Save) },
                enabled = state.canSave,
                modifier = Modifier.testTag(SET_BUDGET_SAVE_TAG),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String, color: Long, month: YearMonth) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val circle = if (color == 0L) MaterialTheme.colorScheme.surfaceVariant else Color(color)
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(circle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = name.ifEmpty { " " },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = "Budget · ${monthLabel(month)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Calculator-style amount field: the visible text is always [text] with the cursor at the end,
 * and every edit is reported as the resulting text so [AmountInputState.applyEdit] can replay it.
 */
@Composable
private fun AmountField(
    text: String,
    currency: Currency,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
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
                Text(
                    text = "0",
                    style = amountStyle,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { onTextChange(it.text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .testTag(SET_BUDGET_AMOUNT_TAG),
                textStyle = amountStyle,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
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
private fun AmountCaption(state: SetBudgetUiState) {
    val caption = when {
        state.amountMinor > 0L -> formatMinor(state.amountMinor, state.currency.code)
        state.existingBudget != null ->
            "Current budget ${formatMinor(state.existingBudget.amountMinor, state.existingBudget.currency.code)}"
        else -> "No budget set for this month"
    }
    Text(
        text = caption,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = AmountFontFeatures),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun CurrencyChips(selected: Currency, onSelect: (Currency) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Currency.entries.forEach { currency ->
            FilterChip(
                selected = currency == selected,
                onClick = { onSelect(currency) },
                label = { Text("${currency.symbol} ${currency.code}", fontSize = 13.sp) },
            )
        }
    }
}

@Composable
internal fun monthLabel(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    return month.format(formatter)
}

// --- Previews ----------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun SetBudgetSheetContentPreview() {
    MonatlichTheme {
        SetBudgetSheetContent(
            state = SetBudgetUiState(
                categoryId = 1,
                month = YearMonth.of(2026, 9),
                isLoading = false,
                categoryName = "Groceries",
                categoryColor = 0xFF2E7D32,
                existingBudget = Money(45_000, Currency.EUR),
                amountText = "450",
                amountMinor = 45_000,
                currency = Currency.EUR,
            ),
            onEvent = {},
        )
    }
}

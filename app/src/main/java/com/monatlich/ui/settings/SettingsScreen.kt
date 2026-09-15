package com.monatlich.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.R
import com.monatlich.domain.model.Currency
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.theme.AmountTextStyle
import com.monatlich.ui.theme.MonatlichTheme
import java.util.Locale

const val SETTINGS_SCREEN_TAG = "settings_screen"
const val BASE_CURRENCY_ROW_TAG = "settings_base_currency"
const val BASE_CURRENCY_DIALOG_TAG = "settings_base_currency_dialog"
const val RATE_INPUT_TAG = "settings_rate_input"
const val MANAGE_CATEGORIES_TAG = "settings_manage_categories"
const val MANAGE_RECURRING_TAG = "settings_manage_recurring"
const val MANAGE_DATA_TAG = "settings_manage_data"
const val OPEN_INSIGHTS_TAG = "settings_open_insights"

/** Test tag of the exchange-rate row for [code], e.g. `settings_rate_USD`. */
fun rateRowTag(code: String): String = "settings_rate_$code"

/**
 * Hilt entry point for the Settings destination. [onManageCategories] opens the category editor,
 * [onManageRecurring] the recurring-transactions manager.
 */
@Composable
fun SettingsRoute(
    onManageCategories: () -> Unit,
    onManageRecurring: () -> Unit,
    onManageData: () -> Unit,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onManageCategories = onManageCategories,
        onManageRecurring = onManageRecurring,
        onManageData = onManageData,
        onOpenInsights = onOpenInsights,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onManageCategories: () -> Unit,
    onManageRecurring: () -> Unit,
    onManageData: () -> Unit,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(SETTINGS_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "currency-header") { SectionHeader("Currency") }
            item(key = "base-currency") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Payments, contentDescription = null) },
                    title = "Base currency",
                    subtitle = if (state.isLoading) "Loading…" else state.baseCurrency.displayLabel(),
                    onClick = { onEvent(SettingsEvent.BaseCurrencyClicked) },
                    modifier = Modifier.testTag(BASE_CURRENCY_ROW_TAG),
                )
            }

            item(key = "rates-header") {
                SectionHeader(
                    title = "Exchange rates",
                    subtitle = "How much one unit of each currency is worth in ${state.baseCurrency.code}. " +
                        "Used for new entries and budgets in other currencies.",
                )
            }
            items(state.rates, key = { it.currency.code }) { row ->
                RateRow(
                    row = row,
                    base = state.baseCurrency,
                    edit = state.rateEdit?.takeIf { it.currency == row.currency },
                    onEvent = onEvent,
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "categories-header") { SectionHeader("Categories") }
            item(key = "manage-categories") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                    title = "Manage categories",
                    subtitle = "Add, rename, reorder or archive",
                    onClick = onManageCategories,
                    modifier = Modifier.testTag(MANAGE_CATEGORIES_TAG),
                )
            }

            item(key = "insights-header") { SectionHeader("Insights") }
            item(key = "open-insights") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                    title = "Spending charts",
                    subtitle = "Spend by category and month-over-month trend",
                    onClick = onOpenInsights,
                    modifier = Modifier.testTag(OPEN_INSIGHTS_TAG),
                )
            }

            item(key = "automation-header") { SectionHeader(stringResource(R.string.settings_automation_section)) }
            item(key = "manage-recurring") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.Autorenew, contentDescription = null) },
                    title = stringResource(R.string.settings_recurring_title),
                    subtitle = stringResource(R.string.settings_recurring_subtitle),
                    onClick = onManageRecurring,
                    modifier = Modifier.testTag(MANAGE_RECURRING_TAG),
                )
            }

            item(key = "data-header") { SectionHeader("Data") }
            item(key = "manage-data") {
                SettingsRow(
                    icon = { Icon(Icons.Outlined.ImportExport, contentDescription = null) },
                    title = "Manage data",
                    subtitle = "Export to CSV, backup and restore",
                    onClick = onManageData,
                    modifier = Modifier.testTag(MANAGE_DATA_TAG),
                )
            }
        }
    }

    if (state.isBaseCurrencyDialogVisible) {
        BaseCurrencyDialog(
            selected = state.baseCurrency,
            options = state.availableCurrencies,
            onSelect = { onEvent(SettingsEvent.BaseCurrencySelected(it)) },
            onDismiss = { onEvent(SettingsEvent.BaseCurrencyDialogDismissed) },
        )
    }
}

// --- Building blocks ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One exchange-rate row. Tapping "Edit" swaps the value for an inline text field; the row grows
 * with `animateContentSize` and the field takes focus with the numeric keyboard.
 */
@Composable
private fun RateRow(
    row: RateRowUiState,
    base: Currency,
    edit: RateEditUiState?,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(rateRowTag(row.currency.code)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .animateContentSize(motion.layout()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Outlined.CurrencyExchange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.currency.displayLabel(), style = MaterialTheme.typography.titleMedium)
                    AnimatedContent(
                        targetState = edit == null,
                        transitionSpec = { fadeIn(motion.feedback()) togetherWith fadeOut(motion.feedback()) },
                        label = "rateValue",
                    ) { showValue ->
                        if (showValue) {
                            Column {
                                Text(
                                    text = "1 ${row.currency.code} = ${row.rate} ${base.code}",
                                    style = AmountTextStyle.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = row.sample,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                text = "Editing rate to ${base.code}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = edit == null, enter = fadeIn(motion.feedback()), exit = fadeOut(motion.feedback())) {
                    TextButton(onClick = { onEvent(SettingsEvent.RateEditStarted(row.currency)) }) {
                        Text("Edit")
                    }
                }
            }
            if (edit != null) {
                RateEditor(edit = edit, base = base, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun RateEditor(
    edit: RateEditUiState,
    base: Currency,
    onEvent: (SettingsEvent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = edit.input,
        onValueChange = { onEvent(SettingsEvent.RateInputChanged(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag(RATE_INPUT_TAG),
        prefix = { Text("1 ${edit.currency.code} =") },
        suffix = { Text(base.code) },
        singleLine = true,
        isError = edit.error != null,
        supportingText = edit.error?.let { { Text(it) } },
        textStyle = AmountTextStyle.copy(fontWeight = FontWeight.Normal),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onEvent(SettingsEvent.RateEditSubmitted) }),
    )
    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(onClick = { onEvent(SettingsEvent.RateEditCancelled) }) { Text("Cancel") }
        FilledTonalButton(
            onClick = { onEvent(SettingsEvent.RateEditSubmitted) },
            enabled = edit.isValid,
        ) { Text("Save") }
    }
}

@Composable
private fun BaseCurrencyDialog(
    selected: Currency,
    options: List<Currency>,
    onSelect: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(BASE_CURRENCY_DIALOG_TAG),
        title = { Text("Base currency") },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Text(
                    text = "Totals and remaining amounts are shown in this currency. Existing entries keep their stored rates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                options.forEach { currency ->
                    val isSelected = currency == selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = isSelected, onClick = { onSelect(currency) }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(currency.code, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = currency.displayName(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** `"EUR · Euro"` in the user's language. */
@Composable
private fun Currency.displayLabel(): String = "$code · ${displayName()}"

@Composable
private fun Currency.displayName(): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    return remember(code, locale) {
        runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)
    }
}

// --- Previews ----------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MonatlichTheme {
        SettingsScreen(
            state = SettingsUiState(
                baseCurrency = Currency.EUR,
                rates = listOf(
                    RateRowUiState(Currency.USD, "0.92", "$100.00 ≈ 92,00 €"),
                    RateRowUiState(Currency.INR, "0.0108", "₹100.00 ≈ 1,08 €"),
                ),
                rateEdit = RateEditUiState(Currency.INR, "0.0108"),
            ),
            onEvent = {},
            onManageCategories = {},
            onManageRecurring = {},
            onManageData = {},
            onOpenInsights = {},
        )
    }
}

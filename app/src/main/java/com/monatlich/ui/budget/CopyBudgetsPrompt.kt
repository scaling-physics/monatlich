package com.monatlich.ui.budget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

const val COPY_BUDGETS_PROMPT_TAG = "copy_budgets_prompt"
const val COPY_BUDGETS_COPY_TAG = "copy_budgets_copy"
const val COPY_BUDGETS_NOT_NOW_TAG = "copy_budgets_not_now"

/**
 * Compact prompt offering to copy the previous month's budgets into [month].
 *
 * Fits the `OverviewScreen.copyBudgetsPrompt` slot. "Copy" persists and dismisses; "Not now"
 * only dismisses — the host decides when to ask again.
 */
@Composable
fun CopyBudgetsPrompt(
    month: YearMonth,
    onDismiss: () -> Unit,
    viewModel: CopyBudgetsViewModel = hiltViewModel(),
) {
    LaunchedEffect(month) { viewModel.load(month) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CopyBudgetsPrompt(state = state, onCopy = viewModel::copy, onDismiss = onDismiss)
}

/** Stateless variant for previews and UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyBudgetsPrompt(
    state: CopyBudgetsUiState,
    onCopy: () -> Unit,
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
        modifier = Modifier.testTag(COPY_BUDGETS_PROMPT_TAG),
    ) {
        CopyBudgetsPromptContent(state = state, onCopy = onCopy, onNotNow = hideThenDismiss)
    }
}

@Composable
private fun CopyBudgetsPromptContent(
    state: CopyBudgetsUiState,
    onCopy: () -> Unit,
    onNotNow: () -> Unit,
) {
    val motion = LocalMotion.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Copy budgets from ${monthLabel(state.previousMonth)}?",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = previewLine(state),
            transitionSpec = { fadeIn(motion.feedback()) togetherWith fadeOut(motion.feedback()) },
            label = "copyPreview",
        ) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = AmountFontFeatures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onNotNow,
                enabled = !state.isCopying,
                modifier = Modifier.testTag(COPY_BUDGETS_NOT_NOW_TAG),
            ) {
                Text("Not now")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onCopy,
                enabled = state.canCopy,
                modifier = Modifier.testTag(COPY_BUDGETS_COPY_TAG),
            ) {
                Text("Copy")
            }
        }
    }
}

/** `"5 categories · total €1.234,00"`, or per-currency totals joined with `+` when mixed. */
private fun previewLine(state: CopyBudgetsUiState): String {
    if (state.isLoading) return " "
    if (state.count == 0) return "No budgets to copy"
    val categories = if (state.count == 1) "1 category" else "${state.count} categories"
    val totals = state.totals.joinToString(" + ") { formatMinor(it.amountMinor, it.currency.code) }
    return "$categories · total $totals"
}

// --- Previews ----------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun CopyBudgetsPromptContentPreview() {
    MonatlichTheme {
        CopyBudgetsPromptContent(
            state = CopyBudgetsUiState(
                month = YearMonth.of(2026, 9),
                previousMonth = YearMonth.of(2026, 8),
                isLoading = false,
                count = 5,
                totals = listOf(Money(199_000, Currency.EUR), Money(500_000, Currency.INR)),
            ),
            onCopy = {},
            onNotNow = {},
        )
    }
}

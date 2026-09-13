package com.monatlich.ui.overview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.ui.common.BudgetBar
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.Motion
import com.monatlich.ui.common.MotionTokens
import com.monatlich.ui.common.animateMinorAmountAsState
import com.monatlich.ui.common.formatMinor
import com.monatlich.ui.theme.AmountTextStyle
import com.monatlich.ui.theme.MonatlichTheme
import com.monatlich.ui.theme.MonatlichThemeTokens
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

const val OVERVIEW_SCREEN_TAG = "overview_screen"
const val ADD_EXPENSE_FAB_TAG = "add_expense_fab"
const val ADD_EXPENSE_SHEET_TAG = "add_expense_sheet"
const val MONTH_LABEL_TAG = "month_label"

/** Hilt entry point for the Overview destination. */
@Composable
fun OverviewRoute(viewModel: OverviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OverviewScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: OverviewUiState,
    onEvent: (OverviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(OVERVIEW_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MonthTopBar(
                month = state.month,
                isCurrentMonth = state.isCurrentMonth,
                onPrevious = { onEvent(OverviewEvent.PreviousMonth) },
                onNext = { onEvent(OverviewEvent.NextMonth) },
                onJumpToCurrent = { onEvent(OverviewEvent.JumpToCurrentMonth) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(OverviewEvent.AddExpenseClicked) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag(ADD_EXPENSE_FAB_TAG),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add expense")
            }
        },
    ) { innerPadding ->
        MonthContent(
            state = state,
            contentPadding = innerPadding,
            onPrevious = { onEvent(OverviewEvent.PreviousMonth) },
            onNext = { onEvent(OverviewEvent.NextMonth) },
        )
    }

    if (state.isAddSheetVisible) {
        AddExpenseSheet(onDismiss = { onEvent(OverviewEvent.AddSheetDismissed) })
    }
}

// --- Top bar / month switcher ------------------------------------------------------------------

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
                    label = "monthLabel",
                ) { shownMonth ->
                    Text(
                        text = monthLabel(shownMonth),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(180.dp)
                            .testTag(MONTH_LABEL_TAG),
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun monthLabel(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    return remember(month, formatter) { month.format(formatter) }
}

/** Shared-axis X: content slides in the direction of travel with a fade-through. */
private fun monthSlide(
    motion: Motion,
    forward: Boolean,
): ContentTransform {
    val sign = if (forward) 1 else -1
    val enter = slideInHorizontally(
        animationSpec = motion.tween(MotionTokens.LAYOUT_MS, easing = MotionTokens.Standard),
    ) { width -> sign * width / 3 } + fadeIn(motion.tween(MotionTokens.EMPHASIS_MS, delayMs = 60))
    val exit = slideOutHorizontally(
        animationSpec = motion.tween(MotionTokens.LAYOUT_MS, easing = MotionTokens.Standard),
    ) { width -> -sign * width / 3 } + fadeOut(motion.tween(MotionTokens.FEEDBACK_MS))
    return enter togetherWith exit
}

// --- Body --------------------------------------------------------------------------------------

@Composable
private fun MonthContent(
    state: OverviewUiState,
    contentPadding: PaddingValues,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val motion = LocalMotion.current
    AnimatedContent(
        targetState = state,
        contentKey = { it.month },
        transitionSpec = { monthSlide(motion, forward = targetState.month > initialState.month) },
        label = "monthContent",
        modifier = Modifier
            .fillMaxSize()
            .monthSwipe(onPrevious = onPrevious, onNext = onNext),
    ) { shown ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                // Leave room for the FAB above the nav bar.
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "total") {
                TotalCard(state = shown, modifier = Modifier.animateItem())
            }
            item(key = "header") {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp, start = 4.dp)
                        .animateItem(),
                )
            }
            items(shown.categories, key = { it.id }) { row ->
                CategoryRow(
                    row = row,
                    currencyCode = shown.currencyCode,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * Horizontal swipe between months. Content follows the finger with a damped translation and
 * springs back; releasing past a distance or velocity threshold fires prev / next.
 */
@Composable
private fun Modifier.monthSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier {
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val distanceThresholdPx = with(density) { 72.dp.toPx() }
    val velocityThresholdPx = with(density) { 900.dp.toPx() }
    val draggableState = rememberDraggableState { delta ->
        scope.launch { offset.snapTo(offset.value + delta) }
    }
    return this
        .graphicsLayer { translationX = offset.value * 0.35f }
        .draggable(
            state = draggableState,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity ->
                val travelled = offset.value
                val fling = abs(velocity) > velocityThresholdPx
                val far = abs(travelled) > distanceThresholdPx
                val direction = if (fling) velocity else travelled
                when {
                    (fling || far) && direction < 0 -> {
                        offset.snapTo(0f)
                        onNext()
                    }
                    (fling || far) && direction > 0 -> {
                        offset.snapTo(0f)
                        onPrevious()
                    }
                    motion.reduceMotion -> offset.snapTo(0f)
                    else -> offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                }
            },
        )
}

@Composable
private fun TotalCard(state: OverviewUiState, modifier: Modifier = Modifier) {
    val budgetColors = MonatlichThemeTokens.budgetColors
    val animatedSpent by animateMinorAmountAsState(state.totalSpentMinor)
    val animatedRemaining by animateMinorAmountAsState(abs(state.totalRemainingMinor))
    val statusColor = if (state.isOverBudget) MaterialTheme.colorScheme.error else budgetColors.onTrack
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Spent this month",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatMinor(animatedSpent, state.currencyCode),
                style = AmountTextStyle.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "of ${formatMinor(state.totalBudgetMinor, state.currencyCode)} budget",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(16.dp))
            BudgetBar(
                progress = state.totalProgress,
                color = if (state.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                height = 10.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (state.isOverBudget) {
                    "${formatMinor(animatedRemaining, state.currencyCode)} over budget"
                } else {
                    "${formatMinor(animatedRemaining, state.currencyCode)} remaining"
                },
                style = AmountTextStyle.copy(fontSize = 14.sp),
                color = statusColor,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    row: CategoryRowUiState,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val budgetColors = MonatlichThemeTokens.budgetColors
    val remainingColor = if (row.isOverBudget) MaterialTheme.colorScheme.error else budgetColors.onTrack
    val barColor = if (row.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val spent = formatMinor(row.spentMinor, currencyCode)
    val budget = formatMinor(row.budgetMinor, currencyCode)
    val remaining = formatMinor(abs(row.remainingMinor), currencyCode)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${row.name}: $spent of $budget spent"
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (row.isOverBudget) "-$remaining" else remaining,
                    style = AmountTextStyle,
                    color = remainingColor,
                )
            }
            Spacer(Modifier.height(10.dp))
            BudgetBar(progress = row.progress, color = barColor)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$spent / $budget",
                style = AmountTextStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Add-expense sheet (placeholder until M4) --------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(ADD_EXPENSE_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Add expense",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "The expense form arrives in M4.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(120.dp))
        }
    }
}

// --- Previews ----------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun OverviewScreenPreview() {
    MonatlichTheme {
        OverviewScreen(
            state = OverviewUiState(
                month = YearMonth.of(2026, 9),
                isCurrentMonth = true,
                currencyCode = "EUR",
                totalSpentMinor = 132_500,
                totalBudgetMinor = 199_000,
                categories = listOf(
                    CategoryRowUiState(1, "Groceries", 31_250, 45_000),
                    CategoryRowUiState(2, "Rent", 120_000, 120_000),
                    CategoryRowUiState(3, "Transport", 10_400, 9_000),
                ),
            ),
            onEvent = {},
        )
    }
}

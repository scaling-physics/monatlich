package com.monatlich.ui.insights

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.ui.theme.MonatlichTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val INSIGHTS_SCREEN_TAG = "insights_screen"
const val INSIGHTS_DONUT_TAG = "insights_donut"
const val INSIGHTS_TREND_TAG = "insights_trend"
const val INSIGHTS_EMPTY_TAG = "insights_empty"

/** Hilt entry point for the "Insights" drill-down from Settings. */
@Composable
fun InsightsRoute(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsScreen(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(INSIGHTS_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "category-header") { SectionHeader("Spend by category — ${state.month.displayLabel()}") }
            item(key = "category-chart") {
                if (state.hasSpending) {
                    CategoryCard(state)
                } else if (!state.isLoading) {
                    EmptyState()
                }
            }

            item(key = "trend-header") { SectionHeader("Last 6 months") }
            item(key = "trend-chart") {
                if (state.trend.isNotEmpty()) TrendCard(state)
            }
        }
    }
}

@Composable
private fun CategoryCard(state: InsightsUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag(INSIGHTS_DONUT_TAG),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            CategoryDonutChart(
                slices = state.categorySlices,
                totalMinor = state.totalSpentMinor,
                currencyCode = state.currencyCode,
                modifier = Modifier.fillMaxWidth().size(180.dp).align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(20.dp))
            state.categorySlices.forEachIndexed { index, slice ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                CategoryLegendRow(slice = slice, currencyCode = state.currencyCode)
            }
        }
    }
}

@Composable
private fun TrendCard(state: InsightsUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag(INSIGHTS_TREND_TAG),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            MonthlyTrendChart(points = state.trend, maxMinor = state.trendMaxMinor)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LegendDot(color = MaterialTheme.colorScheme.primary, label = "Spent")
                LegendDot(color = MaterialTheme.colorScheme.tertiary, label = "Income")
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, shape = CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag(INSIGHTS_EMPTY_TAG),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No spending logged this month yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 4.dp),
    )
}

private fun YearMonth.displayLabel(): String =
    format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))

// --- Previews ------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun InsightsScreenPreview() {
    MonatlichTheme {
        InsightsScreen(
            state = InsightsUiState(
                month = YearMonth.of(2026, 9),
                isLoading = false,
                totalSpentMinor = 10000L,
                categorySlices = listOf(
                    CategorySliceUiState(1, "Groceries", 0xFF2E7D32, 7000L, 0.7f),
                    CategorySliceUiState(2, "Rent", 0xFF6B1F2A, 3000L, 0.3f),
                ),
                trend = (1..6).map {
                    TrendPointUiState(YearMonth.of(2026, it), "M$it", spentMinor = it * 1000L, incomeMinor = it * 1500L)
                },
            ),
            onBack = {},
        )
    }
}

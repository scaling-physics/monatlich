package com.monatlich.ui.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.categoryTint
import com.monatlich.ui.common.formatMinor

/**
 * A ring chart of [slices], each an arc proportional to [CategorySliceUiState.fraction], with the
 * month's total spend centered inside the ring. Arcs animate in on first composition/data change.
 */
@Composable
fun CategoryDonutChart(
    slices: List<CategorySliceUiState>,
    totalMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (motion.reduceMotion) tween(0) else tween(600),
        label = "donutProgress",
    )
    val colors = slices.map { categoryTint(it.color) }

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val strokeWidth = size.minDimension * 0.16f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = 360f * slice.fraction * progress
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += 360f * slice.fraction
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatMinor(totalMinor, currencyCode),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Legend row for one donut slice: color dot, category name, amount and share. */
@Composable
fun CategoryLegendRow(slice: CategorySliceUiState, currencyCode: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(10.dp).background(categoryTint(slice.color), shape = CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(
            text = slice.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = sharePercentLabel(slice.fraction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatMinor(slice.spentMinor, currencyCode),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun sharePercentLabel(fraction: Float): String {
    val percent = fraction * 100f
    return if (percent > 0f && percent < 1f) "<1%" else "${percent.toInt()}%"
}

/**
 * Paired spend/income bars per month. Bar heights are relative to [InsightsUiState.trendMaxMinor]
 * so the chart re-scales as new months enter the window.
 */
@Composable
fun MonthlyTrendChart(
    points: List<TrendPointUiState>,
    maxMinor: Long,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    Row(
        modifier = modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        points.forEach { point ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    TrendBar(
                        fraction = if (maxMinor <= 0L) 0f else point.spentMinor.toFloat() / maxMinor.toFloat(),
                        color = MaterialTheme.colorScheme.primary,
                        reduceMotion = motion.reduceMotion,
                    )
                    TrendBar(
                        fraction = if (maxMinor <= 0L) 0f else point.incomeMinor.toFloat() / maxMinor.toFloat(),
                        color = MaterialTheme.colorScheme.tertiary,
                        reduceMotion = motion.reduceMotion,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrendBar(fraction: Float, color: Color, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) tween(0) else tween(500),
        label = "trendBar",
    )
    Box(
        modifier = modifier
            .width(10.dp)
            .fillMaxHeight(animated.coerceAtLeast(0.015f))
            .background(color, shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
    )
}

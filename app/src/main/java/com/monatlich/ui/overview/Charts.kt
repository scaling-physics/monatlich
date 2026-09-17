package com.monatlich.ui.overview

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.categoryTint
import com.monatlich.ui.common.formatMinor
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * A diagonal light-to-full-tone gradient of a category's own color, used to fill both the donut
 * wedges and the bar chart so each category still reads as its usual color (matching badges and
 * rows elsewhere in the app) while giving charts a bit of depth instead of flat fills.
 */
@Composable
fun categoryGradientBrush(argb: Long): Brush {
    val tint = categoryTint(argb)
    val light = lerp(tint, ComposeColor.White, 0.35f)
    return Brush.linearGradient(colors = listOf(light, tint))
}

/**
 * A ring chart of [slices], each an arc proportional to [CategorySliceUiState.fraction], with the
 * range's total spend centered inside the ring. Arcs animate in on first composition/data change.
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
    val brushes = slices.map { categoryGradientBrush(it.color) }

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
                    brush = brushes[index],
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
 * One gradient-filled horizontal bar per category, proportional to [CategorySliceUiState.fraction]
 * of the range's total. Each row carries its own name/amount/share, so unlike the donut this needs
 * no separate legend.
 */
@Composable
fun CategoryBarChart(
    slices: List<CategorySliceUiState>,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        slices.forEach { slice ->
            CategoryBarRow(slice = slice, currencyCode = currencyCode)
        }
    }
}

@Composable
private fun CategoryBarRow(slice: CategorySliceUiState, currencyCode: String, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val animatedFraction by animateFloatAsState(
        targetValue = slice.fraction.coerceIn(0f, 1f),
        animationSpec = if (motion.reduceMotion) tween(0) else tween(600),
        label = "categoryBarFraction",
    )
    val brush = categoryGradientBrush(slice.color)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = slice.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = sharePercentLabel(slice.fraction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatMinor(slice.spentMinor, currencyCode),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(percent = 50),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction.coerceAtLeast(0.015f))
                    .height(10.dp)
                    .background(brush = brush, shape = RoundedCornerShape(percent = 50)),
            )
        }
    }
}

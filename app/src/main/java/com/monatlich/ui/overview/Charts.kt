package com.monatlich.ui.overview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.formatMinor
import com.monatlich.ui.theme.MonatlichThemeTokens
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The chart's fixed categorical color for [categoryId] — see [MonatlichThemeTokens.chartPalette]. */
@Composable
fun categoryChartColor(categoryId: Long): Color {
    val palette = MonatlichThemeTokens.chartPalette
    return palette[(categoryId % palette.size).toInt()]
}

/**
 * A diagonal light-to-full-tone gradient of [categoryId]'s chart color, used to fill both the
 * donut wedges and the bar chart for a bit of depth instead of flat fills.
 */
@Composable
fun categoryGradientBrush(categoryId: Long): Brush {
    val base = categoryChartColor(categoryId)
    val light = lerp(base, Color.White, 0.35f)
    return Brush.linearGradient(colors = listOf(light, base))
}

/**
 * A ring chart of [slices], each an arc proportional to [CategorySliceUiState.fraction]. Tapping a
 * wedge selects it — it pops outward and the others dim, while the center label swaps from the
 * range's total to that category's name/amount/share; tapping it again, or tapping outside the
 * ring, clears the selection. [selectedCategoryId]/[onSelect] are lifted so the same selection can
 * be driven from the legend rows too.
 */
@Composable
fun CategoryDonutChart(
    slices: List<CategorySliceUiState>,
    totalMinor: Long,
    currencyCode: String,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (motion.reduceMotion) tween(0) else tween(600),
        label = "donutProgress",
    )
    val brushes = slices.map { categoryGradientBrush(it.categoryId) }
    val popSpec = if (motion.reduceMotion) tween<Float>(0) else tween(220)
    val popOuts = slices.map { slice ->
        val target = if (slice.categoryId == selectedCategoryId) 1f else 0f
        val value by animateFloatAsState(target, animationSpec = popSpec, label = "slicePop")
        value
    }
    val dims = slices.map { slice ->
        val hasSelection = selectedCategoryId != null
        val target = if (hasSelection && slice.categoryId != selectedCategoryId) 0.35f else 1f
        val value by animateFloatAsState(target, animationSpec = popSpec, label = "sliceDim")
        value
    }
    val fractions = slices.map { it.fraction }

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        val tapped = donutSliceIndexAt(offset, size, fractions)
                        val tappedCategoryId = tapped?.let { slices[it].categoryId }
                        onSelect(if (tappedCategoryId == selectedCategoryId) null else tappedCategoryId)
                    }
                },
        ) {
            val strokeWidth = size.minDimension * 0.16f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = 360f * slice.fraction * progress
                val bisectorRad = Math.toRadians((startAngle + 360f * slice.fraction / 2).toDouble())
                val popOffset = strokeWidth * 0.35f * popOuts[index]
                val drawTopLeft = topLeft + Offset(
                    (cos(bisectorRad) * popOffset).toFloat(),
                    (sin(bisectorRad) * popOffset).toFloat(),
                )
                drawArc(
                    brush = brushes[index],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = drawTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    alpha = dims[index],
                )
                startAngle += 360f * slice.fraction
            }
        }
        val selectedSlice = slices.firstOrNull { it.categoryId == selectedCategoryId }
        AnimatedContent(targetState = selectedSlice, label = "donutCenterLabel") { shown ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (shown != null) {
                    Text(
                        text = shown.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = formatMinor(shown.spentMinor, currencyCode),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = sharePercentLabel(shown.fraction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
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
    }
}

/**
 * Which slice (if any) a ring-tap at [offset] lands on, for a donut of [canvasSize] built the same
 * way as [CategoryDonutChart]'s own `Canvas` (16%-of-min-dimension stroke, centered). A tap in the
 * hole, outside the ring, or between wedges (shouldn't happen — fractions should sum to ~1) returns
 * `null`. Angles are measured the same way `drawArc` does: 0deg = 3 o'clock, clockwise, so slice 0
 * starts at -90deg (12 o'clock) same as the draw loop.
 */
internal fun donutSliceIndexAt(offset: Offset, canvasSize: IntSize, fractions: List<Float>): Int? {
    val minDim = minOf(canvasSize.width, canvasSize.height).toFloat()
    if (minDim <= 0f) return null
    val strokeWidth = minDim * 0.16f
    val diameter = minDim - strokeWidth
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val distance = sqrt(dx * dx + dy * dy)
    val outerRadius = diameter / 2f + strokeWidth / 2f
    val innerRadius = diameter / 2f - strokeWidth / 2f
    // A little slop outside the exact stroke band makes the ring easier to hit precisely.
    val slop = strokeWidth * 0.3f
    if (distance < innerRadius - slop || distance > outerRadius + slop) return null

    val angleDeg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    val shifted = ((angleDeg + 90f) % 360f + 360f) % 360f
    var cursor = 0f
    fractions.forEachIndexed { index, fraction ->
        val sweep = 360f * fraction
        if (shifted < cursor + sweep) return index
        cursor += sweep
    }
    return null
}

/** Legend row for one donut slice: color dot, category name, amount and share. Tap to select. */
@Composable
fun CategoryLegendRow(
    slice: CategorySliceUiState,
    currencyCode: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = categoryChartColor(slice.categoryId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (selected) color.copy(alpha = 0.14f) else Color.Transparent)
            .padding(vertical = 6.dp, horizontal = 8.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, shape = CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(
            text = slice.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
 * of the range's total. Tapping a row selects it (bold name, others dim) the same way a donut wedge
 * or legend row does — [selectedCategoryId]/[onSelect] are shared with those so switching between
 * chart types keeps the selection.
 */
@Composable
fun CategoryBarChart(
    slices: List<CategorySliceUiState>,
    currencyCode: String,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slices.forEach { slice ->
            CategoryBarRow(
                slice = slice,
                currencyCode = currencyCode,
                selected = slice.categoryId == selectedCategoryId,
                dimmed = selectedCategoryId != null && slice.categoryId != selectedCategoryId,
                onClick = { onSelect(if (selectedCategoryId == slice.categoryId) null else slice.categoryId) },
            )
        }
    }
}

@Composable
private fun CategoryBarRow(
    slice: CategorySliceUiState,
    currencyCode: String,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val animatedFraction by animateFloatAsState(
        targetValue = slice.fraction.coerceIn(0f, 1f),
        animationSpec = if (motion.reduceMotion) tween(0) else tween(600),
        label = "categoryBarFraction",
    )
    val rowAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.4f else 1f,
        animationSpec = if (motion.reduceMotion) tween(0) else tween(220),
        label = "categoryBarDim",
    )
    val brush = categoryGradientBrush(slice.categoryId)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = slice.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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

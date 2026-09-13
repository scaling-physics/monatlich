package com.monatlich.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

/**
 * Horizontal budget progress bar. The fill animates from 0 on first composition and to the new
 * width whenever [progress] changes (~400 ms, FastOutSlowIn per AGENT.md). [progress] is clamped
 * to 0..1; callers pick [color] (maroon on track, `error` when over budget).
 */
@Composable
fun BudgetBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 8.dp,
) {
    val motion = LocalMotion.current
    val target = progress.coerceIn(0f, 1f)
    // Starts at 0 so the first frame grows in; later changes animate between values.
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = motion.bar(),
        label = "budgetBar",
    )
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = motion.layout(),
        label = "budgetBarColor",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress)
                .clip(RoundedCornerShape(percent = 50))
                .background(animatedColor),
        )
    }
}

/**
 * Counts a minor-unit amount up or down towards [target]. Animates a 0..1 fraction and
 * interpolates in `Long` space so large amounts never lose precision through `Float`.
 * Starts from 0 on first composition so totals count up on load.
 */
@Composable
fun animateMinorAmountAsState(target: Long): State<Long> {
    val motion = LocalMotion.current
    val spec = rememberUpdatedState(motion.bar<Float>())
    val fraction = remember { Animatable(0f) }
    val from = remember { mutableLongStateOf(0L) }
    val to = remember { mutableLongStateOf(target) }
    LaunchedEffect(target) {
        val current = interpolate(from.longValue, to.longValue, fraction.value)
        from.longValue = current
        to.longValue = target
        fraction.snapTo(0f)
        fraction.animateTo(1f, animationSpec = spec.value)
    }
    return remember {
        derivedStateOf { interpolate(from.longValue, to.longValue, fraction.value) }
    }
}

private fun interpolate(from: Long, to: Long, fraction: Float): Long =
    from + ((to - from) * fraction.toDouble()).roundToLong()

package com.monatlich.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The category's icon on a softly tinted circle of its colour. One look for overview rows,
 * the category manager and the editor sheet. Colour changes animate (editor swatch picks).
 */
@Composable
fun CategoryBadge(
    icon: String,
    color: Long,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    size: Dp = 36.dp,
) {
    val motion = LocalMotion.current
    val target = categoryTint(color).let { if (muted) it.copy(alpha = 0.45f) else it }
    val tint by animateColorAsState(target, motion.layout(), label = "badgeTint")
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIcon(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

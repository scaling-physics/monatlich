package com.monatlich.ui.common

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens from AGENT.md: 150–200 ms for small feedback, 300–400 ms for layout changes,
 * never above 500 ms. Screens read specs through [LocalMotion] so the system "remove animations"
 * setting (`ANIMATOR_DURATION_SCALE == 0`) collapses every animation to [snap].
 */
object MotionTokens {
    const val FEEDBACK_MS = 150
    const val EMPHASIS_MS = 200
    const val LAYOUT_MS = 300
    const val BAR_MS = 400
    const val MAX_MS = 500

    val Standard: Easing = FastOutSlowInEasing
    val Incoming: Easing = LinearOutSlowInEasing
    val Outgoing: Easing = FastOutLinearInEasing
}

@Immutable
data class Motion(val reduceMotion: Boolean) {
    /** Tween honouring reduced motion. Durations are clamped to [MotionTokens.MAX_MS]. */
    fun <T> tween(
        durationMs: Int,
        delayMs: Int = 0,
        easing: Easing = MotionTokens.Standard,
    ): FiniteAnimationSpec<T> = if (reduceMotion) {
        snap()
    } else {
        tween(
            durationMillis = durationMs.coerceAtMost(MotionTokens.MAX_MS),
            delayMillis = delayMs,
            easing = easing,
        )
    }

    fun <T> feedback(): FiniteAnimationSpec<T> = tween(MotionTokens.FEEDBACK_MS)
    fun <T> layout(): FiniteAnimationSpec<T> = tween(MotionTokens.LAYOUT_MS)
    fun <T> bar(): FiniteAnimationSpec<T> = tween(MotionTokens.BAR_MS)

    /** Duration in ms for APIs that take an Int rather than a spec (e.g. slide transitions). */
    fun duration(ms: Int): Int = if (reduceMotion) 0 else ms.coerceAtMost(MotionTokens.MAX_MS)
}

val LocalMotion = compositionLocalOf { Motion(reduceMotion = false) }

/** Reads the system animator scale once per composition root. */
@Composable
fun rememberMotion(): Motion {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        Motion(reduceMotion = scale == 0f)
    }
}

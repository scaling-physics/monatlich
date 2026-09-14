package com.monatlich.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monatlich.R
import kotlinx.coroutines.delay

private val SplashBackgroundLight = Color(0xFFFFF3EE)
private val SplashForegroundLight = Color(0xFF6B1F2A)
private val SplashBackgroundDark = Color(0xFF1C1517)
private val SplashForegroundDark = Color(0xFFD98FA5)

/** How long the screen stays up before [SplashScreen] calls back to swap it for the real app. */
private const val HOLD_MS = 1100L
private const val ENTER_MS = 420

/**
 * Full-bleed brand splash shown for a moment right after the OS-drawn cold-start splash (which
 * reuses the launcher icon and hands off to Compose almost immediately) — this is where the
 * author attribution actually has room to live, and it shows on every launch rather than only a
 * device's first cold boot.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) SplashBackgroundDark else SplashBackgroundLight
    val foreground = if (isDark) SplashForegroundDark else SplashForegroundLight
    val markDrawable = if (isDark) R.drawable.ic_splash_mark_dark else R.drawable.ic_splash_mark_light

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(HOLD_MS)
        onFinished()
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(ENTER_MS),
        label = "splashAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = tween(ENTER_MS),
        label = "splashScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(markDrawable),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .graphicsLayer { this.alpha = alpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Srikanth Subramanian",
                color = foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "© 2026",
                color = foreground.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}

// --- Preview --------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    SplashScreen(onFinished = {})
}

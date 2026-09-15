package com.monatlich

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.monatlich.ui.navigation.MonatlichAppShell
import com.monatlich.ui.splash.SplashScreen
import com.monatlich.ui.theme.MonatlichTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var quickAddRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        quickAddRequested = intent.getBooleanExtra(EXTRA_QUICK_ADD, false)
        setContent {
            MonatlichTheme {
                var showSplash by remember { mutableStateOf(true) }
                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(300),
                    label = "splashToApp",
                ) { splashVisible ->
                    if (splashVisible) {
                        SplashScreen(onFinished = { showSplash = false })
                    } else {
                        MonatlichAppShell(
                            quickAddRequested = quickAddRequested,
                            onQuickAddHandled = { quickAddRequested = false },
                        )
                    }
                }
            }
        }
    }

    /** The widget's "+" targets this activity directly; a warm instance must pick up the new tap. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QUICK_ADD, false)) quickAddRequested = true
    }

    companion object {
        /** Boolean intent extra: launched from the home-screen widget's quick-add button. */
        const val EXTRA_QUICK_ADD = "quick_add"
    }
}

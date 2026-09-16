package com.monatlich

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.ui.navigation.MonatlichAppShell
import com.monatlich.ui.security.AppLockEvent
import com.monatlich.ui.security.AppLockRoute
import com.monatlich.ui.security.AppLockViewModel
import com.monatlich.ui.splash.SplashScreen
import com.monatlich.ui.theme.MonatlichTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * A [FragmentActivity] (not just [androidx.activity.ComponentActivity]) because
 * [androidx.biometric.BiometricPrompt] needs one to host its internal result fragment.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var quickAddRequested by mutableStateOf(false)
    private var appIsUnlocked by mutableStateOf(false)

    /** Grace window so leaving the app briefly (e.g. the CSV export file picker) doesn't re-lock it. */
    private var backgroundedAtMillis: Long? = null

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            backgroundedAtMillis = System.currentTimeMillis()
        }

        override fun onStart(owner: LifecycleOwner) {
            val backgroundedAt = backgroundedAtMillis ?: return
            if (System.currentTimeMillis() - backgroundedAt > RELOCK_GRACE_MS) {
                appIsUnlocked = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
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
                        AppLockGate(
                            activity = this,
                            unlocked = appIsUnlocked,
                            onUnlocked = { appIsUnlocked = true },
                        ) {
                            MonatlichAppShell(
                                quickAddRequested = quickAddRequested,
                                onQuickAddHandled = { quickAddRequested = false },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
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
        private const val RELOCK_GRACE_MS = 30_000L
    }
}

/** Shows [AppLockRoute] instead of [content] while app lock is on and the session hasn't unlocked yet. */
@Composable
private fun AppLockGate(
    activity: FragmentActivity,
    unlocked: Boolean,
    onUnlocked: () -> Unit,
    content: @Composable () -> Unit,
) {
    val viewModel: AppLockViewModel = hiltViewModel()
    val lockEnabled by viewModel.lockEnabled.collectAsStateWithLifecycle()

    if (!lockEnabled || unlocked) {
        content()
    } else {
        AppLockRoute(
            viewModel = viewModel,
            onBiometricRequested = {
                showBiometricPrompt(activity) { viewModel.onEvent(AppLockEvent.BiometricSucceeded) }
            },
            onUnlocked = onUnlocked,
        )
    }
}

private fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock monatlich")
        .setNegativeButtonText("Use PIN")
        .build()
    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}

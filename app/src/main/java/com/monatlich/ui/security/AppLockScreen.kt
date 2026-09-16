package com.monatlich.ui.security

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.R
import com.monatlich.ui.theme.MonatlichTheme

const val APP_LOCK_SCREEN_TAG = "app_lock_screen"
const val APP_LOCK_BIOMETRIC_BUTTON_TAG = "app_lock_biometric_button"

/** The composable half of the lock gate; [MainActivity] owns unlock state and biometric prompting. */
@Composable
fun AppLockRoute(
    onBiometricRequested: () -> Unit,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.unlocked.collect { onUnlocked() }
    }
    AppLockScreen(
        state = state,
        onEvent = { event ->
            if (event is AppLockEvent.BiometricRequested) onBiometricRequested()
            viewModel.onEvent(event)
        },
        modifier = modifier,
    )
}

@Composable
fun AppLockScreen(
    state: AppLockUiState,
    onEvent: (AppLockEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.biometricEnabled) {
        if (state.biometricEnabled) onEvent(AppLockEvent.BiometricRequested)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(APP_LOCK_SCREEN_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "monatlich",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(28.dp))

        Text(
            text = if (state.error) "Incorrect PIN, try again" else "Enter your PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(20.dp))

        PinDots(length = state.pin.length)
        Spacer(Modifier.height(36.dp))

        Keypad(
            biometricEnabled = state.biometricEnabled,
            onDigit = { onEvent(AppLockEvent.DigitEntered(it)) },
            onBackspace = { onEvent(AppLockEvent.Backspace) },
            onBiometric = { onEvent(AppLockEvent.BiometricRequested) },
        )

        Spacer(Modifier.weight(1.4f))
    }
}

@Composable
internal fun PinDots(length: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(AppLockUiState.PIN_LENGTH) { index ->
            val filled = index < length
            val size by animateDpAsState(
                targetValue = if (filled) 14.dp else 12.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "pinDot",
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Composable
internal fun Keypad(
    biometricEnabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf("123", "456", "789")
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> KeypadKey(label = digit.toString()) { onDigit(digit) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (biometricEnabled) {
                KeypadIconKey(
                    icon = Icons.Filled.Fingerprint,
                    contentDescription = "Unlock with biometrics",
                    onClick = onBiometric,
                    modifier = Modifier.testTag(APP_LOCK_BIOMETRIC_BUTTON_TAG),
                )
            } else {
                Spacer(Modifier.size(64.dp))
            }
            KeypadKey(label = "0") { onDigit('0') }
            KeypadIconKey(
                icon = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete digit",
                onClick = onBackspace,
            )
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun KeypadIconKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(64.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- Preview -------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun AppLockScreenPreview() {
    MonatlichTheme {
        AppLockScreen(state = AppLockUiState(pin = "12", biometricEnabled = true), onEvent = {})
    }
}

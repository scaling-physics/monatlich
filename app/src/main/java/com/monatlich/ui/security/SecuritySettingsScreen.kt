package com.monatlich.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.ui.theme.MonatlichTheme

const val SECURITY_SCREEN_TAG = "security_screen"
const val LOCK_TOGGLE_TAG = "security_lock_toggle"
const val SET_PIN_ROW_TAG = "security_set_pin_row"
const val BIOMETRIC_TOGGLE_TAG = "security_biometric_toggle"
const val REMOVE_PIN_ROW_TAG = "security_remove_pin_row"
const val PIN_SETUP_SHEET_TAG = "security_pin_setup_sheet"

@Composable
fun SecuritySettingsRoute(
    onBack: () -> Unit,
    viewModel: SecuritySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SecuritySettingsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    state: SecurityUiState,
    onEvent: (SecurityEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(SECURITY_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App lock") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { SectionHeader("Require a PIN — or your fingerprint / face — to open monatlich") }

            item(key = "lock-toggle") {
                SecurityRow(
                    icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
                    title = "App lock",
                    subtitle = if (state.lockEnabled) "On" else "Off",
                    modifier = Modifier.testTag(LOCK_TOGGLE_TAG),
                    trailing = {
                        Switch(checked = state.lockEnabled, onCheckedChange = { onEvent(SecurityEvent.LockToggled(it)) })
                    },
                )
            }

            item(key = "pin-row") {
                SecurityRow(
                    icon = { Icon(Icons.Outlined.Password, contentDescription = null) },
                    title = if (state.hasPin) "Change PIN" else "Set PIN",
                    subtitle = if (state.hasPin) "4-digit PIN is set" else "Required before turning app lock on",
                    onClick = { onEvent(SecurityEvent.SetPinClicked) },
                    modifier = Modifier.testTag(SET_PIN_ROW_TAG),
                )
            }

            if (state.hasPin && state.biometricAvailable) {
                item(key = "biometric-toggle") {
                    SecurityRow(
                        icon = { Icon(Icons.Outlined.Fingerprint, contentDescription = null) },
                        title = "Biometric unlock",
                        subtitle = "Use your fingerprint or face instead of the PIN",
                        modifier = Modifier.testTag(BIOMETRIC_TOGGLE_TAG),
                        trailing = {
                            Switch(
                                checked = state.biometricEnabled,
                                onCheckedChange = { onEvent(SecurityEvent.BiometricToggled(it)) },
                            )
                        },
                    )
                }
            }

            if (state.hasPin) {
                item(key = "remove-pin") {
                    SecurityRow(
                        icon = { Icon(Icons.Outlined.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = "Remove PIN",
                        subtitle = "Turns off app lock and biometric unlock",
                        onClick = { onEvent(SecurityEvent.RemovePinClicked) },
                        titleColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(REMOVE_PIN_ROW_TAG),
                    )
                }
            }
        }
    }

    state.pinSetup?.let { setup ->
        PinSetupSheet(setup = setup, onEvent = onEvent)
    }

    if (state.showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { onEvent(SecurityEvent.RemovePinDismissed) },
            title = { Text("Remove PIN?") },
            text = { Text("App lock and biometric unlock both turn off. You can set a new PIN again any time.") },
            confirmButton = {
                TextButton(onClick = { onEvent(SecurityEvent.RemovePinConfirmed) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SecurityEvent.RemovePinDismissed) }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSetupSheet(setup: PinSetupUiState, onEvent: (SecurityEvent) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onEvent(SecurityEvent.PinSetupDismissed) },
        sheetState = sheetState,
        modifier = Modifier.testTag(PIN_SETUP_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when {
                    setup.mismatch -> "PINs didn't match — start over"
                    setup.stage == PinSetupStage.Enter -> "Choose a 4-digit PIN"
                    else -> "Confirm your PIN"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (setup.mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))
            PinDots(length = setup.pin.length)
            Spacer(Modifier.height(28.dp))
            Keypad(
                biometricEnabled = false,
                onDigit = { onEvent(SecurityEvent.PinDigitEntered(it)) },
                onBackspace = { onEvent(SecurityEvent.PinBackspace) },
                onBiometric = {},
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SecurityRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

// --- Preview ---------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun SecuritySettingsScreenPreview() {
    MonatlichTheme {
        SecuritySettingsScreen(
            state = SecurityUiState(hasPin = true, lockEnabled = true, biometricAvailable = true),
            onEvent = {},
            onBack = {},
        )
    }
}

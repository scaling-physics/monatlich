package com.monatlich.ui.security

data class SecurityUiState(
    val hasPin: Boolean = false,
    val lockEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinSetup: PinSetupUiState? = null,
    val showRemoveConfirm: Boolean = false,
)

/** Draft state of the "set/change PIN" sheet: enter a new PIN, then confirm it. */
data class PinSetupUiState(
    val stage: PinSetupStage = PinSetupStage.Enter,
    val firstPin: String = "",
    val pin: String = "",
    val mismatch: Boolean = false,
)

enum class PinSetupStage { Enter, Confirm }

sealed interface SecurityEvent {
    data object SetPinClicked : SecurityEvent
    data object PinSetupDismissed : SecurityEvent
    data class PinDigitEntered(val digit: Char) : SecurityEvent
    data object PinBackspace : SecurityEvent
    data class LockToggled(val enabled: Boolean) : SecurityEvent
    data class BiometricToggled(val enabled: Boolean) : SecurityEvent
    data object RemovePinClicked : SecurityEvent
    data object RemovePinConfirmed : SecurityEvent
    data object RemovePinDismissed : SecurityEvent
}

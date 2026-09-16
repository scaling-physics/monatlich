package com.monatlich.ui.security

data class AppLockUiState(
    val pin: String = "",
    val error: Boolean = false,
    val biometricEnabled: Boolean = false,
) {
    companion object {
        const val PIN_LENGTH = 4
    }
}

sealed interface AppLockEvent {
    data class DigitEntered(val digit: Char) : AppLockEvent
    data object Backspace : AppLockEvent
    data object BiometricRequested : AppLockEvent
    data object BiometricSucceeded : AppLockEvent
}

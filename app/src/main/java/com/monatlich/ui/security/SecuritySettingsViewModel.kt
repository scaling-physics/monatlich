package com.monatlich.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.repository.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "App lock" screen: enable/disable the lock, set or change the PIN, toggle biometrics. */
@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val security: SecurityRepository,
    biometricAvailability: BiometricAvailability,
) : ViewModel() {

    private val biometricAvailable: Boolean = biometricAvailability.isAvailable()

    private val flags = MutableStateFlow(Flags())

    val uiState: StateFlow<SecurityUiState> = combine(
        security.hasPin,
        security.isLockEnabled,
        security.isBiometricEnabled,
        flags,
    ) { hasPin, lockEnabled, biometricEnabled, flags ->
        SecurityUiState(
            hasPin = hasPin,
            lockEnabled = lockEnabled,
            biometricAvailable = biometricAvailable,
            biometricEnabled = biometricEnabled,
            pinSetup = flags.pinSetup,
            showRemoveConfirm = flags.showRemoveConfirm,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SecurityUiState(biometricAvailable = biometricAvailable),
    )

    fun onEvent(event: SecurityEvent) {
        when (event) {
            SecurityEvent.SetPinClicked -> flags.update { it.copy(pinSetup = PinSetupUiState()) }
            SecurityEvent.PinSetupDismissed -> flags.update { it.copy(pinSetup = null) }
            is SecurityEvent.PinDigitEntered -> enterDigit(event.digit)
            SecurityEvent.PinBackspace -> flags.update { flag ->
                flag.pinSetup?.let { flag.copy(pinSetup = it.copy(pin = it.pin.dropLast(1), mismatch = false)) } ?: flag
            }
            is SecurityEvent.LockToggled -> onLockToggled(event.enabled)
            is SecurityEvent.BiometricToggled -> viewModelScope.launch { security.setBiometricEnabled(event.enabled) }
            SecurityEvent.RemovePinClicked -> flags.update { it.copy(showRemoveConfirm = true) }
            SecurityEvent.RemovePinDismissed -> flags.update { it.copy(showRemoveConfirm = false) }
            SecurityEvent.RemovePinConfirmed -> viewModelScope.launch {
                security.clearPin()
                flags.update { it.copy(showRemoveConfirm = false) }
            }
        }
    }

    private fun onLockToggled(enabled: Boolean) {
        if (enabled && !uiState.value.hasPin) {
            flags.update { it.copy(pinSetup = PinSetupUiState()) }
            return
        }
        viewModelScope.launch { security.setLockEnabled(enabled) }
    }

    private fun enterDigit(digit: Char) {
        val setup = flags.value.pinSetup ?: return
        if (setup.pin.length >= AppLockUiState.PIN_LENGTH) return
        val pin = setup.pin + digit
        if (pin.length < AppLockUiState.PIN_LENGTH) {
            flags.update { it.copy(pinSetup = setup.copy(pin = pin, mismatch = false)) }
            return
        }
        when (setup.stage) {
            PinSetupStage.Enter ->
                flags.update { it.copy(pinSetup = PinSetupUiState(stage = PinSetupStage.Confirm, firstPin = pin)) }
            PinSetupStage.Confirm -> {
                if (pin == setup.firstPin) {
                    viewModelScope.launch {
                        security.setPin(pin)
                        security.setLockEnabled(true)
                        flags.update { it.copy(pinSetup = null) }
                    }
                } else {
                    flags.update { it.copy(pinSetup = PinSetupUiState(mismatch = true)) }
                }
            }
        }
    }

    private data class Flags(
        val pinSetup: PinSetupUiState? = null,
        val showRemoveConfirm: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

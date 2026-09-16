package com.monatlich.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.repository.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [AppLockScreen]: PIN entry (and biometric, when enabled) gating the rest of the app.
 *
 * This ViewModel is Activity-scoped, so it outlives any one lock screen showing — the app can be
 * unlocked, backgrounded, and re-locked many times over its life. A successful unlock is therefore
 * modeled as a one-shot [unlocked] event, never as persistent [AppLockUiState] — a boolean flag
 * there would go stale (stay "unlocked") the next time the lock screen has to show again.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val security: SecurityRepository,
) : ViewModel() {

    val lockEnabled: StateFlow<Boolean> = security.isLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private val _unlocked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits once per successful unlock. Not replayed, so a fresh collector never sees a stale hit. */
    val unlocked: SharedFlow<Unit> = _unlocked.asSharedFlow()

    init {
        viewModelScope.launch {
            security.isBiometricEnabled.collect { enabled ->
                _uiState.update { it.copy(biometricEnabled = enabled) }
            }
        }
    }

    fun onEvent(event: AppLockEvent) {
        when (event) {
            is AppLockEvent.DigitEntered -> enterDigit(event.digit)
            AppLockEvent.Backspace -> _uiState.update { it.copy(pin = it.pin.dropLast(1), error = false) }
            AppLockEvent.BiometricRequested -> Unit // handled by the host Activity; nothing to update here
            AppLockEvent.BiometricSucceeded -> _unlocked.tryEmit(Unit)
        }
    }

    private fun enterDigit(digit: Char) {
        val current = _uiState.value
        if (current.pin.length >= AppLockUiState.PIN_LENGTH) return
        val pin = current.pin + digit
        if (pin.length < AppLockUiState.PIN_LENGTH) {
            _uiState.update { it.copy(pin = pin, error = false) }
            return
        }
        _uiState.update { it.copy(pin = pin) }
        viewModelScope.launch {
            val ok = security.verifyPin(pin)
            _uiState.update { it.copy(pin = "", error = !ok) }
            if (ok) _unlocked.tryEmit(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

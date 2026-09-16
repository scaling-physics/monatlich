package com.monatlich.domain.repository

import kotlinx.coroutines.flow.Flow

/** App-lock settings: a PIN, optionally backed by biometric unlock. Nothing here ever leaves the device. */
interface SecurityRepository {
    /** Whether the lock screen shows on launch / resume from background. */
    val isLockEnabled: Flow<Boolean>

    /** Whether a fingerprint/face prompt is offered as a shortcut past the PIN. */
    val isBiometricEnabled: Flow<Boolean>

    /** Whether a PIN has been set at all; [isLockEnabled] and [isBiometricEnabled] require this. */
    val hasPin: Flow<Boolean>

    /** Hashes and stores [pin] as the new PIN, replacing any existing one. */
    suspend fun setPin(pin: String)

    /** True if [pin] matches the stored hash. False (never throws) if no PIN is set. */
    suspend fun verifyPin(pin: String): Boolean

    /** Removes the PIN and turns off both lock and biometric unlock. */
    suspend fun clearPin()

    suspend fun setLockEnabled(enabled: Boolean)

    suspend fun setBiometricEnabled(enabled: Boolean)
}

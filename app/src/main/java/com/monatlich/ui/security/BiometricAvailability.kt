package com.monatlich.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether this device can actually offer biometric unlock — behind an interface so it's fakeable in tests. */
interface BiometricAvailability {
    fun isAvailable(): Boolean
}

class AndroidBiometricAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricAvailability {
    override fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
}

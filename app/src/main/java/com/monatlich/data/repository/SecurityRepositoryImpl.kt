package com.monatlich.data.repository

import com.monatlich.data.local.SecurityDataStore
import com.monatlich.domain.repository.SecurityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SecurityRepositoryImpl @Inject constructor(
    private val store: SecurityDataStore,
) : SecurityRepository {

    override val isLockEnabled: Flow<Boolean> = store.lockEnabled
    override val isBiometricEnabled: Flow<Boolean> = store.biometricEnabled
    override val hasPin: Flow<Boolean> = store.pinHash.map { it != null }

    override suspend fun setPin(pin: String) {
        val salt = PinHash.newSalt()
        store.setPin(hash = PinHash.hash(pin, salt), salt = salt)
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val hash = store.pinHash.first() ?: return false
        val salt = store.pinSalt.first() ?: return false
        return PinHash.hash(pin, salt) == hash
    }

    override suspend fun clearPin() = store.clearPin()

    override suspend fun setLockEnabled(enabled: Boolean) = store.setLockEnabled(enabled)

    override suspend fun setBiometricEnabled(enabled: Boolean) = store.setBiometricEnabled(enabled)
}

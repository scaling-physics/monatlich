package com.monatlich.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access to the app-lock preferences, stored in the same DataStore file as [SettingsDataStore].
 * Only the PIN's salted hash is kept here, never the PIN itself — see [com.monatlich.data.repository.PinHash].
 */
@Singleton
class SecurityDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val lockEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LOCK_ENABLED] ?: false }
    val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_BIOMETRIC_ENABLED] ?: false }
    val pinHash: Flow<String?> = dataStore.data.map { it[KEY_PIN_HASH] }
    val pinSalt: Flow<String?> = dataStore.data.map { it[KEY_PIN_SALT] }

    suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LOCK_ENABLED] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setPin(hash: String, salt: String) {
        dataStore.edit {
            it[KEY_PIN_HASH] = hash
            it[KEY_PIN_SALT] = salt
        }
    }

    /** Removes the PIN and turns off both lock and biometric unlock, since neither works without it. */
    suspend fun clearPin() {
        dataStore.edit {
            it.remove(KEY_PIN_HASH)
            it.remove(KEY_PIN_SALT)
            it[KEY_LOCK_ENABLED] = false
            it[KEY_BIOMETRIC_ENABLED] = false
        }
    }

    companion object {
        val KEY_LOCK_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("lock_enabled")
        val KEY_BIOMETRIC_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("biometric_enabled")
        val KEY_PIN_HASH: Preferences.Key<String> = stringPreferencesKey("pin_hash")
        val KEY_PIN_SALT: Preferences.Key<String> = stringPreferencesKey("pin_salt")
    }
}

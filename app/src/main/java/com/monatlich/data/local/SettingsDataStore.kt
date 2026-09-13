package com.monatlich.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.monatlich.domain.model.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Typed access to the app's preference DataStore. Keys and defaults live here and nowhere else. */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val baseCurrency: Flow<Currency> = dataStore.data.map { prefs ->
        prefs[KEY_BASE_CURRENCY]
            ?.let { code -> Currency.entries.firstOrNull { it.code == code } }
            ?: DEFAULT_BASE_CURRENCY
    }

    suspend fun setBaseCurrency(currency: Currency) {
        dataStore.edit { it[KEY_BASE_CURRENCY] = currency.code }
    }

    companion object {
        const val FILE_NAME = "settings"
        val DEFAULT_BASE_CURRENCY: Currency = Currency.EUR
        val KEY_BASE_CURRENCY: Preferences.Key<String> = stringPreferencesKey("base_currency")
    }
}

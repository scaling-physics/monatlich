package com.monatlich.domain.repository

import com.monatlich.domain.model.Currency
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** The base currency all totals are computed in. Defaults to EUR until the user changes it. */
    val baseCurrency: Flow<Currency>

    suspend fun setBaseCurrency(currency: Currency)
}

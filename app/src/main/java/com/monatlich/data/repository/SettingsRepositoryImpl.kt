package com.monatlich.data.repository

import com.monatlich.data.local.SettingsDataStore
import com.monatlich.domain.model.Currency
import com.monatlich.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val settings: SettingsDataStore,
) : SettingsRepository {

    override val baseCurrency: Flow<Currency> = settings.baseCurrency

    override suspend fun setBaseCurrency(currency: Currency) = settings.setBaseCurrency(currency)
}

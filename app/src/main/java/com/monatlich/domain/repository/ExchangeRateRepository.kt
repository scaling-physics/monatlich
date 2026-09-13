package com.monatlich.domain.repository

import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.ExchangeRate
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface ExchangeRateRepository {
    /** The whole rate table, ordered by currency code. */
    fun observeAll(): Flow<List<ExchangeRate>>

    fun observe(currency: Currency): Flow<ExchangeRate?>

    suspend fun get(currency: Currency): ExchangeRate?

    /** Creates or replaces the rate for [currency], stamping it with the current time. */
    suspend fun set(currency: Currency, rateToBase: BigDecimal)

    /**
     * The rate to use for a new entry in [currency] when [base] is the base currency:
     * `1` when they are the same, otherwise the table row for [currency].
     * Returns `null` if the table has no row for [currency].
     */
    suspend fun currentRateToBase(currency: Currency, base: Currency): BigDecimal?
}

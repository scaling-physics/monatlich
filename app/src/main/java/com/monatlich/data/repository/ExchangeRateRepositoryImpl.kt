package com.monatlich.data.repository

import com.monatlich.data.local.ExchangeRateDao
import com.monatlich.data.local.ExchangeRateEntity
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.ExchangeRate
import com.monatlich.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class ExchangeRateRepositoryImpl @Inject constructor(
    private val dao: ExchangeRateDao,
) : ExchangeRateRepository {

    override fun observeAll(): Flow<List<ExchangeRate>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(currency: Currency): Flow<ExchangeRate?> =
        dao.observe(currency.code).map { it?.toDomain() }

    override suspend fun get(currency: Currency): ExchangeRate? = dao.get(currency.code)?.toDomain()

    override suspend fun set(currency: Currency, rateToBase: BigDecimal) {
        require(rateToBase.signum() > 0) { "rateToBase must be positive: $rateToBase" }
        dao.upsert(ExchangeRateEntity(currency.code, rateToBase, Instant.now()))
    }

    override suspend fun currentRateToBase(currency: Currency, base: Currency): BigDecimal? =
        if (currency == base) BigDecimal.ONE else dao.get(currency.code)?.rateToBase
}

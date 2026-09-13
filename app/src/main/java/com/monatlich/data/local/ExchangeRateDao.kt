package com.monatlich.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {

    @Query("SELECT * FROM exchange_rates ORDER BY currencyCode")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM exchange_rates WHERE currencyCode = :currencyCode")
    fun observe(currencyCode: String): Flow<ExchangeRateEntity?>

    @Query("SELECT * FROM exchange_rates WHERE currencyCode = :currencyCode")
    suspend fun get(currencyCode: String): ExchangeRateEntity?

    @Query("SELECT COUNT(*) FROM exchange_rates")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: ExchangeRateEntity)
}

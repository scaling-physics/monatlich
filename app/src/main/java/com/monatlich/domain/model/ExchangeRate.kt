package com.monatlich.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * One row of the user-editable rate table: `1 [currency] == [rateToBase] base currency`.
 * The row for the base currency itself is expected to hold `1`.
 */
data class ExchangeRate(
    val currency: Currency,
    val rateToBase: BigDecimal,
    val updatedAt: Instant,
) {
    init {
        require(rateToBase.signum() > 0) { "rateToBase must be positive: $rateToBase" }
    }
}

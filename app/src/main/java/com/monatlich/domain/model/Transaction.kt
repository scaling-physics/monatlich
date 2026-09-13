package com.monatlich.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

enum class TransactionType { EXPENSE, INCOME }

/**
 * A single logged expense or income.
 *
 * [rateToBase] is the exchange rate from [amount]'s currency to the base currency *at entry time*
 * (`1 amount.currency == rateToBase base`), so history never shifts when the rate table changes.
 * It is `1` when the transaction is already in the base currency.
 */
data class Transaction(
    val id: Long = 0L,
    val categoryId: Long,
    val date: LocalDate,
    val amount: Money,
    val rateToBase: BigDecimal,
    val type: TransactionType,
    val note: String? = null,
) {
    init {
        require(rateToBase.signum() > 0) { "rateToBase must be positive: $rateToBase" }
    }

    /** The month this transaction belongs to; always derived from [date]. */
    val month: YearMonth get() = YearMonth.from(date)

    /** [amount] expressed in the base currency via the stored [rateToBase]. */
    fun amountInBase(base: Currency): Money =
        if (amount.currency == base) amount else amount.convertTo(base, rateToBase)
}

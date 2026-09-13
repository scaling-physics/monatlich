package com.monatlich.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Thrown when two [Money] values of different currencies are combined without conversion. */
class CurrencyMismatchException(left: Currency, right: Currency) :
    IllegalArgumentException("Cannot combine ${left.code} with ${right.code}; convert to a common currency first")

/**
 * An exact amount of money in a single currency.
 *
 * Stored as [amountMinor] minor units (cents / paise) in a `Long` — never floating point.
 * Arithmetic between different currencies throws [CurrencyMismatchException]; convert first with
 * [convertTo] using an explicit rate.
 */
data class Money(
    val amountMinor: Long,
    val currency: Currency,
) : Comparable<Money> {

    val isZero: Boolean get() = amountMinor == 0L
    val isNegative: Boolean get() = amountMinor < 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = Math.addExact(amountMinor, other.amountMinor))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = Math.subtractExact(amountMinor, other.amountMinor))
    }

    operator fun unaryMinus(): Money = copy(amountMinor = Math.negateExact(amountMinor))

    fun abs(): Money = if (isNegative) -this else this

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    /** The amount in major units (e.g. `12.34` for 1234 cents), scaled to the currency's minor digits. */
    fun toMajor(): BigDecimal = BigDecimal.valueOf(amountMinor, currency.minorDigits)

    /**
     * Converts this amount into [target] using [rate], where `1 [currency] == rate [target]`.
     * The result is rounded to [target]'s minor unit with [RoundingMode.HALF_EVEN] (banker's rounding).
     */
    fun convertTo(target: Currency, rate: BigDecimal): Money {
        require(rate.signum() >= 0) { "Exchange rate must not be negative: $rate" }
        val targetMinor = toMajor()
            .multiply(rate)
            .movePointRight(target.minorDigits)
            .setScale(0, RoundingMode.HALF_EVEN)
        return Money(targetMinor.longValueExact(), target)
    }

    private fun requireSameCurrency(other: Money) {
        if (currency != other.currency) throw CurrencyMismatchException(currency, other.currency)
    }

    override fun toString(): String = "${toMajor().toPlainString()} ${currency.code}"

    companion object {
        fun zero(currency: Currency): Money = Money(0L, currency)

        /** Builds a [Money] from a major-unit amount (`12.34`), rounding half-even to the minor unit. */
        fun of(major: BigDecimal, currency: Currency): Money =
            Money(
                major.setScale(currency.minorDigits, RoundingMode.HALF_EVEN).unscaledValue().longValueExact(),
                currency,
            )

        fun of(major: String, currency: Currency): Money = of(BigDecimal(major), currency)
    }
}

/** Sums the values, which must all be in [currency]; an empty collection yields zero in [currency]. */
fun Iterable<Money>.sumIn(currency: Currency): Money = fold(Money.zero(currency)) { acc, m -> acc + m }

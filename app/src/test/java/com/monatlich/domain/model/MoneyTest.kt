package com.monatlich.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    private val eur = Currency.EUR
    private val inr = Currency.INR
    private val usd = Currency.USD

    @Test
    fun `plus adds minor units of the same currency`() {
        assertEquals(Money(1500, eur), Money(1000, eur) + Money(500, eur))
    }

    @Test
    fun `minus subtracts and can go negative`() {
        assertEquals(Money(-250, eur), Money(250, eur) - Money(500, eur))
        assertTrue((Money(250, eur) - Money(500, eur)).isNegative)
    }

    @Test
    fun `plus with mismatched currencies throws`() {
        assertThrows(CurrencyMismatchException::class.java) { Money(100, eur) + Money(100, usd) }
    }

    @Test
    fun `minus with mismatched currencies throws`() {
        assertThrows(CurrencyMismatchException::class.java) { Money(100, eur) - Money(100, inr) }
    }

    @Test
    fun `compareTo with mismatched currencies throws`() {
        assertThrows(CurrencyMismatchException::class.java) { Money(100, eur).compareTo(Money(100, inr)) }
    }

    @Test
    fun `plus overflow throws instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE, eur) + Money(1, eur) }
    }

    @Test
    fun `toMajor scales by the currency's minor digits`() {
        assertEquals(BigDecimal("12.34"), Money(1234, eur).toMajor())
        assertEquals(BigDecimal("-0.05"), Money(-5, usd).toMajor())
        assertEquals(BigDecimal("0.00"), Money.zero(inr).toMajor())
    }

    @Test
    fun `of parses major amounts and rounds half-even to the minor unit`() {
        assertEquals(Money(1234, eur), Money.of("12.34", eur))
        assertEquals(Money(1234, eur), Money.of("12.345", eur)) // 4 is even -> stays
        assertEquals(Money(1236, eur), Money.of("12.355", eur)) // 5 is odd -> rounds up to 6
        assertEquals(Money(100000, inr), Money.of(BigDecimal("1000"), inr))
    }

    @Test
    fun `convertTo multiplies by the rate and rounds half-even`() {
        // 1234.56 INR * 0.0108 = 13.333248 EUR -> 13.33
        assertEquals(Money(1333, eur), Money(123456, inr).convertTo(eur, BigDecimal("0.0108")))
        // 100.00 EUR * 1.0837 = 108.37 USD exactly
        assertEquals(Money(10837, usd), Money(10000, eur).convertTo(usd, BigDecimal("1.0837")))
    }

    @Test
    fun `convertTo uses banker's rounding on exact halves`() {
        // 1.25 * 0.1 = 0.125 -> 12 (round half to even: 2 is even)
        assertEquals(Money(12, eur), Money(125, usd).convertTo(eur, BigDecimal("0.1")))
        // 1.35 * 0.1 = 0.135 -> 14 (3 is odd, round up)
        assertEquals(Money(14, eur), Money(135, usd).convertTo(eur, BigDecimal("0.1")))
    }

    @Test
    fun `convertTo with rate one changes only the currency`() {
        assertEquals(Money(999, inr), Money(999, eur).convertTo(inr, BigDecimal.ONE))
    }

    @Test
    fun `convertTo preserves sign`() {
        assertEquals(Money(-1333, eur), Money(-123456, inr).convertTo(eur, BigDecimal("0.0108")))
    }

    @Test
    fun `convertTo rejects negative rates`() {
        assertThrows(IllegalArgumentException::class.java) { Money(100, eur).convertTo(usd, BigDecimal("-1")) }
    }

    @Test
    fun `unaryMinus and abs`() {
        assertEquals(Money(-100, eur), -Money(100, eur))
        assertEquals(Money(100, eur), Money(-100, eur).abs())
        assertFalse(Money(100, eur).isNegative)
    }

    @Test
    fun `sumIn folds a list and yields zero for empty input`() {
        assertEquals(Money(600, eur), listOf(Money(100, eur), Money(200, eur), Money(300, eur)).sumIn(eur))
        assertEquals(Money.zero(usd), emptyList<Money>().sumIn(usd))
        assertThrows(CurrencyMismatchException::class.java) { listOf(Money(1, eur), Money(1, usd)).sumIn(eur) }
    }

    @Test
    fun `Currency fromCode round-trips and rejects unknown codes`() {
        Currency.entries.forEach { assertEquals(it, Currency.fromCode(it.code)) }
        assertThrows(IllegalArgumentException::class.java) { Currency.fromCode("XXX") }
    }
}

package com.monatlich.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    /** Locale data may use NBSP / narrow NBSP as grouping or currency separators. */
    private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun usdUsesCommaGroupingAndDot() {
        assertEquals("$1,234.56", formatMinor(123_456, "USD"))
    }

    @Test
    fun eurUsesGermanConventions() {
        assertEquals("1.234,56 €", formatMinor(123_456, "EUR").normalizeSpaces())
    }

    @Test
    fun inrUsesLakhGrouping() {
        assertEquals("₹1,23,456.00", formatMinor(12_345_600, "INR"))
        assertEquals("₹999.00", formatMinor(99_900, "INR"))
        assertEquals("₹12,34,567.89", formatMinor(123_456_789, "INR"))
        assertEquals("-₹1,000.50", formatMinor(-100_050, "INR"))
    }

    @Test
    fun zeroAndNegative() {
        assertEquals("$0.00", formatMinor(0, "USD"))
        assertEquals("-$5.00", formatMinor(-500, "USD"))
    }
}

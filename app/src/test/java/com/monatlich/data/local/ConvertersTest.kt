package com.monatlich.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `YearMonth is stored as ISO yyyy-MM`() {
        assertEquals("2026-09", converters.yearMonthToString(YearMonth.of(2026, 9)))
        assertEquals("2026-12", converters.yearMonthToString(YearMonth.of(2026, 12)))
        assertEquals(YearMonth.of(2026, 9), converters.stringToYearMonth("2026-09"))
    }

    @Test
    fun `LocalDate is stored as ISO yyyy-MM-dd`() {
        assertEquals("2026-09-05", converters.localDateToString(LocalDate.of(2026, 9, 5)))
        assertEquals(LocalDate.of(2026, 9, 5), converters.stringToLocalDate("2026-09-05"))
    }

    @Test
    fun `BigDecimal is stored as plain text without exponent and keeps scale`() {
        assertEquals("0.0108", converters.bigDecimalToString(BigDecimal("0.0108")))
        assertEquals("0.00000001", converters.bigDecimalToString(BigDecimal("1E-8")))
        assertEquals(BigDecimal("0.0108"), converters.stringToBigDecimal("0.0108"))
        assertEquals(4, converters.stringToBigDecimal("0.0108")!!.scale())
    }

    @Test
    fun `Instant is stored as epoch millis`() {
        val instant = Instant.parse("2026-09-13T10:15:30.123Z")
        assertEquals(instant.toEpochMilli(), converters.instantToLong(instant))
        assertEquals(instant, converters.longToInstant(instant.toEpochMilli()))
    }

    @Test
    fun `ISO month strings sort chronologically as text`() {
        val months = listOf(YearMonth.of(2026, 10), YearMonth.of(2025, 12), YearMonth.of(2026, 9))
        val stored = months.map { converters.yearMonthToString(it)!! }
        assertEquals(months.sorted().map { it.toString() }, stored.sorted())
    }

    @Test
    fun `nulls pass through`() {
        assertNull(converters.yearMonthToString(null))
        assertNull(converters.stringToYearMonth(null))
        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
        assertNull(converters.bigDecimalToString(null))
        assertNull(converters.stringToBigDecimal(null))
        assertNull(converters.instantToLong(null))
        assertNull(converters.longToInstant(null))
    }
}

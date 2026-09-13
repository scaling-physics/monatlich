package com.monatlich.ui.budget

import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputStateTest {

    private fun state(currency: Currency = Currency.EUR) = AmountInputState(currency)

    private fun AmountInputState.type(keys: String) = apply { keys.forEach { append(it) } }

    @Test
    fun `empty state is zero`() {
        val s = state()
        assertTrue(s.isEmpty)
        assertEquals("", s.text)
        assertEquals("0", s.display)
        assertEquals(0L, s.minorUnits)
        assertEquals(Money.zero(Currency.EUR), s.money)
    }

    @Test
    fun `typing integer digits then decimals builds the amount`() {
        val s = state().type("450")
        assertEquals("450", s.display)
        assertEquals(45_000L, s.minorUnits)

        s.append('.')
        assertEquals("450.", s.display)
        assertEquals(45_000L, s.minorUnits)

        s.type("50")
        assertEquals("450.50", s.display)
        assertEquals(45_050L, s.minorUnits)
    }

    @Test
    fun `single fraction digit counts as tens of minor units`() {
        val s = state().type("12.5")
        assertEquals("12.5", s.display)
        assertEquals(1_250L, s.minorUnits)
    }

    @Test
    fun `leading zeros are dropped`() {
        val s = state()
        assertFalse(s.append('0'))
        assertFalse(s.append('0'))
        assertTrue(s.isEmpty)
        assertTrue(s.append('7'))
        assertEquals("7", s.display)
        assertEquals(700L, s.minorUnits)
    }

    @Test
    fun `separator on empty field yields zero point`() {
        val s = state()
        assertTrue(s.append('.'))
        assertEquals("0.", s.display)
        assertFalse(s.isEmpty)
        s.append('5')
        assertEquals("0.5", s.display)
        assertEquals(50L, s.minorUnits)
    }

    @Test
    fun `digit after lone zero replaces it`() {
        val s = state().type(".")
        s.backspace() // "0"
        assertEquals("0", s.display)
        assertFalse(s.append('0'))
        assertTrue(s.append('3'))
        assertEquals("3", s.display)
    }

    @Test
    fun `comma is accepted as decimal separator`() {
        val s = state().type("9,99")
        assertEquals("9.99", s.display)
        assertEquals(999L, s.minorUnits)
    }

    @Test
    fun `second separator is ignored`() {
        val s = state().type("1.")
        assertFalse(s.append('.'))
        assertFalse(s.append(','))
        assertEquals("1.", s.display)
    }

    @Test
    fun `at most two decimals for two-digit currencies`() {
        val s = state(Currency.INR).type("1.234")
        assertEquals("1.23", s.display)
        assertEquals(123L, s.minorUnits)
        assertFalse(s.append('9'))
    }

    @Test
    fun `non numeric characters are ignored`() {
        val s = state().type("4a-b5 €")
        assertEquals("45", s.display)
    }

    @Test
    fun `backspace walks back across the decimal point`() {
        val s = state().type("450.50")
        assertTrue(s.backspace())
        assertEquals("450.5", s.display)
        assertEquals(45_050L, s.minorUnits)
        assertTrue(s.backspace())
        assertEquals("450.", s.display)
        assertTrue(s.backspace())
        assertEquals("450", s.display)
        assertEquals(45_000L, s.minorUnits)
        assertTrue(s.backspace())
        assertEquals("45", s.display)
        assertTrue(s.backspace())
        assertTrue(s.backspace())
        assertTrue(s.isEmpty)
        assertFalse(s.backspace())
        assertEquals("0", s.display)
        assertEquals(0L, s.minorUnits)
    }

    @Test
    fun `clear resets to empty`() {
        val s = state().type("123.45")
        s.clear()
        assertTrue(s.isEmpty)
        assertEquals(0L, s.minorUnits)
        assertEquals("", s.text)
    }

    @Test
    fun `integer digits are capped at twelve and never overflow`() {
        val s = state().type("9".repeat(20))
        assertEquals("9".repeat(12), s.display)
        assertFalse(s.append('1'))
        assertEquals(99_999_999_999_900L, s.minorUnits)

        // Decimals still allowed after the cap, and the total stays comfortably inside a Long.
        s.type(".99")
        assertEquals("999999999999.99", s.display)
        assertEquals(99_999_999_999_999L, s.minorUnits)
    }

    @Test
    fun `switching currency keeps the digits`() {
        val s = state(Currency.EUR).type("450.50")
        s.currency = Currency.USD
        assertEquals("450.50", s.display)
        assertEquals(45_050L, s.minorUnits)
        assertEquals(Money(45_050L, Currency.USD), s.money)
        s.currency = Currency.INR
        assertEquals(Money(45_050L, Currency.INR), s.money)
    }

    @Test
    fun `setMinorUnits loads an existing amount and drops trailing fraction zeros`() {
        val s = state()
        s.setMinorUnits(45_000L)
        assertEquals("450", s.display)
        assertEquals(45_000L, s.minorUnits)

        s.setMinorUnits(45_050L)
        assertEquals("450.5", s.display)
        assertEquals(45_050L, s.minorUnits)

        s.setMinorUnits(45_005L)
        assertEquals("450.05", s.display)
        assertEquals(45_005L, s.minorUnits)

        s.setMinorUnits(5L)
        assertEquals("0.05", s.display)
        assertEquals(5L, s.minorUnits)

        s.setMinorUnits(0L)
        assertTrue(s.isEmpty)

        s.setMinorUnits(-1_250L)
        assertEquals("12.5", s.display)
    }

    @Test
    fun `setMinorUnits then typing continues editing`() {
        val s = state()
        s.setMinorUnits(45_000L)
        s.append('0')
        assertEquals("4500", s.display)
        s.backspace()
        s.backspace()
        s.type(".7")
        assertEquals("45.7", s.display)
        assertEquals(4_570L, s.minorUnits)
    }

    @Test
    fun `applyEdit replays typing, deleting and pasting`() {
        val s = state()
        s.applyEdit("4")
        s.applyEdit("45")
        s.applyEdit("450")
        assertEquals("450", s.text)

        s.applyEdit("450.")
        s.applyEdit("450.5")
        assertEquals(45_050L, s.minorUnits)

        s.applyEdit("450")
        assertEquals("450", s.text)
        assertEquals(45_000L, s.minorUnits)

        s.applyEdit("")
        assertTrue(s.isEmpty)

        s.applyEdit("1234.567")
        assertEquals("1234.56", s.text)

        // An edit in the middle keeps the prefix and replays the rest.
        s.applyEdit("12934.56")
        assertEquals("12934.56", s.text)

        // Invalid characters in a paste are dropped.
        s.applyEdit("12934.56abc")
        assertEquals("12934.56", s.text)
    }
}

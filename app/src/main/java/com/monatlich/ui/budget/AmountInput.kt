package com.monatlich.ui.budget

import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.Money

/**
 * Calculator-style amount entry: digits are appended to an integer part until a decimal separator
 * is typed, after which at most [Currency.minorDigits] fraction digits are accepted.
 *
 * Plain Kotlin, no Compose dependency, so the set-budget sheet (M3) and the add-expense sheet
 * (M4) share one state machine. Callers snapshot [display] / [text] / [minorUnits] into their
 * `UiState` after each mutation.
 *
 * Rules:
 * - Leading zeros are dropped (`0`, `0`, `4` → `4`); a separator on an empty field yields `0.`.
 * - The integer part is capped at [MAX_INTEGER_DIGITS] so [minorUnits] can never overflow a `Long`.
 * - Both `.` and `,` are accepted as the decimal separator (keyboards differ by locale); the
 *   display always uses `.` — locale formatting happens at the UI edge with the formatted preview.
 * - [backspace] walks back through fraction digits, then the separator, then integer digits.
 * - Switching [currency] keeps the digits typed so far (fraction truncated if the new currency has
 *   fewer minor digits).
 */
class AmountInputState(currency: Currency) {

    private val integerPart = StringBuilder()
    private val fractionPart = StringBuilder()
    private var hasSeparator = false

    var currency: Currency = currency
        set(value) {
            field = value
            if (value.minorDigits == 0) {
                hasSeparator = false
                fractionPart.clear()
            } else if (fractionPart.length > value.minorDigits) {
                fractionPart.setLength(value.minorDigits)
            }
        }

    /** True when nothing has been typed; [minorUnits] is `0` and [text] is empty. */
    val isEmpty: Boolean get() = integerPart.isEmpty() && !hasSeparator

    /** The raw typed text, `""` when empty — what a text field should show. */
    val text: String
        get() = if (isEmpty) "" else buildString {
            append(integerPart)
            if (hasSeparator) append(SEPARATOR).append(fractionPart)
        }

    /** What a calculator display shows: [text], or `"0"` when empty. */
    val display: String get() = if (isEmpty) "0" else text

    /** The typed amount in the currency's minor units (cents / paise); `0` when empty. */
    val minorUnits: Long
        get() {
            val digits = currency.minorDigits
            val integer = if (integerPart.isEmpty()) 0L else integerPart.toString().toLong()
            val fraction = fractionPart.toString().padEnd(digits, '0').ifEmpty { "0" }.toLong()
            return integer * POW10[digits] + fraction
        }

    val money: Money get() = Money(minorUnits, currency)

    /**
     * Feeds one key press. Returns `true` if the state changed, `false` if the character was
     * ignored (non-digit, extra separator, cap reached, redundant leading zero).
     */
    fun append(char: Char): Boolean = when {
        char in '0'..'9' -> appendDigit(char)
        char == '.' || char == ',' -> appendSeparator()
        else -> false
    }

    /** Removes the last typed character; returns `false` when there was nothing to remove. */
    fun backspace(): Boolean = when {
        fractionPart.isNotEmpty() -> {
            fractionPart.setLength(fractionPart.length - 1)
            true
        }
        hasSeparator -> {
            hasSeparator = false
            true
        }
        integerPart.isNotEmpty() -> {
            integerPart.setLength(integerPart.length - 1)
            true
        }
        else -> false
    }

    fun clear() {
        integerPart.clear()
        fractionPart.clear()
        hasSeparator = false
    }

    /**
     * Replaces the contents with [amountMinor], e.g. `45_050` → `450.5` and `45_000` → `450`
     * (trailing fraction zeros are dropped so editing needs fewer backspaces). Negative amounts
     * are entered as their absolute value; the sign is not part of this input.
     */
    fun setMinorUnits(amountMinor: Long) {
        clear()
        val digits = currency.minorDigits
        val absolute = if (amountMinor < 0) -amountMinor else amountMinor
        val integer = absolute / POW10[digits]
        val fraction = (absolute % POW10[digits]).toString().padStart(digits, '0').trimEnd('0')
        if (integer != 0L || fraction.isNotEmpty()) {
            integerPart.append(integer.toString().take(MAX_INTEGER_DIGITS))
        }
        if (fraction.isNotEmpty()) {
            if (integerPart.isEmpty()) integerPart.append('0')
            hasSeparator = true
            fractionPart.append(fraction)
        }
    }

    /**
     * Applies a text-field edit: the common prefix of [text] and [newText] is kept, the removed
     * tail is undone with [backspace] and the added tail replayed through [append]. Because the
     * caller keeps the cursor at the end, this covers typing, deleting and pasting alike.
     */
    fun applyEdit(newText: String) {
        val current = text
        val prefix = current.commonPrefixWith(newText).length
        repeat(current.length - prefix) { backspace() }
        newText.substring(prefix).forEach { append(it) }
    }

    private fun appendDigit(char: Char): Boolean {
        if (hasSeparator) {
            if (fractionPart.length >= currency.minorDigits) return false
            fractionPart.append(char)
            return true
        }
        return when {
            char == '0' && integerPart.isEmpty() -> false
            integerPart.length == 1 && integerPart[0] == '0' -> {
                if (char == '0') return false
                integerPart.setCharAt(0, char)
                true
            }
            integerPart.length >= MAX_INTEGER_DIGITS -> false
            else -> {
                integerPart.append(char)
                true
            }
        }
    }

    private fun appendSeparator(): Boolean {
        if (hasSeparator || currency.minorDigits == 0) return false
        if (integerPart.isEmpty()) integerPart.append('0')
        hasSeparator = true
        return true
    }

    companion object {
        const val SEPARATOR = '.'

        /** 12 integer digits: 999 999 999 999.99 in cents is ~1e14, far below `Long.MAX_VALUE`. */
        const val MAX_INTEGER_DIGITS = 12

        private val POW10 = longArrayOf(1L, 10L, 100L, 1_000L, 10_000L)
    }
}

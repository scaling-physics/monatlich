package com.monatlich.ui.common

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * Formats a minor-unit amount (cents / paise) for display using the currency's own locale
 * conventions: `$1,234.56`, `1.234,56 €`, `₹1,23,456.00`.
 *
 * UI-edge helper only. The `Money` value type itself lives in `domain/model` (M2); this function
 * deliberately takes primitives so the UI shell has no dependency on the domain layer.
 */
fun formatMinor(amountMinor: Long, currencyCode: String): String {
    if (currencyCode == "INR") return formatIndianRupee(amountMinor)
    val currency = Currency.getInstance(currencyCode)
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    val format = NumberFormat.getCurrencyInstance(localeFor(currencyCode)).apply {
        this.currency = currency
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    val major = BigDecimal.valueOf(amountMinor).movePointLeft(digits)
    return format.format(major)
}

/** Locale whose number conventions match the currency. Falls back to the device locale. */
private fun localeFor(currencyCode: String): Locale = when (currencyCode) {
    "USD" -> Locale.US
    "EUR" -> Locale.GERMANY
    else -> Locale.getDefault()
}

/**
 * Indian grouping (`₹1,23,456.00`): the last three digits, then pairs. `java.text.DecimalFormat`
 * on the JVM ignores secondary grouping while Android's ICU-backed one honours it, so this is done
 * by hand to keep unit tests and the device in agreement.
 */
private fun formatIndianRupee(amountMinor: Long): String {
    val sign = if (amountMinor < 0) "-" else ""
    val absolute = abs(amountMinor)
    val whole = (absolute / 100).toString()
    val fraction = (absolute % 100).toString().padStart(2, '0')
    val grouped = if (whole.length <= 3) {
        whole
    } else {
        val head = whole.dropLast(3)
        val pairs = head.reversed().chunked(2).joinToString(",").reversed()
        "$pairs,${whole.takeLast(3)}"
    }
    return "$sign₹$grouped.$fraction"
}

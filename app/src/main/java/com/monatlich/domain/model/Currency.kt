package com.monatlich.domain.model

/**
 * Supported currencies, keyed by ISO 4217 code.
 *
 * The list is intentionally small for now (see AGENT.md roadmap); adding a currency means adding
 * an entry here and a seed row in the exchange-rate table.
 */
enum class Currency(
    /** ISO 4217 alphabetic code, e.g. `"EUR"`. This is what gets persisted. */
    val code: String,
    /** Number of digits after the decimal point in the currency's minor unit (cents, paise). */
    val minorDigits: Int,
    /** Display symbol; formatting itself happens at the UI edge. */
    val symbol: String,
) {
    USD("USD", 2, "$"),
    EUR("EUR", 2, "€"),
    INR("INR", 2, "₹");

    companion object {
        fun fromCode(code: String): Currency =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unsupported currency code: $code")
    }
}

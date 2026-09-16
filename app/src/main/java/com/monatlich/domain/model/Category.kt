package com.monatlich.domain.model

/**
 * A global, reusable spending category. Categories are never deleted — [archived] hides them from
 * pickers while historical transactions keep pointing at them.
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    /** Material Icons name, e.g. `"ShoppingCart"`; resolved to an `ImageVector` in the UI layer. */
    val icon: String,
    /** ARGB color as a `Long` (e.g. `0xFF6B1F2A`), resolved to a Compose `Color` in the UI layer. */
    val color: Long,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /**
     * When true, [GetMonthSummary] carries this category's unspent budget (or overspend) from
     * every prior month into the current one — a running balance for saving toward something
     * across months (a trip, a big purchase) rather than a fresh allowance each month.
     */
    val rolloverEnabled: Boolean = false,
)

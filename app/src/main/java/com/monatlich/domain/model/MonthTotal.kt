package com.monatlich.domain.model

import java.time.YearMonth

/** One point on the month-over-month trend chart: base-currency totals for a single month. */
data class MonthTotal(
    val month: YearMonth,
    val spentInBase: Money,
    val incomeInBase: Money,
)

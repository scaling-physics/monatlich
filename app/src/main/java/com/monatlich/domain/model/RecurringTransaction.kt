package com.monatlich.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * A rule that produces one [Transaction] per month (rent, subscriptions, salary).
 *
 * A rule covers every month in `[startMonth, endMonth]` ([endMonth] inclusive; `null` = open
 * ended). Generated transactions carry [Transaction.recurringId] so generation is idempotent and
 * the origin stays visible. The rule's [amount] is snapshotted into each generated transaction
 * together with the exchange rate *at generation time*; editing a rule never rewrites history.
 */
data class RecurringTransaction(
    val id: Long = 0L,
    val categoryId: Long,
    val amount: Money,
    val type: TransactionType,
    val note: String? = null,
    /** 1..31; clamped to the month's length when generating (31 → Feb 28/29). */
    val dayOfMonth: Int,
    val startMonth: YearMonth,
    /** Last month the rule applies to, inclusive; `null` means it runs until deactivated. */
    val endMonth: YearMonth? = null,
    val active: Boolean = true,
) {
    init {
        require(dayOfMonth in MIN_DAY..MAX_DAY) { "dayOfMonth must be in $MIN_DAY..$MAX_DAY: $dayOfMonth" }
        require(endMonth == null || !endMonth.isBefore(startMonth)) {
            "endMonth $endMonth must not be before startMonth $startMonth"
        }
    }

    /** True when [month] lies within `[startMonth, endMonth]`. Ignores [active]. */
    fun covers(month: YearMonth): Boolean =
        !month.isBefore(startMonth) && (endMonth == null || !month.isAfter(endMonth))

    /** True when an active rule should produce a transaction in [month]. */
    fun appliesTo(month: YearMonth): Boolean = active && covers(month)

    /** The date the generated transaction lands on in [month]: [dayOfMonth] clamped to the month. */
    fun dateIn(month: YearMonth): LocalDate = month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))

    companion object {
        const val MIN_DAY = 1
        const val MAX_DAY = 31
    }
}

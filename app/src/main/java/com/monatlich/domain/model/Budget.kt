package com.monatlich.domain.model

import java.time.YearMonth

/** The amount planned for one [categoryId] in one [month]. At most one budget per (category, month). */
data class Budget(
    val id: Long = 0L,
    val categoryId: Long,
    val month: YearMonth,
    val amount: Money,
)

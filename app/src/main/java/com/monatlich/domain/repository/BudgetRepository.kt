package com.monatlich.domain.repository

import com.monatlich.domain.model.Budget
import com.monatlich.domain.model.Money
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface BudgetRepository {
    fun observeForMonth(month: YearMonth): Flow<List<Budget>>

    fun observe(categoryId: Long, month: YearMonth): Flow<Budget?>

    suspend fun get(categoryId: Long, month: YearMonth): Budget?

    /** Creates or replaces the budget for ([categoryId], [month]). Returns the budget's id. */
    suspend fun set(categoryId: Long, month: YearMonth, amount: Money): Long

    suspend fun remove(categoryId: Long, month: YearMonth)

    /**
     * Copies every budget of [from] into [to], skipping categories that already have a budget in
     * [to]. Returns the number of budgets created.
     */
    suspend fun copy(from: YearMonth, to: YearMonth): Int
}

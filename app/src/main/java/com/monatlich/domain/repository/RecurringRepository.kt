package com.monatlich.domain.repository

import com.monatlich.domain.model.RecurringTransaction
import kotlinx.coroutines.flow.Flow

interface RecurringRepository {
    /** Every rule, active or not, ordered by day of month then id. */
    fun observeAll(): Flow<List<RecurringTransaction>>

    fun observe(id: Long): Flow<RecurringTransaction?>

    suspend fun get(id: Long): RecurringTransaction?

    /** Inserts [rule] (its [RecurringTransaction.id] is ignored); returns the new id. */
    suspend fun add(rule: RecurringTransaction): Long

    suspend fun update(rule: RecurringTransaction)

    suspend fun setActive(id: Long, active: Boolean)

    /** Deletes the rule; transactions it generated keep existing with their link cleared. */
    suspend fun delete(id: Long)
}

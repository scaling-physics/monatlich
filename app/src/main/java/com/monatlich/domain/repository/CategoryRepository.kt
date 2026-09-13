package com.monatlich.domain.repository

import com.monatlich.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    /** All categories including archived ones, ordered by [Category.sortOrder]. */
    fun observeAll(): Flow<List<Category>>

    /** Only non-archived categories, ordered by [Category.sortOrder]. */
    fun observeActive(): Flow<List<Category>>

    fun observe(id: Long): Flow<Category?>

    suspend fun get(id: Long): Category?

    /** Inserts [category] (its [Category.id] is ignored) at the end of the sort order; returns the new id. */
    suspend fun add(category: Category): Long

    suspend fun update(category: Category)

    /** Rewrites [Category.sortOrder] so that categories appear in the order of [orderedIds]. */
    suspend fun reorder(orderedIds: List<Long>)

    suspend fun setArchived(id: Long, archived: Boolean)
}

package com.monatlich.ui.categories

import com.monatlich.domain.model.Category
import com.monatlich.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CategoryRepository] for ViewModel tests. Mirrors the Room implementation's ordering
 * (`sortOrder, id`) and `add` semantics (id ignored, appended at the end).
 */
class InMemoryCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    /** Every `orderedIds` list passed to [reorder], for assertions. */
    val reorderCalls = mutableListOf<List<Long>>()

    val sorted: List<Category>
        get() = state.value.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    override fun observeAll(): Flow<List<Category>> =
        state.map { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.id })) }

    override fun observeActive(): Flow<List<Category>> =
        observeAll().map { list -> list.filter { !it.archived } }

    override fun observe(id: Long): Flow<Category?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: Long): Category? = state.value.firstOrNull { it.id == id }

    override suspend fun add(category: Category): Long {
        val id = nextId++
        val nextSortOrder = (state.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
        state.update { it + category.copy(id = id, sortOrder = nextSortOrder) }
        return id
    }

    override suspend fun update(category: Category) =
        state.update { list -> list.map { if (it.id == category.id) category else it } }

    override suspend fun reorder(orderedIds: List<Long>) {
        reorderCalls += orderedIds
        state.update { list ->
            list.map { c -> orderedIds.indexOf(c.id).takeIf { it >= 0 }?.let { c.copy(sortOrder = it) } ?: c }
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) =
        state.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
}

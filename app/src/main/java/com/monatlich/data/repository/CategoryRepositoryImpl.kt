package com.monatlich.data.repository

import com.monatlich.data.local.CategoryDao
import com.monatlich.domain.model.Category
import com.monatlich.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Category>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Category?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun get(id: Long): Category? = dao.getById(id)?.toDomain()

    override suspend fun add(category: Category): Long =
        dao.insertAtEnd(category.copy(id = 0L).toEntity())

    override suspend fun update(category: Category) = dao.update(category.toEntity())

    override suspend fun reorder(orderedIds: List<Long>) = dao.reorder(orderedIds)

    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)
}

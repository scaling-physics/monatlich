package com.monatlich.data.repository

import com.monatlich.data.local.RecurringTransactionDao
import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RecurringRepositoryImpl @Inject constructor(
    private val dao: RecurringTransactionDao,
) : RecurringRepository {

    override fun observeAll(): Flow<List<RecurringTransaction>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<RecurringTransaction?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun get(id: Long): RecurringTransaction? = dao.getById(id)?.toDomain()

    override suspend fun add(rule: RecurringTransaction): Long = dao.insert(rule.copy(id = 0L).toEntity())

    override suspend fun update(rule: RecurringTransaction) = dao.update(rule.toEntity())

    override suspend fun setActive(id: Long, active: Boolean) = dao.setActive(id, active)

    override suspend fun delete(id: Long) = dao.deleteById(id)
}

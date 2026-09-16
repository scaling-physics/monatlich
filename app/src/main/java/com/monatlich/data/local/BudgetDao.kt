package com.monatlich.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE month = :month ORDER BY id")
    fun observeForMonth(month: YearMonth): Flow<List<BudgetEntity>>

    /** Every budget ever set, any month; backs rollover, which sums a category's whole history. */
    @Query("SELECT * FROM budgets ORDER BY month")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE month = :month ORDER BY id")
    suspend fun getForMonth(month: YearMonth): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND month = :month")
    fun observe(categoryId: Long, month: YearMonth): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND month = :month")
    suspend fun get(categoryId: Long, month: YearMonth): BudgetEntity?

    /** Aborts with a constraint violation if a budget for the same (category, month) exists. */
    @Insert
    suspend fun insert(entity: BudgetEntity): Long

    @Update
    suspend fun update(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId AND month = :month")
    suspend fun delete(categoryId: Long, month: YearMonth)

    /** Inserts, or updates the existing row for the same (category, month). Returns the row id. */
    @Transaction
    suspend fun upsert(entity: BudgetEntity): Long {
        val existing = get(entity.categoryId, entity.month) ?: return insert(entity)
        update(entity.copy(id = existing.id))
        return existing.id
    }
}

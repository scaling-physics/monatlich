package com.monatlich.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.monatlich.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE month = :month ORDER BY date DESC, id DESC")
    fun observeForMonth(month: YearMonth): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE month = :month AND categoryId = :categoryId " +
            "ORDER BY date DESC, id DESC",
    )
    fun observeForCategoryAndMonth(categoryId: Long, month: YearMonth): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Per-category sums for [month] and [type], grouped by currency *and* stored rate so the
     * repository can convert each group to base with its own `rateToBase`.
     */
    @Query(
        "SELECT categoryId, currencyCode, rateToBase, SUM(amountMinor) AS totalMinor " +
            "FROM transactions WHERE month = :month AND type = :type " +
            "GROUP BY categoryId, currencyCode, rateToBase " +
            "ORDER BY categoryId, currencyCode, rateToBase",
    )
    fun observeTotalsByCategory(month: YearMonth, type: TransactionType): Flow<List<CategoryTotalRow>>
}

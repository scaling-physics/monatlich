package com.monatlich.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.monatlich.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@Entity(tableName = CategoryEntity.TABLE)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String,
    val color: Long,
    val sortOrder: Int,
    val archived: Boolean = false,
) {
    companion object {
        const val TABLE = "categories"
    }
}

@Entity(
    tableName = BudgetEntity.TABLE,
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["categoryId", "month"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    val month: YearMonth,
    val amountMinor: Long,
    val currencyCode: String,
) {
    companion object {
        const val TABLE = "budgets"
    }
}

@Entity(
    tableName = TransactionEntity.TABLE,
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["month"]), Index(value = ["categoryId"])],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    /** Denormalised from [date] so month queries hit the index; kept in sync by the repository. */
    val month: YearMonth,
    val date: LocalDate,
    val amountMinor: Long,
    val currencyCode: String,
    val rateToBase: BigDecimal,
    val type: TransactionType,
    val note: String?,
) {
    companion object {
        const val TABLE = "transactions"
    }
}

@Entity(tableName = ExchangeRateEntity.TABLE)
data class ExchangeRateEntity(
    @PrimaryKey val currencyCode: String,
    val rateToBase: BigDecimal,
    val updatedAt: Instant,
) {
    companion object {
        const val TABLE = "exchange_rates"
    }
}

/** Result row of [TransactionDao.observeTotalsByCategory]: one line per (category, currency, rate). */
data class CategoryTotalRow(
    val categoryId: Long,
    val currencyCode: String,
    val rateToBase: BigDecimal,
    val totalMinor: Long,
)

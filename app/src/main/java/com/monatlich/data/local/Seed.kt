package com.monatlich.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.monatlich.domain.model.Currency
import java.math.BigDecimal
import java.time.Instant

/**
 * Default rows inserted exactly once, when the database file is first created.
 *
 * Categories get an icon name from Material Icons (resolved in the UI layer) and an ARGB color.
 * Every supported currency gets a placeholder rate of `1` so lookups never miss; the user edits
 * the real rates in Settings.
 */
object Seed {

    val categories: List<CategoryEntity> = listOf(
        CategoryEntity(name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = 0),
        CategoryEntity(name = "Rent", icon = "Home", color = 0xFF6B1F2A, sortOrder = 1),
        CategoryEntity(name = "Transport", icon = "DirectionsBus", color = 0xFF1565C0, sortOrder = 2),
        CategoryEntity(name = "Eating out", icon = "Restaurant", color = 0xFFEF6C00, sortOrder = 3),
        CategoryEntity(name = "Fun", icon = "Celebration", color = 0xFF7B1FA2, sortOrder = 4),
        CategoryEntity(name = "Health", icon = "Favorite", color = 0xFFC62828, sortOrder = 5),
        CategoryEntity(name = "Other", icon = "MoreHoriz", color = 0xFF546E7A, sortOrder = 6),
    )

    fun exchangeRates(now: Instant): List<ExchangeRateEntity> =
        Currency.entries.map { ExchangeRateEntity(currencyCode = it.code, rateToBase = BigDecimal.ONE, updatedAt = now) }
}

/** Room callback that writes [Seed] into a freshly created database. */
class SeedCallback(private val now: () -> Instant = Instant::now) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val converters = Converters()
        Seed.categories.forEach { c ->
            db.execSQL(
                "INSERT INTO ${CategoryEntity.TABLE} (name, icon, color, sortOrder, archived, rolloverEnabled) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(c.name, c.icon, c.color, c.sortOrder, if (c.archived) 1 else 0, if (c.rolloverEnabled) 1 else 0),
            )
        }
        Seed.exchangeRates(now()).forEach { r ->
            db.execSQL(
                "INSERT OR IGNORE INTO ${ExchangeRateEntity.TABLE} (currencyCode, rateToBase, updatedAt) VALUES (?, ?, ?)",
                arrayOf<Any?>(r.currencyCode, converters.bigDecimalToString(r.rateToBase), converters.instantToLong(r.updatedAt)),
            )
        }
    }
}

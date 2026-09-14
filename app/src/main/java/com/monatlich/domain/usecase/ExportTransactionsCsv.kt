package com.monatlich.domain.usecase

import com.monatlich.domain.model.Transaction
import com.monatlich.domain.repository.CategoryRepository
import com.monatlich.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Renders every transaction ever logged as CSV text: `Date,Type,Category,Amount,Currency,Note`,
 * newest first. Amounts are major units (`"45.00"`, not minor-unit cents) so the file reads
 * naturally in a spreadsheet; each transaction keeps its own entry currency, not the base one.
 */
class ExportTransactionsCsv @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(): String {
        val all = transactions.getAll()
        val categoryNames = categories.observeAll().first().associate { it.id to it.name }
        val header = listOf("Date", "Type", "Category", "Amount", "Currency", "Note")
        val rows = all.map { it.toCsvRow(categoryNames[it.categoryId].orEmpty()) }
        return (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",", transform = ::csvField) }
    }

    private fun Transaction.toCsvRow(categoryName: String): List<String> = listOf(
        date.toString(),
        type.name,
        categoryName,
        BigDecimal.valueOf(amount.amountMinor).movePointLeft(amount.currency.minorDigits).toPlainString(),
        amount.currency.code,
        note.orEmpty(),
    )

    /** Quotes a field when it contains a comma, quote or newline; doubles any internal quotes. */
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}

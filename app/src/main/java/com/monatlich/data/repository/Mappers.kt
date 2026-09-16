package com.monatlich.data.repository

import com.monatlich.data.local.BudgetEntity
import com.monatlich.data.local.CategoryEntity
import com.monatlich.data.local.ExchangeRateEntity
import com.monatlich.data.local.RecurringTransactionEntity
import com.monatlich.data.local.TransactionEntity
import com.monatlich.domain.model.Budget
import com.monatlich.domain.model.Category
import com.monatlich.domain.model.Currency
import com.monatlich.domain.model.ExchangeRate
import com.monatlich.domain.model.Money
import com.monatlich.domain.model.RecurringTransaction
import com.monatlich.domain.model.Transaction

internal fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    archived = archived,
    rolloverEnabled = rolloverEnabled,
)

internal fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    archived = archived,
    rolloverEnabled = rolloverEnabled,
)

internal fun BudgetEntity.toDomain() = Budget(
    id = id,
    categoryId = categoryId,
    month = month,
    amount = Money(amountMinor, Currency.fromCode(currencyCode)),
)

internal fun Budget.toEntity() = BudgetEntity(
    id = id,
    categoryId = categoryId,
    month = month,
    amountMinor = amount.amountMinor,
    currencyCode = amount.currency.code,
)

internal fun TransactionEntity.toDomain() = Transaction(
    id = id,
    categoryId = categoryId,
    date = date,
    amount = Money(amountMinor, Currency.fromCode(currencyCode)),
    rateToBase = rateToBase,
    type = type,
    note = note,
    recurringId = recurringId,
)

internal fun Transaction.toEntity() = TransactionEntity(
    id = id,
    categoryId = categoryId,
    month = month,
    date = date,
    amountMinor = amount.amountMinor,
    currencyCode = amount.currency.code,
    rateToBase = rateToBase,
    type = type,
    note = note,
    recurringId = recurringId,
)

internal fun ExchangeRateEntity.toDomain() = ExchangeRate(
    currency = Currency.fromCode(currencyCode),
    rateToBase = rateToBase,
    updatedAt = updatedAt,
)

internal fun ExchangeRate.toEntity() = ExchangeRateEntity(
    currencyCode = currency.code,
    rateToBase = rateToBase,
    updatedAt = updatedAt,
)

internal fun RecurringTransactionEntity.toDomain() = RecurringTransaction(
    id = id,
    categoryId = categoryId,
    amount = Money(amountMinor, Currency.fromCode(currencyCode)),
    type = type,
    note = note,
    dayOfMonth = dayOfMonth,
    startMonth = startMonth,
    endMonth = endMonth,
    active = active,
)

internal fun RecurringTransaction.toEntity() = RecurringTransactionEntity(
    id = id,
    categoryId = categoryId,
    amountMinor = amount.amountMinor,
    currencyCode = amount.currency.code,
    type = type,
    note = note,
    dayOfMonth = dayOfMonth,
    startMonth = startMonth,
    endMonth = endMonth,
    active = active,
)

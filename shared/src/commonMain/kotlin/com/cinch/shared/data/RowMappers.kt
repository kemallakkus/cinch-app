package com.cinch.shared.data

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import com.cinch.shared.db.Budget_config as BudgetRow
import com.cinch.shared.db.Card as CardRow
import com.cinch.shared.db.Expense as ExpenseRow

// SQLDelight satırları ↔ domain modelleri. Tarih epoch-day, tutar kuruş olarak saklanır.

internal fun ExpenseRow.toDomain(): Expense = Expense(
    id = id,
    date = LocalDate.fromEpochDays(date_epoch_day),
    grossAmount = Money(gross_minor, Currency.fromCode(currency)),
    chargeableFraction = chargeable_fraction,
    category = categoryOf(category),
    cardId = card_id,
    note = note,
    tokens = decodeTokens(tokens),
    rawText = raw_text,
)

internal fun CardRow.toDomain(): Card = Card(
    id = id,
    name = name,
    carriedBalance = Money(carried_balance_minor, Currency.fromCode(currency)),
    monthlyRatePercent = monthly_rate_percent,
    statementDay = statement_day?.toInt(),
)

internal fun BudgetRow.toDomain(): BudgetConfig {
    val currency = Currency.fromCode(currency)
    return BudgetConfig(
        weeklyLimit = Money(weekly_limit_minor, currency),
        weekStart = DayOfWeek(week_start_iso.toInt()),
        weeklyGoal = weekly_goal_minor?.let { Money(it, currency) },
    )
}

/** Bilinmeyen (ör. eski sürümden kalmış) kategori adı [Category.DIGER] sayılır. */
internal fun categoryOf(name: String): Category =
    Category.entries.firstOrNull { it.name == name } ?: Category.DIGER

/** Kelimeler zaten boşluktan bölünmüş olduğundan tek boşlukla birleştirmek kayıpsızdır. */
internal fun encodeTokens(tokens: List<String>): String = tokens.joinToString(" ")

internal fun decodeTokens(stored: String): List<String> =
    if (stored.isBlank()) emptyList() else stored.split(' ').filter { it.isNotEmpty() }

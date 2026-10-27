package com.cinch.shared.data

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.Income
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import com.cinch.shared.db.AllFixedPayments
import com.cinch.shared.db.Budget_config as BudgetRow
import com.cinch.shared.db.Card as CardRow
import com.cinch.shared.db.Expense as ExpenseRow
import com.cinch.shared.db.Fixed_expense as FixedRow
import com.cinch.shared.db.Income as IncomeRow

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
    fixedExpenseId = fixed_expense_id,
)

/**
 * `allFixedPayments` sorgusu `fixed_expense_id IS NOT NULL` dediği için SQLDelight o sütunu null olamaz
 * sayıp ayrı bir satır tipi üretir; tablo satırına çevirip aynı dönüşüm kullanılır.
 */
internal fun AllFixedPayments.toDomain(): Expense = ExpenseRow(
    id = id,
    date_epoch_day = date_epoch_day,
    gross_minor = gross_minor,
    chargeable_fraction = chargeable_fraction,
    currency = currency,
    category = category,
    card_id = card_id,
    note = note,
    raw_text = raw_text,
    tokens = tokens,
    fixed_expense_id = fixed_expense_id,
).toDomain()

internal fun FixedRow.toDomain(): FixedExpense {
    val money = Currency.fromCode(currency)
    return FixedExpense(
        id = id,
        name = name,
        amount = Money(amount_minor, money),
        dayOfMonth = day_of_month.toInt().coerceIn(1, 31),
        kind = FixedExpenseKind.entries.firstOrNull { it.name == kind } ?: FixedExpenseKind.OTHER,
        cardId = card_id,
        plan = FixedPlan.entries.firstOrNull { it.name == plan } ?: FixedPlan.MONTHLY,
        totalInstallments = total_installments?.toInt(),
        paidBefore = paid_before.toInt(),
        debt = debt_minor?.let { Money(it, money) },
    )
}

internal fun IncomeRow.toDomain(): Income = Income(
    id = id,
    name = name,
    amount = Money(amount_minor, Currency.fromCode(currency)),
    dayOfMonth = day_of_month?.toInt(),
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

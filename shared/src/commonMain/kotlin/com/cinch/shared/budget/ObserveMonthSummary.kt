package com.cinch.shared.budget

import com.cinch.shared.domain.Income
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Ayın özeti: gelir, harcama (kullanıcının payı) ve kalan.
 *
 * @param nextPayday bugünden itibaren ilk gelir günü; günü girilmiş gelir yoksa null.
 */
data class MonthSummary(
    val monthStart: LocalDate,
    val monthEnd: LocalDate,
    val income: Money,
    val spent: Money,
    val remaining: Money,
    val nextPayday: Payday?,
) {
    val hasIncome: Boolean get() = !income.isZero
}

/** @param daysAway 0 = bugün. */
data class Payday(val income: Income, val date: LocalDate, val daysAway: Int)

/**
 * Gelirler, bütçe ve bu ayın harcamalarını izleyip [MonthSummary] yayar. Tutarlar bütçenin para
 * biriminde (bütçe yoksa TL); başka birimdeki gelir ve harcamalar toplama girmez.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ObserveMonthSummary(
    private val budgets: BudgetRepository,
    private val incomes: IncomeRepository,
    private val expenses: ExpenseRepository,
) {

    operator fun invoke(today: LocalDate): Flow<MonthSummary> {
        val monthStart = LocalDate(today.year, today.month, 1)
        val monthEnd = monthStart.plus(DatePeriod(months = 1)).minusDays(1)
        return combine(budgets.observe(), incomes.observeAll()) { budget, list ->
            (budget?.currency ?: Currency.TRY) to list
        }.flatMapLatest { (currency, incomeList) ->
            expenses.observeBetween(monthStart, monthEnd).map { monthExpenses ->
                val income = Money.sum(incomeList.filter { it.currency == currency }.map { it.amount }, currency)
                val spent = Money.sum(
                    monthExpenses.filter { it.grossAmount.currency == currency }.map { it.chargeableAmount },
                    currency,
                )
                MonthSummary(
                    monthStart = monthStart,
                    monthEnd = monthEnd,
                    income = income,
                    spent = spent,
                    remaining = income - spent,
                    nextPayday = nextPayday(incomeList.filter { it.currency == currency }, today),
                )
            }
        }
    }

    private fun nextPayday(incomes: List<Income>, today: LocalDate): Payday? =
        incomes.mapNotNull { income ->
            // Aylık tekrar ettiği için bir ay ileri bakmak her zaman bir gün bulur.
            income.paydaysBetween(today, today.plus(DatePeriod(months = 1))).firstOrNull()
                ?.let { Payday(income, it, today.daysUntil(it)) }
        }.minWithOrNull(compareBy<Payday> { it.date }.thenByDescending { it.income.amount.minor })
}

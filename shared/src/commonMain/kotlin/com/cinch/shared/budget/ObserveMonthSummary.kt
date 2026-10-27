package com.cinch.shared.budget

import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Ayın özeti: gelir − sabit giderler − serbest harcamalar = elinde kalan.
 *
 * @param fixed sabit giderlerin bu ayki durumları (ödendi / bekliyor), ödeme günü sırasıyla.
 * @param fixedTotal sabit giderlerin ay toplamı: ödenenlerin gerçek, bekleyenlerin planlanan tutarı.
 * @param spent serbest harcamalar (kullanıcının payı); sabit gider ödemeleri hariç.
 * @param nextPayday bugünden itibaren ilk gelir günü; günü girilmiş gelir yoksa null.
 */
data class MonthSummary(
    val monthStart: LocalDate,
    val monthEnd: LocalDate,
    val income: Money,
    val fixed: List<FixedStatus>,
    val finishedFixed: List<FixedStatus> = emptyList(),
    val fixedTotal: Money,
    val spent: Money,
    val remaining: Money,
    val nextPayday: Payday?,
) {
    val hasIncome: Boolean get() = !income.isZero

    /** Bu ay henüz "ödendi" işaretlenmemiş sabit gider sayısı. */
    val pendingFixed: Int get() = fixed.count { it.state != FixedPaymentState.PAID }

    /** Haftalık sınır önerisi; gelir yoksa ya da sabit giderler geliri aşıyorsa null. */
    val weeklySuggestion: Money? get() = WeeklyLimitSuggestion.suggest(income, fixedTotal)
}

/** @param daysAway 0 = bugün. */
data class Payday(val income: Income, val date: LocalDate, val daysAway: Int)

/**
 * Gelirler, sabit giderler, bütçe ve bu ayın harcamalarını izleyip [MonthSummary] yayar. Tutarlar bütçenin
 * para biriminde (bütçe yoksa TL); başka birimdeki kayıtlar toplama girmez.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ObserveMonthSummary(
    private val budgets: BudgetRepository,
    private val incomes: IncomeRepository,
    private val fixedExpenses: FixedExpenseRepository,
    private val expenses: ExpenseRepository,
) {

    private data class Inputs(
        val currency: Currency,
        val incomes: List<Income>,
        val fixed: List<FixedExpense>,
    )

    operator fun invoke(today: LocalDate): Flow<MonthSummary> {
        val monthStart = LocalDate(today.year, today.month, 1)
        val monthEnd = monthStart.plus(DatePeriod(months = 1)).minusDays(1)
        return combine(budgets.observe(), incomes.observeAll(), fixedExpenses.observeAll()) { budget, incomeList, fixed ->
            val currency = budget?.currency ?: Currency.TRY
            Inputs(currency, incomeList.filter { it.currency == currency }, fixed.filter { it.currency == currency })
        }.flatMapLatest { inputs ->
            // Borç ilerlemesi (taksit sayısı, kalan borç) geçmiş tüm ödemelerden hesaplanır.
            combine(expenses.observeBetween(monthStart, monthEnd), expenses.observeFixedPayments()) { all, payments ->
                val monthExpenses = all.filter { it.grossAmount.currency == inputs.currency }
                val income = Money.sum(inputs.incomes.map { it.amount }, inputs.currency)
                val statuses = fixedStatuses(inputs.fixed, payments, today)
                val fixed = statuses.filterNot { it.isArchived }
                val fixedTotal = Money.sum(fixed.map { it.amountForMonth }, inputs.currency)
                val spent = Money.sum(monthExpenses.discretionary().map { it.chargeableAmount }, inputs.currency)
                MonthSummary(
                    monthStart = monthStart,
                    monthEnd = monthEnd,
                    income = income,
                    fixed = fixed,
                    finishedFixed = statuses.filter { it.isArchived },
                    fixedTotal = fixedTotal,
                    spent = spent,
                    remaining = income - fixedTotal - spent,
                    nextPayday = nextPayday(inputs.incomes, today),
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

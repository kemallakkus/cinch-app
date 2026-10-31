package com.cinch.shared.budget

import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.SavingsRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import com.cinch.shared.parser.plusDays
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Bir ayın özeti: "Ekim'de 12.400 ₺ harcadın, Eylül'den %8 az; en çok markete gitti, 22 gün payının içinde kaldın,
 * kumbaraya 1.200 ₺ attın."
 *
 * @param month ayın ilk günü.
 * @param spent serbest harcama (kullanıcının payı); [previousSpent] bir önceki ayın tamamı.
 * @param topCategory en çok harcanan kategori.
 * @param daysOnTrack günün harcaması günlük payı (haftalık sınır ÷ 7) geçmeyen gün; bütçe yoksa null. [daysCounted]
 *   sayılan gün (ilk kayıttan önceki günler sayılmaz).
 * @param fixedPaid o ay "ödendi" işaretlenen sabit giderler.
 * @param saved kumbaraya net (atılan − çekilen).
 * @param entries serbest harcama kaydı sayısı.
 */
data class MonthRecap(
    val month: LocalDate,
    val spent: Money,
    val previousSpent: Money,
    val topCategory: CategorySpend?,
    val topWord: TopWord?,
    val daysOnTrack: Int?,
    val daysCounted: Int,
    val fixedPaid: Money,
    val saved: Money,
    val entries: Int,
) {
    /** Önceki aya göre değişim oranı (−0.08 = %8 az); önceki ay boşsa null. */
    val change: Double? get() = changeRatio(spent, previousSpent)

    /** Kayıt yok: özet gösterilmez. */
    val isEmpty: Boolean get() = entries == 0 && fixedPaid.isZero
}

object MonthRecaps {

    /** [today]'den önceki ayın ilk günü: ayın ilk günlerinde gösterilen özet onun. */
    fun previousMonth(today: LocalDate): LocalDate = LocalDate(today.year, today.month, 1).minus(DatePeriod(months = 1))

    /**
     * @param month ayın herhangi bir günü.
     * @param expenses en az önceki ayın başından bu ayın sonuna kadarki kayıtlar.
     */
    fun compute(
        month: LocalDate,
        expenses: List<Expense>,
        deposits: List<SavingsDeposit>,
        budget: BudgetConfig?,
        today: LocalDate,
    ): MonthRecap {
        val currency = budget?.currency ?: Currency.TRY
        val start = LocalDate(month.year, month.month, 1)
        val end = minOf(start.plus(DatePeriod(months = 1)).minusDays(1), today)
        val breakdown = SpendingBreakdowns.compute(expenses, BreakdownPeriod.of(start, today), currency)
        val inMonth = expenses.filter { it.date in start..end && it.grossAmount.currency == currency }
        val discretionary = inMonth.discretionary()

        return MonthRecap(
            month = start,
            spent = breakdown.total,
            previousSpent = breakdown.previousTotal,
            topCategory = breakdown.slices.firstOrNull(),
            topWord = breakdown.topWords.firstOrNull(),
            daysOnTrack = budget?.let { daysOnTrack(discretionary, it, start, end, firstUse = expenses.minOfOrNull { e -> e.date }) },
            daysCounted = daysCounted(start, end, expenses.minOfOrNull { it.date }),
            fixedPaid = Money.sum(inMonth.filter { it.isFixedPayment }.map { it.grossAmount }, currency),
            saved = Money.sum(deposits.filter { it.date in start..end && it.amount.currency == currency }.map { it.amount }, currency),
            entries = discretionary.size,
        )
    }

    private fun countFrom(start: LocalDate, firstUse: LocalDate?): LocalDate = maxOf(start, firstUse ?: start)

    private fun daysCounted(start: LocalDate, end: LocalDate, firstUse: LocalDate?): Int {
        val from = countFrom(start, firstUse)
        return if (from > end) 0 else (end.toEpochDays() - from.toEpochDays()).toInt() + 1
    }

    private fun daysOnTrack(
        discretionary: List<Expense>,
        budget: BudgetConfig,
        start: LocalDate,
        end: LocalDate,
        firstUse: LocalDate?,
    ): Int {
        val share = budget.weeklyLimit.minor / 7
        val byDay = discretionary.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.chargeableAmount.minor } }
        var day = countFrom(start, firstUse)
        var count = 0
        while (day <= end) {
            if ((byDay[day] ?: 0L) <= share) count++
            day = day.plusDays(1)
        }
        return count
    }
}

/** Bir ayın özeti; harcama, bütçe ya da kumbara değişince yeniden hesaplanır. */
class ObserveMonthRecap(
    private val budgets: BudgetRepository,
    private val expenses: ExpenseRepository,
    private val savings: SavingsRepository,
    private val clock: LocalClock,
) {
    /** @param month ayın herhangi bir günü. */
    operator fun invoke(month: LocalDate): Flow<MonthRecap> {
        val today = clock.today()
        val start = LocalDate(month.year, month.month, 1)
        val from = start.minus(DatePeriod(months = 1))
        val to = start.plus(DatePeriod(months = 1)).minusDays(1)
        return combine(budgets.observe(), expenses.observeBetween(from, to), savings.observeDeposits()) { budget, list, deposits ->
            MonthRecaps.compute(start, list, deposits, budget, today)
        }
    }
}

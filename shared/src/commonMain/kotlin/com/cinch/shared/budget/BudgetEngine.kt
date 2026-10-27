package com.cinch.shared.budget

import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import com.cinch.shared.parser.plusDays
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * Haftalık bütçe yapılandırması.
 *
 * @param weeklyLimit haftalık harcama sınırı.
 * @param weekStart haftanın başladığı gün (varsayılan Pazartesi).
 * @param weeklyGoal opsiyonel haftalık hedef (sınırdan düşük tutma amacı).
 */
data class BudgetConfig(
    val weeklyLimit: Money,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val weeklyGoal: Money? = null,
) {
    val currency: Currency get() = weeklyLimit.currency
}

/** Belirli bir gün için bütçe durumu. Tüm tutarlar config para biriminde. */
data class BudgetStatus(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val weeklyLimit: Money,
    val weekSpent: Money,
    val weekRemaining: Money,
    val daysRemaining: Int,
    val dailyAllowance: Money,
    val todaySpent: Money,
    val todayRemaining: Money,
    val monthTotal: Money,
    val pacePercent: Double,
    val isOverLimit: Boolean,
)

/**
 * Harcamalardan haftalık sınır, günlük kalan pay ve ay toplamını hesaplar.
 * Paylaşılan harcamada sadece [Expense.chargeableAmount] (kullanıcı payı) sınıra sayılır.
 */
class BudgetEngine {

    fun status(expenses: List<Expense>, config: BudgetConfig, today: LocalDate): BudgetStatus {
        val currency = config.currency
        val weekStart = startOfWeek(today, config.weekStart)
        val weekEnd = weekStart.plusDays(6)

        val weekExpenses = expenses.filter { it.date in weekStart..weekEnd }
        val weekSpent = sumChargeable(weekExpenses, currency)
        val weekRemaining = config.weeklyLimit - weekSpent

        val todaySpent = sumChargeable(expenses.filter { it.date == today }, currency)

        // Bugün dahil hafta sonuna kalan gün sayısı.
        val daysRemaining = daysBetweenInclusive(today, weekEnd).coerceAtLeast(1)

        // Kalan bütçe, kalan günlere eşit bölünür (negatifse 0).
        val remainingForSplit = if (weekRemaining.isNegative) Money.zero(currency) else weekRemaining
        val dailyAllowance = Money(remainingForSplit.minor / daysRemaining, currency)
        val todayRemaining = dailyAllowance - todaySpent

        val monthTotal = sumChargeable(
            expenses.filter { it.date.year == today.year && it.date.month == today.month },
            currency,
        )

        val pacePercent = if (config.weeklyLimit.minor == 0L) 0.0
        else weekSpent.minor.toDouble() / config.weeklyLimit.minor.toDouble()

        return BudgetStatus(
            weekStart = weekStart,
            weekEnd = weekEnd,
            weeklyLimit = config.weeklyLimit,
            weekSpent = weekSpent,
            weekRemaining = weekRemaining,
            daysRemaining = daysRemaining,
            dailyAllowance = dailyAllowance,
            todaySpent = todaySpent,
            todayRemaining = todayRemaining,
            monthTotal = monthTotal,
            pacePercent = pacePercent,
            isOverLimit = weekRemaining.isNegative,
        )
    }

    private fun sumChargeable(expenses: List<Expense>, currency: Currency): Money =
        expenses.fold(Money.zero(currency)) { acc, e -> acc + e.chargeableAmount }

    companion object {
        /** [date] gününü içeren haftanın başlangıç gününü döner. */
        fun startOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate {
            // isoDayNumber: Pazartesi=1 ... Pazar=7
            val diff = (date.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + 7) % 7
            return date.minusDays(diff)
        }

        /** [from]..[to] arası gün sayısı (ikisi dahil). */
        fun daysBetweenInclusive(from: LocalDate, to: LocalDate): Int =
            from.daysUntil(to) + 1

        /**
         * [status] için gereken harcamaların tarih aralığı: bu hafta + bu ay.
         * Hafta ay sınırını aşabildiği için ikisinin birleşimi alınır.
         */
        fun statusRange(today: LocalDate, weekStart: DayOfWeek): ClosedRange<LocalDate> {
            val weekFirst = startOfWeek(today, weekStart)
            val weekLast = weekFirst.plusDays(6)
            val monthFirst = LocalDate(today.year, today.month, 1)
            val monthLast = monthFirst.plus(DatePeriod(months = 1)).minusDays(1)
            return minOf(weekFirst, monthFirst)..maxOf(weekLast, monthLast)
        }
    }
}

package com.cinch.shared.gamification

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import com.cinch.shared.parser.plusDays
import kotlinx.datetime.LocalDate

/**
 * @param budgetStreak günlük payın (haftalık sınır / 7) içinde kalınan ardışık **tamamlanmış** gün (dünden geriye).
 *   Bugün sayılmaz, gün bitmedi.
 * @param todayOnTrack bugün şu ana kadar payın içinde: gün böyle biterse seri bir artar.
 * @param bestBudgetStreak bakılan dönemdeki en uzun sınır serisi.
 * @param loggingStreak kayıt girilen ardışık gün (bugün henüz girilmediyse dünden geriye).
 * @param bestLoggingStreak bakılan dönemdeki en uzun kayıt serisi.
 * @param weeksOnTrack sınırın altında kapanan (ve içinde kayıt olan) tamamlanmış hafta sayısı.
 */
data class Streaks(
    val budgetStreak: Int,
    val todayOnTrack: Boolean,
    val bestBudgetStreak: Int,
    val loggingStreak: Int,
    val bestLoggingStreak: Int,
    val weeksOnTrack: Int,
) {
    companion object {
        val NONE = Streaks(0, false, 0, 0, 0, 0)
    }
}

/**
 * Seriler: oyunsu katmanın temeli. Uygulamayı kullanmaya başlamadan önceki günler seriye sayılmaz (ilk kayıttan
 * itibaren); harcamasız gün payın içinde sayılır.
 */
object StreakCalculator {

    /**
     * @param expenses serbest harcamalar (sabit gider ödemeleri hariç), bütçe para biriminde, bakılan dönem için.
     * @param firstUse ilk kayıt günü; seri bundan önceye uzamaz.
     */
    fun compute(expenses: List<Expense>, budget: BudgetConfig?, today: LocalDate, firstUse: LocalDate?): Streaks {
        val start = firstUse ?: return Streaks.NONE
        val spentByDay = expenses.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.chargeableAmount.minor } }
        val loggedDays = spentByDay.keys

        // Kayıt serisi: bugün girildiyse bugünden, girilmediyse dünden geriye.
        var logEnd = if (today in loggedDays) today else today.minusDays(1)
        var logging = 0
        while (logEnd >= start && logEnd in loggedDays) {
            logging++
            logEnd = logEnd.minusDays(1)
        }
        val bestLogging = longestRun(start, today) { it in loggedDays }

        if (budget == null || budget.weeklyLimit.isZero) {
            return Streaks(0, false, 0, logging, bestLogging, 0)
        }
        val share = budget.weeklyLimit.minor / 7
        fun onTrack(day: LocalDate) = (spentByDay[day] ?: 0L) <= share

        var day = today.minusDays(1)
        var streak = 0
        while (day >= start && onTrack(day)) {
            streak++
            day = day.minusDays(1)
        }
        return Streaks(
            budgetStreak = streak,
            todayOnTrack = onTrack(today),
            bestBudgetStreak = longestRun(start, today.minusDays(1)) { onTrack(it) },
            loggingStreak = logging,
            bestLoggingStreak = bestLogging,
            weeksOnTrack = weeksOnTrack(spentByDay, budget, start, today),
        )
    }

    private fun longestRun(from: LocalDate, to: LocalDate, ok: (LocalDate) -> Boolean): Int {
        var best = 0
        var run = 0
        var day = from
        while (day <= to) {
            run = if (ok(day)) run + 1 else 0
            if (run > best) best = run
            day = day.plusDays(1)
        }
        return best
    }

    /** Tamamlanmış bütçe haftaları: içinde en az bir kayıt olan ve sınırın altında kalan. */
    private fun weeksOnTrack(spentByDay: Map<LocalDate, Long>, budget: BudgetConfig, start: LocalDate, today: LocalDate): Int {
        val thisWeek = BudgetEngine.startOfWeek(today, budget.weekStart)
        var weekStart = BudgetEngine.startOfWeek(start, budget.weekStart)
        var count = 0
        while (weekStart < thisWeek) {
            val days = (0 until 7).map { weekStart.plusDays(it) }
            val spent = days.sumOf { spentByDay[it] ?: 0L }
            if (days.any { it in spentByDay } && spent <= budget.weeklyLimit.minor) count++
            weekStart = weekStart.plusDays(7)
        }
        return count
    }
}

/** Geçen hafta sınırın altında kalan para: "kumbaraya at" önerisi. */
data class WeeklyLeftover(val weekStart: LocalDate, val amount: Money)

object WeeklyLeftovers {

    /**
     * Bir önceki bütçe haftasından artan (sınır − harcanan). O hafta hiç kayıt yoksa ya da sınır aşıldıysa null:
     * kayıt tutulmayan haftanın "artanı" gerçek değil.
     */
    fun lastWeek(expenses: List<Expense>, budget: BudgetConfig, today: LocalDate): WeeklyLeftover? {
        val thisWeek = BudgetEngine.startOfWeek(today, budget.weekStart)
        val lastWeek = thisWeek.minusDays(7)
        val week = expenses.filter { it.date >= lastWeek && it.date < thisWeek && it.grossAmount.currency == budget.currency }
        if (week.isEmpty()) return null
        val left = budget.weeklyLimit.minor - week.sumOf { it.chargeableAmount.minor }
        return if (left > 0) WeeklyLeftover(lastWeek, Money(left, budget.currency)) else null
    }
}

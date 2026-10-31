package com.cinch.widget

import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.display
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.datetime.LocalDate

/**
 * Ana ekran widget'ının gösterdiği (Android: Glance, iOS: WidgetKit). Bütçe kurulmadıysa [hasBudget] false.
 *
 * @param day hesaplandığı gün; iOS widget'ı bunu bugünle karşılaştırıp eskimiş veriyi belli eder.
 * @param todayText bugün harcanabilecek (aşıldıysa aşılan) tutar, işaretsiz.
 */
data class TodaySnapshot(
    val hasBudget: Boolean,
    val day: LocalDate? = null,
    val todayText: String = "",
    val over: Boolean = false,
    val weekText: String = "",
    val streak: Int = 0,
) {
    companion object {
        val NO_BUDGET = TodaySnapshot(hasBudget = false)
    }
}

/** Telefondaki veriden widget'ın anlık görüntüsünü hesaplar. */
class LoadTodaySnapshot(
    private val budgetStatus: ObserveBudgetStatus,
    private val gamification: ObserveGamification,
    private val clock: LocalClock,
) {
    suspend operator fun invoke(): TodaySnapshot {
        val today = clock.today()
        val status = budgetStatus(today).first() ?: return TodaySnapshot.NO_BUDGET
        val streak = gamification(today).first().streaks.budgetStreak
        return TodaySnapshot(
            hasBudget = true,
            day = today,
            todayText = status.todayRemaining.absolute.display(),
            over = status.todayRemaining.isNegative,
            weekText = "Bu hafta ${status.weekSpent.display()} / ${status.weeklyLimit.display()}",
            streak = streak,
        )
    }
}

/** Widget'ı yenilemeyi gerektiren değişiklikler: bütçe ya da harcamalar (uygulamadan, widget'tan, fişten). */
fun widgetChanges(budgets: BudgetRepository, expenses: ExpenseRepository): Flow<Unit> =
    merge(budgets.observe().map { }, expenses.observeAll().map { })

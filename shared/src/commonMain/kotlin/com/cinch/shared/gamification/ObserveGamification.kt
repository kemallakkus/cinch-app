package com.cinch.shared.gamification

import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.budget.discretionary
import com.cinch.shared.budget.progress
import com.cinch.shared.domain.GoalProgress
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.SavingsRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.parser.minusDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/**
 * @param earnedNow şu an koşulu sağlanan rozetler; [BadgeStore.record] ile kaydedilince yeni olanlar kutlanır.
 * @param leftover geçen haftadan artan ("kumbaraya at" önerisi).
 */
data class GamificationState(
    val streaks: Streaks,
    val badges: List<BadgeState>,
    val earnedNow: Set<Badge>,
    val leftover: WeeklyLeftover?,
    val goals: List<GoalProgress>,
)

/**
 * Seriler, rozetler, haftalık artan ve kumbara hedefleri; harcama ve kumbara değiştikçe yeniden hesaplanır.
 * Kayıt yan etkisi yok: rozetleri kaydetmek ve kutlamak ana ekranın işi (birden çok ekran izleyebilir).
 *
 * @param lookbackDays serilerin geriye bakılan gün sayısı.
 */
class ObserveGamification(
    private val budgets: BudgetRepository,
    private val expenses: ExpenseRepository,
    private val savings: SavingsRepository,
    private val fixedExpenses: FixedExpenseRepository,
    private val backups: BackupTracker,
    private val badgeStore: BadgeStore,
    private val lookbackDays: Int = 120,
) {
    operator fun invoke(today: LocalDate): Flow<GamificationState> {
        val window = expenses.observeBetween(today.minusDays(lookbackDays - 1), today)
        val debtsFinished = combine(fixedExpenses.observeAll(), expenses.observeFixedPayments()) { fixed, payments ->
            fixed.count { it.progress(payments, today)?.isFinished == true }
        }
        val goals = combine(savings.observeGoals(), savings.observeDeposits()) { goals, deposits ->
            GoalProgress.of(goals, deposits) to deposits.count { !it.amount.isNegative && !it.amount.isZero }
        }
        return combine(budgets.observe(), window, goals, debtsFinished) { budget, list, (goalList, depositCount), finished ->
            val currency = budget?.currency ?: Currency.TRY
            val usable = list.discretionary().filter { it.grossAmount.currency == currency }
            val streaks = StreakCalculator.compute(usable, budget, today, firstUse = list.minOfOrNull { it.date })
            val input = BadgeInputs(
                expenseCount = list.size,
                streaks = streaks,
                depositCount = depositCount,
                goalsReached = goalList.count { it.isReached },
                debtsFinished = finished,
                hasBackup = backups.lastBackup() != null,
            )
            val earned = Badges.earned(input)
            GamificationState(
                streaks = streaks,
                badges = badgeStore.states(input).map { state ->
                    // Kaydedilmemiş ama koşulu sağlanmış: bugün kazanılmış gibi gösterilir.
                    if (state.earnedOn == null && state.badge in earned) state.copy(earnedOn = today, progress = null) else state
                },
                earnedNow = earned,
                leftover = budget?.let { WeeklyLeftovers.lastWeek(usable, it, today) },
                goals = goalList,
            )
        }
    }
}

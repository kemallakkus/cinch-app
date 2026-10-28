package com.cinch.shared.gamification

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.prefs.InMemoryKeyValueStore
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreakCalculatorTest {

    private val today = LocalDate(2025, 1, 15) // Çarşamba; hafta pazartesi başlıyor
    private val budget = BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY)) // günlük pay 100
    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private fun spend(day: Int, units: Long) = Expense(date = LocalDate(2025, 1, day), grossAmount = tl(units))

    /** 8 Ocak aşıldı; 9–14 payın içinde (11'i harcamasız); bugün 30. */
    private val expenses = listOf(
        spend(8, 150), spend(9, 80), spend(10, 100), spend(12, 60), spend(13, 90), spend(14, 40), spend(15, 30),
    )

    @Test
    fun budgetAndLoggingStreaks() {
        val s = StreakCalculator.compute(expenses, budget, today, firstUse = LocalDate(2025, 1, 8))

        assertEquals(6, s.budgetStreak) // 9–14 Ocak; harcamasız gün de payın içinde, tam pay (100) da
        assertTrue(s.todayOnTrack)
        assertEquals(6, s.bestBudgetStreak)
        assertEquals(4, s.loggingStreak) // 12–15 Ocak; 11'inde kayıt yok
        assertEquals(4, s.bestLoggingStreak)
        assertEquals(1, s.weeksOnTrack) // 6–12 Ocak: 390 ≤ 700
    }

    @Test
    fun daysBeforeFirstUse_doNotCount() {
        val s = StreakCalculator.compute(listOf(spend(13, 10), spend(14, 10)), budget, today, firstUse = LocalDate(2025, 1, 13))
        assertEquals(2, s.budgetStreak)
        assertEquals(0, s.weeksOnTrack) // geçen hafta kayıt yok
    }

    @Test
    fun overspendingToday_breaksNothingYet_butIsFlagged() {
        val s = StreakCalculator.compute(expenses + spend(15, 200), budget, today, firstUse = LocalDate(2025, 1, 8))
        assertEquals(6, s.budgetStreak)
        assertTrue(!s.todayOnTrack)
    }

    @Test
    fun withoutBudget_onlyLoggingStreak_withoutUse_nothing() {
        val noBudget = StreakCalculator.compute(expenses, null, today, firstUse = LocalDate(2025, 1, 8))
        assertEquals(0, noBudget.budgetStreak)
        assertEquals(4, noBudget.loggingStreak)
        assertEquals(Streaks.NONE, StreakCalculator.compute(emptyList(), budget, today, firstUse = null))
    }

    @Test
    fun lastWeekLeftover() {
        assertEquals(WeeklyLeftover(LocalDate(2025, 1, 6), tl(310)), WeeklyLeftovers.lastWeek(expenses, budget, today))
        assertNull(WeeklyLeftovers.lastWeek(listOf(spend(14, 10)), budget, today)) // geçen hafta kayıt yok
        assertNull(WeeklyLeftovers.lastWeek(listOf(spend(10, 800)), budget, today)) // sınır aşılmış
    }

    @Test
    fun badges_earnedOnceAndKept() {
        val streaks = StreakCalculator.compute(expenses, budget, today, firstUse = LocalDate(2025, 1, 8))
        val input = BadgeInputs(expenseCount = 7, streaks = streaks, depositCount = 0, goalsReached = 0, debtsFinished = 0, hasBackup = false)
        val earned = Badges.earned(input)
        assertEquals(setOf(Badge.FIRST_ENTRY, Badge.STREAK_3, Badge.WEEK_ON_TRACK), earned)

        val store = BadgeStore(InMemoryKeyValueStore())
        assertEquals(earned, store.record(earned, today).toSet())
        assertTrue(store.record(earned, today.plusDaysForTest(1)).isEmpty()) // ikinci kez kutlanmaz
        assertEquals(today, store.earnedOn(Badge.STREAK_3))

        // Seri bozulsa da kazanılan rozet kalır; kilitliler ilerleme gösterir.
        val states = store.states(input.copy(streaks = Streaks.NONE))
        assertTrue(states.single { it.badge == Badge.STREAK_3 }.isEarned)
        assertEquals(0 to 7, states.single { it.badge == Badge.STREAK_7 }.progress)
    }

    private fun LocalDate.plusDaysForTest(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)
}

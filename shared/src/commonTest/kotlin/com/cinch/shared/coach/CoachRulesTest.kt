package com.cinch.shared.coach

import com.cinch.shared.budget.BudgetStatus
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachRulesTest {

    private val rules = CoachRules()
    private val today = LocalDate(2025, 1, 13) // Pazartesi
    private fun t(units: Long) = Money.of(units, Currency.TRY)

    private fun status(pace: Double = 0.4, over: Boolean = false) = BudgetStatus(
        weekStart = today,
        weekEnd = LocalDate(2025, 1, 19),
        weeklyLimit = t(700),
        weekSpent = t(200),
        weekRemaining = t(500),
        daysRemaining = 7,
        dailyAllowance = t(100),
        todaySpent = t(0),
        todayRemaining = t(100),
        monthTotal = t(200),
        pacePercent = pace,
        isOverLimit = over,
    )

    @Test
    fun midday_onesPerDay_plusWeeklyClosingAndForgot() {
        val plan = rules.planWeek(CoachInput(today = today, budget = status()), CoachConfig())
        // 7 gün ortası + 1 pazar kapanış + 1 kayıt unutma = 9
        assertEquals(7, plan.count { it.type == NotificationType.MIDDAY_REMAINING })
        assertEquals(1, plan.count { it.type == NotificationType.WEEKLY_CLOSING })
        assertEquals(1, plan.count { it.type == NotificationType.FORGOT_LOG })

        val closing = plan.first { it.type == NotificationType.WEEKLY_CLOSING }
        assertEquals(LocalDate(2025, 1, 19), closing.date) // Pazar
    }

    @Test
    fun approachingLimit_firesWhenPaceHigh() {
        val plan = rules.planWeek(CoachInput(today = today, budget = status(pace = 0.9)), CoachConfig())
        val warn = plan.filter { it.type == NotificationType.APPROACHING_LIMIT }
        assertEquals(1, warn.size)
        assertEquals(today.minusDays(-1), warn.first().date) // yarın
    }

    @Test
    fun approachingLimit_silentWhenOver() {
        val plan = rules.planWeek(CoachInput(today = today, budget = status(pace = 1.2, over = true)), CoachConfig())
        assertFalse(plan.any { it.type == NotificationType.APPROACHING_LIMIT })
    }

    @Test
    fun maxPerDay_enforced() {
        val many = listOf(10, 20, 30, 40).mapIndexed { i, prio ->
            CoachNotification(today, LocalTime(9 + i, 0), NotificationType.MIDDAY_REMAINING, prio, "p$prio")
        }
        val kept = rules.applyConstraints(many, CoachConfig(maxPerDay = 2))
        assertEquals(2, kept.size)
        // En yüksek öncelikliler kalır (40 ve 30)
        assertEquals(setOf(40, 30), kept.map { it.priority }.toSet())
    }

    @Test
    fun quietHours_shiftsIntoAllowedWindow() {
        val n = CoachNotification(today, LocalTime(23, 0), NotificationType.STREAK, 10, "x")
        val kept = rules.applyConstraints(listOf(n), CoachConfig())
        assertEquals(LocalTime(8, 0), kept.first().time) // 22:00–08:00 → 08:00'e kaydı
    }

    @Test
    fun habitAlert_firesAboveThreshold() {
        val expenses = (1..11).map {
            Expense(
                date = today.minusDays(it % 5),
                grossAmount = t(20),
                tokens = listOf("bufe"),
            )
        }
        val plan = rules.planWeek(
            CoachInput(today = today, budget = status(), expenses = expenses),
            CoachConfig(habitThreshold = 10),
        )
        val habit = plan.firstOrNull { it.type == NotificationType.HABIT_ALERT }
        assertTrue(habit != null)
        assertTrue(habit!!.message.contains("11"))
        assertTrue(habit.message.contains("bufe"))
    }

    @Test
    fun streak_firesForConsecutiveDays() {
        val expenses = (0..2).map { Expense(date = today.minusDays(it), grossAmount = t(10)) }
        val plan = rules.planWeek(
            CoachInput(today = today, budget = status(), expenses = expenses),
            CoachConfig(),
        )
        val streak = plan.firstOrNull { it.type == NotificationType.STREAK }
        assertTrue(streak != null)
        assertTrue(streak!!.message.contains("3"))
    }

    @Test
    fun loggedToday_noForgotReminder() {
        val expenses = listOf(Expense(date = today, grossAmount = t(10)))
        val plan = rules.planWeek(
            CoachInput(today = today, budget = status(), expenses = expenses),
            CoachConfig(),
        )
        assertFalse(plan.any { it.type == NotificationType.FORGOT_LOG })
    }

    @Test
    fun quietHours_overnightContains() {
        val q = QuietHours(LocalTime(22, 0), LocalTime(8, 0))
        assertTrue(q.contains(LocalTime(23, 0)))
        assertTrue(q.contains(LocalTime(7, 0)))
        assertFalse(q.contains(LocalTime(8, 0)))
        assertFalse(q.contains(LocalTime(13, 0)))
    }

    @Test
    fun allWithinQuietHoursRespected_inFullPlan() {
        val plan = rules.planWeek(CoachInput(today = today, budget = status()), CoachConfig())
        assertTrue(plan.none { CoachConfig().quietHours.contains(it.time) })
    }

    @Test
    fun categorySpike_onFifthDay_comparesSameDaysOfLastWeek() {
        val friday = LocalDate(2025, 1, 17) // hafta pazartesi 13'ünde başladı
        val expenses = listOf(
            Expense(date = LocalDate(2025, 1, 14), grossAmount = t(300), category = Category.YEMEK),
            Expense(date = LocalDate(2025, 1, 16), grossAmount = t(200), category = Category.YEMEK),
            Expense(date = LocalDate(2025, 1, 7), grossAmount = t(300), category = Category.YEMEK),
            // Geçen haftanın cumartesisi: bu haftanın aynı günlerine denk gelmez, kıyasa girmez.
            Expense(date = LocalDate(2025, 1, 11), grossAmount = t(400), category = Category.YEMEK),
            Expense(date = LocalDate(2025, 1, 15), grossAmount = t(100), category = Category.MARKET),
            Expense(date = LocalDate(2025, 1, 8), grossAmount = t(100), category = Category.MARKET),
        )

        val plan = rules.planWeek(CoachInput(today = friday, budget = status(), expenses = expenses), CoachConfig())

        val spike = plan.single { it.type == NotificationType.CATEGORY_SPIKE }
        assertEquals(friday, spike.date)
        assertEquals(LocalTime(17, 30), spike.time)
        assertTrue("yemeğe" in spike.message, spike.message)
        assertTrue("%67" in spike.message, spike.message) // 300 → 500
    }

    @Test
    fun categorySpike_silentOnOtherDays_andForSmallDifferences() {
        val thursday = LocalDate(2025, 1, 16)
        val expenses = listOf(
            Expense(date = LocalDate(2025, 1, 14), grossAmount = t(500), category = Category.YEMEK),
            Expense(date = LocalDate(2025, 1, 7), grossAmount = t(300), category = Category.YEMEK),
        )
        val onThursday = rules.planWeek(CoachInput(today = thursday, budget = status(), expenses = expenses), CoachConfig())
        assertFalse(onThursday.any { it.type == NotificationType.CATEGORY_SPIKE })

        // %50 fazla ama fark 50 TL: haftalık sınırın (700) %10'una varmıyor.
        val small = listOf(
            Expense(date = LocalDate(2025, 1, 14), grossAmount = t(150), category = Category.YEMEK),
            Expense(date = LocalDate(2025, 1, 7), grossAmount = t(100), category = Category.YEMEK),
        )
        val friday = LocalDate(2025, 1, 17)
        val plan = rules.planWeek(CoachInput(today = friday, budget = status(), expenses = small), CoachConfig())
        assertFalse(plan.any { it.type == NotificationType.CATEGORY_SPIKE })
    }

    @Test
    fun approachingLimit_silentWhenTomorrowIsNewWeek() {
        val sunday = LocalDate(2025, 1, 19) // status() haftası 13–19 Ocak
        val plan = rules.planWeek(CoachInput(today = sunday, budget = status(pace = 0.9)), CoachConfig())
        assertFalse(plan.any { it.type == NotificationType.APPROACHING_LIMIT })
    }

    @Test
    fun habitAlert_isWeekly_onThirdDayOfWeek() {
        val expenses = (1..11).map { Expense(date = today.minusDays(it % 5), grossAmount = t(20), tokens = listOf("bufe")) }

        val fromMonday = rules.planWeek(CoachInput(today = today, budget = status(), expenses = expenses), CoachConfig(habitThreshold = 10))
        assertEquals(LocalDate(2025, 1, 15), fromMonday.single { it.type == NotificationType.HABIT_ALERT }.date) // çarşamba

        val tuesday = LocalDate(2025, 1, 14)
        val fromTuesday = rules.planWeek(CoachInput(today = tuesday, budget = status(), expenses = expenses), CoachConfig(habitThreshold = 10))
        assertFalse(fromTuesday.any { it.type == NotificationType.HABIT_ALERT })
    }
}

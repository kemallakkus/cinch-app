package com.cinch.shared.coach

import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.prefs.InMemoryKeyValueStore
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.data.SqlBudgetRepository
import com.cinch.shared.data.SqlCardRepository
import com.cinch.shared.data.SqlCardStatementRepository
import com.cinch.shared.data.SqlExpenseRepository
import com.cinch.shared.data.SqlFixedExpenseRepository
import com.cinch.shared.data.SqlIncomeRepository
import com.cinch.shared.data.newDatabase
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.AppLocale
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyFormatter
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixedClock(var now: LocalDateTime) : LocalClock {
    override fun now(): LocalDateTime = now
}

class CoachNotificationPlannerTest {

    private val wednesday = LocalDate(2025, 1, 15)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val budgets: SqlBudgetRepository,
        val expenses: SqlExpenseRepository,
        val clock: FixedClock,
        val planner: CoachNotificationPlanner,
    )

    private fun TestScope.fixture(at: LocalTime = LocalTime(14, 0)): Fixture {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val budgets = SqlBudgetRepository(db, io)
        val expenses = SqlExpenseRepository(db, io)
        val clock = FixedClock(LocalDateTime(wednesday, at))
        val plan = PlanCoachWeek(
            budgets, expenses, SqlCardRepository(db, io), SqlIncomeRepository(db, io), SqlFixedExpenseRepository(db, io),
            SqlCardStatementRepository(db, io), BackupTracker(InMemoryKeyValueStore()), BudgetEngine(), CoachRules(),
        )
        return Fixture(budgets, expenses, clock, CoachNotificationPlanner(plan, clock))
    }

    @Test
    fun upcoming_skipsTimesAlreadyPassedToday() = runTest {
        val f = fixture(at = LocalTime(14, 0))
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))

        val middays = f.planner.upcoming().filter { it.type == NotificationType.MIDDAY_REMAINING }

        // Bugünün 13:00'ü geçti; yarından itibaren 6 gün kalır.
        assertEquals(LocalDate(2025, 1, 16), middays.first().date)
        assertEquals(6, middays.size)
    }

    @Test
    fun upcoming_isSortedByTime() = runTest {
        val f = fixture(at = LocalTime(8, 0))
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))

        val times = f.planner.upcoming().map { LocalDateTime(it.date, it.time) }

        assertEquals(times.sorted(), times)
        assertTrue(times.first() > f.clock.now)
    }

    @Test
    fun upcoming_emptyWithoutBudget() = runTest {
        assertTrue(fixture().planner.upcoming().isEmpty())
    }

    @Test
    fun resolveDue_usesDataAtFireTime() = runTest {
        val f = fixture(at = LocalTime(13, 0))
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        // Çarşamba: 5 gün kaldı. Plan kurulduktan sonra harcama girildi.
        f.expenses.add(Expense(date = wednesday, grossAmount = tl(200)))

        val due = f.planner.resolveDue(NotificationType.MIDDAY_REMAINING, wednesday)

        // (700 - 200) / 5 = 100 ₺
        assertNotNull(due)
        assertTrue(MoneyFormatter.format(tl(100), AppLocale.TR) in due.message, due.message)
    }

    @Test
    fun resolveDue_lateDelivery_isDropped() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        assertNull(f.planner.resolveDue(NotificationType.MIDDAY_REMAINING, LocalDate(2025, 1, 14)))
    }

    @Test
    fun resolveDue_forgotLog_onlyWhenYesterdayIsEmpty() = runTest {
        val f = fixture(at = LocalTime(20, 0))
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        assertNotNull(f.planner.resolveDue(NotificationType.FORGOT_LOG, wednesday))

        f.expenses.add(Expense(date = LocalDate(2025, 1, 14), grossAmount = tl(30))) // dün
        assertNull(f.planner.resolveDue(NotificationType.FORGOT_LOG, wednesday))
    }

    @Test
    fun approachingLimit_plannedYesterday_survivesMorningReplan_andIsShown() = runTest {
        val f = fixture(at = LocalTime(8, 0))
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 13), grossAmount = tl(300)))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 14), grossAmount = tl(300)))

        // Sabah yeniden kurulan plan, dün bugün 10:00'a planlanan uyarıyı yarına itmez.
        val warnings = f.planner.upcoming().filter { it.type == NotificationType.APPROACHING_LIMIT }
        assertEquals(listOf(wednesday, LocalDate(2025, 1, 16)), warnings.map { it.date })

        f.clock.now = LocalDateTime(wednesday, LocalTime(10, 0))
        val due = f.planner.resolveDue(NotificationType.APPROACHING_LIMIT, wednesday)
        assertNotNull(due)
        assertTrue(MoneyFormatter.format(tl(100), AppLocale.TR) in due.message, due.message) // 700 − 600
    }

    @Test
    fun approachingLimit_isNotCarriedIntoNewWeek() = runTest {
        val f = fixture()
        val monday = LocalDate(2025, 1, 20)
        f.clock.now = LocalDateTime(monday, LocalTime(10, 0))
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 19), grossAmount = tl(650))) // geçen haftanın pazarı

        assertNull(f.planner.resolveDue(NotificationType.APPROACHING_LIMIT, monday))
    }

    @Test
    fun streak_plannedYesterday_isShownThisMorning() = runTest {
        val f = fixture(at = LocalTime(9, 0))
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        listOf(12, 13, 14).forEach { f.expenses.add(Expense(date = LocalDate(2025, 1, it), grossAmount = tl(20))) }

        // Bugün henüz kayıt yok; seri dünün gözünden sayılır: 12–14 Ocak = 3 gün.
        val due = f.planner.resolveDue(NotificationType.STREAK, wednesday)
        assertNotNull(due)
        assertTrue("3" in due.message, due.message)
    }

    @Test
    fun habitAlert_plannedOnMonday_isShownWednesday() = runTest {
        val f = fixture(at = LocalTime(18, 0))
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        repeat(11) { f.expenses.add(Expense(date = LocalDate(2025, 1, 3 + it), grossAmount = tl(20), tokens = listOf("bufe"))) }

        val due = f.planner.resolveDue(NotificationType.HABIT_ALERT, wednesday)
        assertNotNull(due)
        assertTrue("bufe" in due.message && "11" in due.message, due.message)
    }
}

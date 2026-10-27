package com.cinch.shared.coach

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.data.SqlBudgetRepository
import com.cinch.shared.data.SqlCardRepository
import com.cinch.shared.data.SqlExpenseRepository
import com.cinch.shared.data.SqlIncomeRepository
import com.cinch.shared.data.newDatabase
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Income
import com.cinch.shared.money.AppLocale
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyFormatter
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.parser.minusDays
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanCoachWeekTest {

    private val today = LocalDate(2025, 1, 13) // Pazartesi
    private val parser = QuickEntryParser()
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val budgets: SqlBudgetRepository,
        val expenses: SqlExpenseRepository,
        val cards: SqlCardRepository,
        val incomes: SqlIncomeRepository,
        val plan: PlanCoachWeek,
    )

    private fun TestScope.fixture(): Fixture {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val budgets = SqlBudgetRepository(db, io)
        val expenses = SqlExpenseRepository(db, io)
        val cards = SqlCardRepository(db, io)
        val incomes = SqlIncomeRepository(db, io)
        return Fixture(
            budgets, expenses, cards, incomes,
            PlanCoachWeek(budgets, expenses, cards, incomes, BudgetEngine(), CoachRules()),
        )
    }

    /** Hızlı kayıt yolunu taklit eder: metin → ayrıştır → kaydet. */
    private suspend fun Fixture.log(raw: String, date: LocalDate) {
        expenses.add(parser.parse(raw, date).toExpense()!!)
    }

    @Test
    fun withoutBudget_planIsEmpty() = runTest {
        val f = fixture()
        f.log("195 market", today)
        assertTrue(f.plan(today).isEmpty())
    }

    @Test
    fun dailyAllowanceComesFromStoredExpenses() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.log("140 market", today)

        val midday = f.plan(today).first { it.type == NotificationType.MIDDAY_REMAINING }

        // (700 - 140) / 7 gün = 80 ₺
        val expected = MoneyFormatter.format(tl(80), AppLocale.TR)
        assertTrue(expected in midday.message, midday.message)
    }

    @Test
    fun habitAlert_usesTokensSavedWithExpense() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        repeat(11) { f.log("20 büfe", today.minusDays(it)) }

        val habit = f.plan(today).first { it.type == NotificationType.HABIT_ALERT }

        assertTrue("bufe" in habit.message && "11" in habit.message, habit.message)
    }

    @Test
    fun habitAlert_ignoresExpensesBeforeLookback() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        repeat(11) { f.log("20 büfe", today.minusDays(40 + it)) }

        assertTrue(f.plan(today).none { it.type == NotificationType.HABIT_ALERT })
    }

    @Test
    fun statementDue_fromCardStatementDay() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.cards.add(Card(name = "QNB", carriedBalance = tl(0), statementDay = 16))
        f.cards.add(Card(name = "Bonus", carriedBalance = tl(0), statementDay = 16)) // aynı gün, tek uyarı

        val due = f.plan(today).filter { it.type == NotificationType.STATEMENT_DUE }

        assertEquals(listOf(LocalDate(2025, 1, 15)), due.map { it.date })
    }

    @Test
    fun salary_followsLargestIncomeDay() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.incomes.add(Income(name = "Kira geliri", amount = tl(8_000), dayOfMonth = 14))
        f.incomes.add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15))

        val salary = f.plan(today).filter { it.type == NotificationType.SALARY }

        assertEquals(listOf(LocalDate(2025, 1, 15)), salary.map { it.date })
    }

    @Test
    fun salary_noneWithoutIncomeDay() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.incomes.add(Income(name = "Serbest iş", amount = tl(5_000))) // günü belli değil
        assertTrue(f.plan(today).none { it.type == NotificationType.SALARY })
    }

    @Test
    fun weeklyClosing_followsBudgetWeekStart() = runTest {
        val f = fixture()
        // Hafta Pazar başlıyorsa kapanış Cumartesi; çağıranın CoachConfig'i Pazartesi olsa da.
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700), weekStart = DayOfWeek.SUNDAY))

        val closing = f.plan(today, CoachConfig(weekStart = DayOfWeek.MONDAY))
            .single { it.type == NotificationType.WEEKLY_CLOSING }

        assertEquals(LocalDate(2025, 1, 18), closing.date)
    }
}

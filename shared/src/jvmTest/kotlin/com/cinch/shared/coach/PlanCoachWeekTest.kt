package com.cinch.shared.coach

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.data.SqlBudgetRepository
import com.cinch.shared.data.SqlCardRepository
import com.cinch.shared.data.SqlCardStatementRepository
import com.cinch.shared.data.SqlExpenseRepository
import com.cinch.shared.data.SqlFixedExpenseRepository
import com.cinch.shared.data.SqlIncomeRepository
import com.cinch.shared.data.newDatabase
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.FixedPlan
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
        val fixed: SqlFixedExpenseRepository,
        val plan: PlanCoachWeek,
        val statements: SqlCardStatementRepository,
    )

    private fun TestScope.fixture(): Fixture {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val budgets = SqlBudgetRepository(db, io)
        val expenses = SqlExpenseRepository(db, io)
        val cards = SqlCardRepository(db, io)
        val incomes = SqlIncomeRepository(db, io)
        val fixed = SqlFixedExpenseRepository(db, io)
        val statements = SqlCardStatementRepository(db, io)
        return Fixture(
            budgets, expenses, cards, incomes, fixed,
            PlanCoachWeek(budgets, expenses, cards, incomes, fixed, statements, BudgetEngine(), CoachRules()),
            statements,
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

    @Test
    fun fixedDue_remindsDayBefore_andAsksOnTheDayIfUnpaid() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.fixed.add(FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 15, kind = FixedExpenseKind.RENT))
        f.fixed.add(FixedExpense(name = "İnternet", amount = tl(450), dayOfMonth = 15, kind = FixedExpenseKind.BILL))

        val plan = f.plan(today) // 13 Ocak
        val due = plan.single { it.type == NotificationType.FIXED_DUE }
        val unpaid = plan.single { it.type == NotificationType.FIXED_UNPAID }

        assertEquals(LocalDate(2025, 1, 14), due.date)
        assertTrue("2 ödeme" in due.message && "Kira" in due.message, due.message)
        assertEquals(LocalDate(2025, 1, 15), unpaid.date)
    }

    @Test
    fun fixedAlreadyPaidThisMonth_isNotReminded() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        val rent = f.fixed.add(FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 15))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 10), grossAmount = tl(15_000), fixedExpenseId = rent))

        val plan = f.plan(today)

        assertTrue(plan.none { it.type == NotificationType.FIXED_DUE || it.type == NotificationType.FIXED_UNPAID })
    }

    @Test
    fun fixedPayment_doesNotCountAsLoggedSpending() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.expenses.add(Expense(date = today, grossAmount = tl(15_000), fixedExpenseId = 1))

        val midday = f.plan(today).first { it.type == NotificationType.MIDDAY_REMAINING }

        // Kira haftalık paydan düşülmez: (700 - 0) / 7 = 100 ₺
        assertTrue(MoneyFormatter.format(tl(100), AppLocale.TR) in midday.message, midday.message)
    }

    @Test
    fun installments_reminderShowsInstallmentNumber_andStopsWhenFinished() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        val id = f.fixed.add(
            FixedExpense(
                name = "Ablama borç", amount = tl(5_000), dayOfMonth = 15,
                plan = FixedPlan.INSTALLMENTS, totalInstallments = 6, paidBefore = 4,
            ),
        )

        val due = f.plan(today).single { it.type == NotificationType.FIXED_DUE }
        assertTrue("5/6. taksit" in due.message, due.message)

        // Son iki taksit önceki aylarda ödendi: borç bitti, hatırlatma yok.
        f.expenses.add(Expense(date = LocalDate(2024, 11, 15), grossAmount = tl(5_000), fixedExpenseId = id))
        f.expenses.add(Expense(date = LocalDate(2024, 12, 15), grossAmount = tl(5_000), fixedExpenseId = id))
        assertTrue(f.plan(today).none { it.type == NotificationType.FIXED_DUE || it.type == NotificationType.FIXED_UNPAID })
    }

    @Test
    fun flexibleWithoutUsualAmount_reminderHasNoAmount() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.fixed.add(FixedExpense(name = "Eminevim", amount = tl(0), dayOfMonth = 15, plan = FixedPlan.FLEXIBLE, debt = tl(180_000)))

        val due = f.plan(today).single { it.type == NotificationType.FIXED_DUE }

        assertEquals("Yarın ödeme günü: Eminevim.", due.message)
    }

    @Test
    fun cardDue_comesFromLatestReadStatement() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        val qnb = f.cards.add(Card(name = "QNB", carriedBalance = tl(0)))
        val due = LocalDate(2025, 1, 16)
        f.statements.add(CardStatement(cardId = qnb, readOn = LocalDate(2025, 1, 10), dueDate = due, statementBalance = tl(52_331)))
        f.statements.add(CardStatement(cardId = qnb, readOn = LocalDate(2025, 1, 12), dueDate = due, statementBalance = tl(31_393)))

        val reminders = f.plan(today).filter { it.type == NotificationType.CARD_DUE }

        assertEquals(listOf(today, due), reminders.map { it.date }) // 3 gün önce ve o gün
        val amount = MoneyFormatter.format(tl(31_393), AppLocale.TR)
        assertTrue(amount in reminders.first().message, reminders.first().message) // en son okunan borç
    }

    @Test
    fun cardDue_silentAfterReadingPaidStatement() = runTest {
        val f = fixture()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        val qnb = f.cards.add(Card(name = "QNB", carriedBalance = tl(0)))
        f.statements.add(CardStatement(cardId = qnb, readOn = today, dueDate = LocalDate(2025, 1, 16), statementBalance = tl(0)))

        assertTrue(f.plan(today).none { it.type == NotificationType.CARD_DUE })
    }
}

package com.cinch.shared.budget

import com.cinch.shared.data.SqlBudgetRepository
import com.cinch.shared.data.SqlSavingsRepository
import com.cinch.shared.data.SqlExpenseRepository
import com.cinch.shared.data.SqlFixedExpenseRepository
import com.cinch.shared.data.SqlIncomeRepository
import com.cinch.shared.data.newDatabase
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.SavingsGoal
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ObserveMonthSummaryTest {

    private val today = LocalDate(2025, 1, 20)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val incomes: SqlIncomeRepository,
        val fixed: SqlFixedExpenseRepository,
        val expenses: SqlExpenseRepository,
        val observe: ObserveMonthSummary,
        val savings: SqlSavingsRepository,
    )

    private fun TestScope.fixture(): Fixture {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val incomes = SqlIncomeRepository(db, io)
        val expenses = SqlExpenseRepository(db, io)
        val fixed = SqlFixedExpenseRepository(db, io)
        val savings = SqlSavingsRepository(db, io)
        return Fixture(
            incomes, fixed, expenses,
            ObserveMonthSummary(SqlBudgetRepository(db, io), incomes, fixed, expenses, savings),
            savings,
        )
    }

    @Test
    fun incomeMinusThisMonthsShare() = runTest {
        val f = fixture()
        f.incomes.add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15))
        f.incomes.add(Income(name = "Kira geliri", amount = tl(8_000), dayOfMonth = 1))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 2), grossAmount = tl(1_000)))
        f.expenses.add(Expense(date = today, grossAmount = tl(600), chargeableFraction = 0.5)) // payı 300
        f.expenses.add(Expense(date = LocalDate(2024, 12, 31), grossAmount = tl(999))) // geçen ay

        val summary = f.observe(today).first()

        assertEquals(tl(48_000), summary.income)
        assertEquals(tl(1_300), summary.spent)
        assertEquals(tl(46_700), summary.remaining)
        assertEquals(LocalDate(2025, 1, 1)..LocalDate(2025, 1, 31), summary.monthStart..summary.monthEnd)
    }

    @Test
    fun nextPayday_isTheNearestIncomeDay() = runTest {
        val f = fixture()
        f.incomes.add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15)) // 15 Şubat
        f.incomes.add(Income(name = "Kira geliri", amount = tl(8_000), dayOfMonth = 1)) // 1 Şubat

        val payday = f.observe(today).first().nextPayday!!

        assertEquals("Kira geliri", payday.income.name)
        assertEquals(LocalDate(2025, 2, 1), payday.date)
        assertEquals(12, payday.daysAway)
    }

    @Test
    fun paydayToday_isZeroDaysAway() = runTest {
        val f = fixture()
        f.incomes.add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 20))
        assertEquals(0, f.observe(today).first().nextPayday!!.daysAway)
    }

    @Test
    fun withoutIncome() = runTest {
        val f = fixture()
        f.expenses.add(Expense(date = today, grossAmount = tl(100)))

        val summary = f.observe(today).first()

        assertFalse(summary.hasIncome)
        assertEquals(tl(-100), summary.remaining)
        assertNull(summary.nextPayday)
    }

    @Test
    fun fixedExpenses_plannedUntilPaid_thenActual_andNotCountedAsSpending() = runTest {
        val f = fixture()
        f.incomes.add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15))
        val rentId = f.fixed.add(FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 1))
        f.fixed.add(FixedExpense(name = "Elektrik", amount = tl(800), dayOfMonth = 25))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 5), grossAmount = tl(1_000)))

        val before = f.observe(today).first()
        assertEquals(tl(15_800), before.fixedTotal)
        assertEquals(2, before.pendingFixed)
        assertEquals(tl(23_200), before.remaining) // 40.000 − 15.800 − 1.000

        // Kira ödendi (gerçek tutar biraz farklı): harcama değil, sabit gider sayılır.
        f.expenses.add(Expense(date = LocalDate(2025, 1, 2), grossAmount = tl(15_100), fixedExpenseId = rentId))
        val after = f.observe(today).first()

        assertEquals(tl(15_900), after.fixedTotal)
        assertEquals(1, after.pendingFixed)
        assertEquals(tl(1_000), after.spent)
        assertEquals(tl(23_100), after.remaining)
        assertEquals(tl(5_500), after.weeklySuggestion) // (40.000 − 15.900) / 4,35 ≈ 5.542 → 50'liğe aşağı
    }

    @Test
    fun savingsThisMonth_areSetAsideFromRemaining() = runTest {
        val f = fixture()
        f.incomes.add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15))
        val goal = f.savings.addGoal(SavingsGoal(name = "Tatil", target = tl(20_000), createdOn = today))
        f.savings.addDeposit(SavingsDeposit(goalId = goal, amount = tl(1_000), date = LocalDate(2025, 1, 6)))
        f.savings.addDeposit(SavingsDeposit(goalId = goal, amount = tl(-200), date = LocalDate(2025, 1, 10))) // çekildi
        f.savings.addDeposit(SavingsDeposit(goalId = goal, amount = tl(5_000), date = LocalDate(2024, 12, 20))) // geçen ay

        val summary = f.observe(today).first()

        assertEquals(tl(800), summary.saved)
        assertEquals(tl(39_200), summary.remaining)
    }
}

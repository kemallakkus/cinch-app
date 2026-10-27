package com.cinch.shared.budget

import com.cinch.shared.data.SqlBudgetRepository
import com.cinch.shared.data.SqlExpenseRepository
import com.cinch.shared.data.SqlIncomeRepository
import com.cinch.shared.data.newDatabase
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.Income
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
        val expenses: SqlExpenseRepository,
        val observe: ObserveMonthSummary,
    )

    private fun TestScope.fixture(): Fixture {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val incomes = SqlIncomeRepository(db, io)
        val expenses = SqlExpenseRepository(db, io)
        return Fixture(incomes, expenses, ObserveMonthSummary(SqlBudgetRepository(db, io), incomes, expenses))
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
}

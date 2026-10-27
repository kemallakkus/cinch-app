package com.cinch.shared.budget

import com.cinch.shared.data.SqlBudgetRepository
import com.cinch.shared.data.SqlExpenseRepository
import com.cinch.shared.data.newDatabase
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class ObserveBudgetStatusTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val budgets: SqlBudgetRepository,
        val expenses: SqlExpenseRepository,
        val observe: ObserveBudgetStatus,
    )

    private fun TestScope.fixture(): Fixture {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val budgets = SqlBudgetRepository(db, io)
        val expenses = SqlExpenseRepository(db, io)
        return Fixture(budgets, expenses, ObserveBudgetStatus(budgets, expenses, BudgetEngine()))
    }

    @Test
    fun withoutBudget_emitsNull() = runTest {
        val f = fixture()
        assertNull(f.observe(LocalDate(2025, 1, 15)).first())
    }

    @Test
    fun countsWeekAcrossMonthBoundary() = runTest {
        val f = fixture()
        val today = LocalDate(2025, 2, 2) // Pazar; hafta 27 Ocak–2 Şubat
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 28), grossAmount = tl(200))) // geçen ay, bu hafta
        f.expenses.add(Expense(date = LocalDate(2025, 1, 20), grossAmount = tl(999))) // geçen hafta
        f.expenses.add(Expense(date = today, grossAmount = tl(100), chargeableFraction = 0.5))

        val status = f.observe(today).first()!!

        assertEquals(tl(250), status.weekSpent)
        assertEquals(tl(50), status.monthTotal) // yalnız Şubat
        assertEquals(tl(50), status.todaySpent)
    }

    @Test
    fun ignoresExpensesInOtherCurrency() = runTest {
        val f = fixture()
        val today = LocalDate(2025, 1, 15)
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.expenses.add(Expense(date = today, grossAmount = tl(100)))
        f.expenses.add(Expense(date = today, grossAmount = Money.of(20, Currency.EUR)))

        assertEquals(tl(100), f.observe(today).first()!!.weekSpent)
    }

    @Test
    fun recomputesWhenExpenseOrBudgetChanges() = runTest {
        val f = fixture()
        val today = LocalDate(2025, 1, 15)
        val spent = mutableListOf<Money?>()
        backgroundScope.launch { f.observe(today).collect { spent += it?.weekSpent } }
        runCurrent()

        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        runCurrent()
        f.expenses.add(Expense(date = today, grossAmount = tl(120)))
        runCurrent()
        f.budgets.save(BudgetConfig(weeklyLimit = tl(500))) // harcama aynı, durum yine yayılır
        runCurrent()

        assertEquals(listOf(null, tl(0), tl(120), tl(120)), spent)
    }

    @Test
    fun fixedPayments_doNotCountTowardWeeklyShare() = runTest {
        val f = fixture()
        val today = LocalDate(2025, 1, 15)
        f.budgets.save(BudgetConfig(weeklyLimit = tl(700)))
        f.expenses.add(Expense(date = today, grossAmount = tl(15_000), fixedExpenseId = 1)) // kira
        f.expenses.add(Expense(date = today, grossAmount = tl(100)))

        val status = f.observe(today).first()!!

        assertEquals(tl(100), status.weekSpent)
        assertEquals(tl(100), status.monthTotal)
    }
}

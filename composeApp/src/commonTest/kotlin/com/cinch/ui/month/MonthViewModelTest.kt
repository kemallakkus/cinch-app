package com.cinch.ui.month

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.FixedPaymentState
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.budget.ObserveSpendingBreakdown
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.Income
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeFixedExpenseRepository
import com.cinch.ui.FakeIncomeRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val vm: MonthViewModel,
        val incomes: FakeIncomeRepository,
        val fixed: FakeFixedExpenseRepository,
        val expenses: FakeExpenseRepository,
        val clock: FakeClock,
    ) {
        val state get() = vm.state.value
    }

    private fun testVm(today: LocalDate = LocalDate(2025, 1, 20), block: suspend TestScope.(Fixture) -> Unit) = withTestMain {
        runTest {
            val incomes = FakeIncomeRepository()
            val fixed = FakeFixedExpenseRepository()
            val expenses = FakeExpenseRepository()
            val clock = FakeClock(today)
            val vm = MonthViewModel(
                ObserveMonthSummary(FakeBudgetRepository(), incomes, fixed, expenses),
                ObserveSpendingBreakdown(FakeBudgetRepository(), expenses),
                incomes,
                FixedPayments(expenses),
                clock,
            )
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            block(Fixture(vm, incomes, fixed, expenses, clock))
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun summaryFollowsIncomesFixedAndExpenses_andMonthChangesOnResume() = testVm(today = LocalDate(2025, 1, 31)) { f ->
        assertFalse(f.state.summary!!.hasIncome)

        f.incomes.add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15))
        f.fixed.add(FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 1, kind = FixedExpenseKind.RENT))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 10), grossAmount = tl(1_000)))
        runCurrent()

        assertEquals(tl(24_000), f.state.summary!!.remaining) // 40.000 − 15.000 − 1.000

        // Şubat'a geçildi: Ocak harcaması artık bu ayın değil, kira yine düşülür.
        f.clock.date = LocalDate(2025, 2, 1)
        f.vm.onEvent(MonthEvent.Resumed)
        runCurrent()

        assertEquals(tl(25_000), f.state.summary!!.remaining)
        assertTrue(f.state.summary!!.hasIncome)
    }

    @Test
    fun markPaid_withCorrectedAmount_thenUndo() = testVm { f ->
        f.fixed.add(FixedExpense(name = "Elektrik", amount = tl(800), dayOfMonth = 20, kind = FixedExpenseKind.BILL))
        runCurrent()
        val status = f.state.summary!!.fixed.single()
        assertEquals(FixedPaymentState.DUE_TODAY, status.state)

        f.vm.onEvent(MonthEvent.FixedCheckClicked(status))
        assertIs<MonthDialog.Pay>(f.state.dialog)
        assertEquals("800", f.state.payAmount.text.toString())

        // Fatura bu ay farklı geldi.
        f.state.payAmount.setTextAndPlaceCursorAtEnd("912,40")
        Snapshot.sendApplyNotifications()
        runCurrent()
        f.vm.onEvent(MonthEvent.PayConfirmed)
        runCurrent()

        assertEquals(MonthEffect.Paid(finishedName = null), f.vm.effect.first())
        val paid = f.state.summary!!.fixed.single()
        assertEquals(FixedPaymentState.PAID, paid.state)
        assertEquals(Money(91_240, Currency.TRY), paid.amountForMonth)
        assertNull(f.state.dialog)

        f.vm.onEvent(MonthEvent.FixedCheckClicked(paid))
        assertIs<MonthDialog.Undo>(f.state.dialog)
        f.vm.onEvent(MonthEvent.UndoConfirmed)
        runCurrent()

        assertTrue(f.expenses.all.value.isEmpty())
        assertEquals(FixedPaymentState.DUE_TODAY, f.state.summary!!.fixed.single().state)
    }

    @Test
    fun invalidPaidAmount_keepsDialogOpen() = testVm { f ->
        f.fixed.add(FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 1))
        runCurrent()
        f.vm.onEvent(MonthEvent.FixedCheckClicked(f.state.summary!!.fixed.single()))
        f.state.payAmount.setTextAndPlaceCursorAtEnd("")
        Snapshot.sendApplyNotifications()
        runCurrent()

        f.vm.onEvent(MonthEvent.PayConfirmed)
        runCurrent()

        assertTrue(f.state.payAmountError)
        assertIs<MonthDialog.Pay>(f.state.dialog)
        assertTrue(f.expenses.all.value.isEmpty())
    }

    @Test
    fun breakdown_followsThisMonthsExpenses_withoutFixedPayments() = testVm { f ->
        assertTrue(f.state.breakdown!!.isEmpty)

        f.expenses.add(Expense(date = LocalDate(2025, 1, 5), grossAmount = tl(300), category = Category.YEMEK))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 6), grossAmount = tl(15_000), category = Category.EV, fixedExpenseId = 9))
        runCurrent()

        val breakdown = f.state.breakdown!!
        assertEquals(tl(300), breakdown.total)
        assertEquals(listOf(Category.YEMEK), breakdown.slices.map { it.category })
    }
}

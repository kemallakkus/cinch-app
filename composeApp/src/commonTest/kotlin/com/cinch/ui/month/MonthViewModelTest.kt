package com.cinch.ui.month

import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.Income
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeIncomeRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    @Test
    fun summaryFollowsIncomesAndExpenses_andMonthChangesOnResume() = withTestMain {
        runTest {
            val incomes = FakeIncomeRepository()
            val expenses = FakeExpenseRepository()
            val clock = FakeClock(LocalDate(2025, 1, 31))
            val vm = MonthViewModel(ObserveMonthSummary(FakeBudgetRepository(), incomes, expenses), incomes, clock)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertFalse(vm.state.value.summary!!.hasIncome)

            incomes.add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15))
            expenses.add(Expense(date = LocalDate(2025, 1, 10), grossAmount = tl(1_000)))
            runCurrent()

            assertEquals(tl(39_000), vm.state.value.summary!!.remaining)
            assertEquals(1, vm.state.value.incomes.size)

            // Şubat'a geçildi: Ocak harcaması artık bu ayın değil.
            clock.date = LocalDate(2025, 2, 1)
            vm.onEvent(MonthEvent.Resumed)
            runCurrent()

            assertEquals(tl(40_000), vm.state.value.summary!!.remaining)
            assertTrue(vm.state.value.summary!!.hasIncome)
            vm.viewModelScope.cancel()
        }
    }
}

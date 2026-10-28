package com.cinch.ui.insights

import androidx.lifecycle.viewModelScope
import com.cinch.navigation.InsightsRoute
import com.cinch.shared.budget.ObserveSpendingBreakdown
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {

    private val today = LocalDate(2025, 3, 20)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private fun testVm(route: InsightsRoute = InsightsRoute(), block: suspend TestScope.(InsightsViewModel, FakeExpenseRepository) -> Unit) =
        withTestMain {
            runTest {
                val expenses = FakeExpenseRepository()
                expenses.add(Expense(date = LocalDate(2025, 3, 3), grossAmount = tl(400), category = Category.YEMEK))
                expenses.add(Expense(date = LocalDate(2025, 2, 3), grossAmount = tl(100), category = Category.YEMEK))
                expenses.add(Expense(date = LocalDate(2025, 1, 3), grossAmount = tl(50), category = Category.GIYIM))
                val vm = InsightsViewModel(route, ObserveSpendingBreakdown(FakeBudgetRepository(), expenses), FakeClock(today))
                backgroundScope.launch { vm.state.collect {} }
                runCurrent()
                block(vm, expenses)
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun currentMonth_thenPreviousMonths() = testVm { vm, _ ->
        val march = vm.state.value
        assertFalse(march.isLoading)
        assertEquals(tl(400), march.breakdown!!.total)
        assertEquals(3.0, march.breakdown.change) // 100 → 400
        assertFalse(march.canGoNext)

        vm.onEvent(InsightsEvent.NextMonth) // gelecek ay yok
        runCurrent()
        assertEquals(LocalDate(2025, 3, 1), vm.state.value.month)

        vm.onEvent(InsightsEvent.PreviousMonth)
        runCurrent()
        val february = vm.state.value
        assertEquals(LocalDate(2025, 2, 1), february.month)
        assertEquals(tl(100), february.breakdown!!.total)
        assertEquals(listOf(Category.YEMEK, Category.GIYIM), february.breakdown.categories.map { it.category })
        assertTrue(february.canGoNext)
    }

    @Test
    fun routeMonth_isOpened() = testVm(InsightsRoute(year = 2025, month = 1)) { vm, _ ->
        assertEquals(LocalDate(2025, 1, 1), vm.state.value.month)
        assertEquals(tl(50), vm.state.value.breakdown!!.total)
    }
}

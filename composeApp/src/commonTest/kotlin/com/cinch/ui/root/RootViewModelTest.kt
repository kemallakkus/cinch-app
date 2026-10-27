package com.cinch.ui.root

import com.cinch.navigation.BudgetSetupRoute
import com.cinch.navigation.HomeRoute
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private fun testVm(block: suspend TestScope.() -> Unit) = withTestMain { runTest { block() } }

    @Test
    fun withoutBudget_startsWithOnboarding() = testVm {
        val vm = RootViewModel(FakeBudgetRepository(null))
        runCurrent()
        assertEquals(BudgetSetupRoute(isOnboarding = true), vm.startRoute.value)
    }

    @Test
    fun withBudget_startsAtHome() = testVm {
        val vm = RootViewModel(FakeBudgetRepository(BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY))))
        runCurrent()
        assertEquals(HomeRoute, vm.startRoute.value)
    }
}

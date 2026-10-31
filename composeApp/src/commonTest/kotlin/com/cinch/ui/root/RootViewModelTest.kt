package com.cinch.ui.root

import com.cinch.navigation.BudgetSetupRoute
import com.cinch.navigation.HomeRoute
import com.cinch.navigation.IntroRoute
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.prefs.InMemoryKeyValueStore
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private val budget = BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY))

    private fun testVm(block: suspend TestScope.() -> Unit) = withTestMain { runTest { block() } }

    @Test
    fun firstLaunch_startsWithIntro_thenBudgetSetup() = testVm {
        val prefs = InMemoryKeyValueStore()
        val vm = RootViewModel(FakeBudgetRepository(null), prefs)
        runCurrent()
        assertEquals(IntroRoute, vm.startRoute.value)

        vm.introFinished()

        // Tanıtım bitti ama bütçe kurulmadan çıkıldı: sonraki açılış kurulumla başlar.
        val again = RootViewModel(FakeBudgetRepository(null), prefs)
        runCurrent()
        assertEquals(BudgetSetupRoute(isOnboarding = true), again.startRoute.value)
    }

    @Test
    fun withBudget_startsAtHome_andSkipsIntroForGood() = testVm {
        val prefs = InMemoryKeyValueStore()
        val vm = RootViewModel(FakeBudgetRepository(budget), prefs)
        runCurrent()
        assertEquals(HomeRoute, vm.startRoute.value)
        assertTrue(prefs.getBoolean(RootViewModel.INTRO_SEEN)) // tanıtımdan önceki sürümden gelen kullanıcı
    }
}

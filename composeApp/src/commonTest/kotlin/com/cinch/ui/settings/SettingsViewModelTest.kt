package com.cinch.ui.settings

import androidx.lifecycle.viewModelScope
import com.cinch.notifications.NotificationAccess
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeAccess(var access: NotificationAccess) : NotificationAccessChecker {
    var requested = false
    override suspend fun current(): NotificationAccess = access
    override fun markRequested() {
        requested = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private fun testVm(access: NotificationAccess, block: suspend TestScope.(SettingsViewModel, FakeAccess) -> Unit) =
        withTestMain {
            runTest {
                val fake = FakeAccess(access)
                val vm = SettingsViewModel(FakeBudgetRepository(BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY))), fake)
                backgroundScope.launch { vm.state.collect {} }
                runCurrent()
                block(vm, fake)
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun showsBudgetAndNotificationState() = testVm(NotificationAccess.GRANTED) { vm, _ ->
        assertEquals(Money.of(700, Currency.TRY), vm.state.value.budget?.weeklyLimit)
        assertEquals(NotificationAccess.GRANTED, vm.state.value.notifications)
    }

    @Test
    fun askable_requestsPermission() = testVm(NotificationAccess.ASKABLE) { vm, access ->
        vm.onEvent(SettingsEvent.NotificationsClicked)
        assertTrue(access.requested)
        assertEquals(SettingsEffect.RequestPermission, vm.effect.first())
    }

    @Test
    fun denied_opensSystemSettings_andRefreshPicksUpChange() = testVm(NotificationAccess.DENIED) { vm, access ->
        vm.onEvent(SettingsEvent.NotificationsClicked)
        assertEquals(SettingsEffect.OpenSystemSettings, vm.effect.first())

        access.access = NotificationAccess.GRANTED // kullanıcı ayarlardan açıp döndü
        vm.onEvent(SettingsEvent.Refresh)
        runCurrent()

        assertEquals(NotificationAccess.GRANTED, vm.state.value.notifications)
    }
}

package com.cinch.ui.notifications

import androidx.lifecycle.viewModelScope
import com.cinch.notifications.NotificationAccess
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.shared.prefs.InMemoryKeyValueStore
import com.cinch.ui.FakeClock
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAccess(var access: NotificationAccess) : NotificationAccessChecker {
    var requested = false
    override suspend fun current(): NotificationAccess = access
    override fun markRequested() {
        requested = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPromptViewModelTest {

    private class Fixture(val vm: NotificationPromptViewModel, val access: FakeAccess, val clock: FakeClock) {
        val prompt get() = vm.state.value.prompt
    }

    private fun testVm(access: NotificationAccess, block: suspend TestScope.(Fixture) -> Unit) = withTestMain {
        runTest {
            val fake = FakeAccess(access)
            val clock = FakeClock(LocalDate(2025, 1, 15))
            val vm = NotificationPromptViewModel(fake, InMemoryKeyValueStore(), clock)
            runCurrent()
            block(Fixture(vm, fake, clock))
            vm.viewModelScope.cancel()
        }
    }

    private fun TestScope.send(f: Fixture, event: NotificationPromptEvent) {
        f.vm.onEvent(event)
        runCurrent()
    }

    @Test
    fun askable_explainsThenRequestsPermission() = testVm(NotificationAccess.ASKABLE) { f ->
        assertEquals(NotificationPrompt.ASK, f.prompt)

        send(f, NotificationPromptEvent.Accept)

        assertTrue(f.access.requested)
        assertEquals(NotificationPromptEffect.RequestPermission, f.vm.effect.first())
    }

    @Test
    fun denied_sendsToSettings() = testVm(NotificationAccess.DENIED) { f ->
        assertEquals(NotificationPrompt.OPEN_SETTINGS, f.prompt)

        send(f, NotificationPromptEvent.Accept)

        assertEquals(NotificationPromptEffect.OpenSettings, f.vm.effect.first())
    }

    @Test
    fun granted_showsNothing() = testVm(NotificationAccess.GRANTED) { f ->
        assertNull(f.prompt)
    }

    @Test
    fun grantedInSystemDialog_hidesCard() = testVm(NotificationAccess.ASKABLE) { f ->
        f.access.access = NotificationAccess.GRANTED
        send(f, NotificationPromptEvent.PermissionResult)
        assertNull(f.prompt)
    }

    @Test
    fun snooze_hidesCardForAWeek() = testVm(NotificationAccess.ASKABLE) { f ->
        send(f, NotificationPromptEvent.Snooze)
        assertNull(f.prompt)

        f.clock.date = LocalDate(2025, 1, 21)
        send(f, NotificationPromptEvent.Refresh)
        assertNull(f.prompt)

        f.clock.date = LocalDate(2025, 1, 22)
        send(f, NotificationPromptEvent.Refresh)
        assertEquals(NotificationPrompt.ASK, f.prompt)
    }
}

package com.cinch.ui.settings

import androidx.lifecycle.viewModelScope
import com.cinch.shared.coach.NotificationGroup
import com.cinch.shared.coach.NotificationPreferencesStore
import com.cinch.shared.coach.QuietHours
import com.cinch.shared.prefs.InMemoryKeyValueStore
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    private fun testVm(block: suspend TestScope.(NotificationSettingsViewModel, NotificationPreferencesStore) -> Unit) = withTestMain {
        runTest {
            val store = NotificationPreferencesStore(InMemoryKeyValueStore())
            val vm = NotificationSettingsViewModel(store)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            block(vm, store)
            vm.viewModelScope.cancel()
        }
    }

    private fun TestScope.send(vm: NotificationSettingsViewModel, event: NotificationSettingsEvent) {
        vm.onEvent(event)
        runCurrent()
    }

    @Test
    fun pickingATime_changesOnlyThatField() = testVm { vm, store ->
        send(vm, NotificationSettingsEvent.EditTime(TimeField.QUIET_START))
        assertEquals(TimeField.QUIET_START, vm.state.value.editing)

        send(vm, NotificationSettingsEvent.TimePicked(LocalTime(23, 30)))

        assertNull(vm.state.value.editing)
        assertEquals(QuietHours(LocalTime(23, 30), LocalTime(8, 0)), store.current.quietHours)

        send(vm, NotificationSettingsEvent.EditTime(TimeField.MIDDAY))
        send(vm, NotificationSettingsEvent.TimeDismissed)
        assertEquals(LocalTime(13, 0), store.current.middayTime)
    }

    @Test
    fun quietHours_offAndBackOn() = testVm { vm, store ->
        send(vm, NotificationSettingsEvent.QuietToggled(false))
        assertFalse(vm.state.value.quietEnabled)
        assertTrue(store.current.quietHours.start == store.current.quietHours.end)

        send(vm, NotificationSettingsEvent.QuietToggled(true))
        assertEquals(QuietHours(LocalTime(22, 0), LocalTime(8, 0)), store.current.quietHours)
    }

    @Test
    fun groupsAndDailyLimit() = testVm { vm, store ->
        send(vm, NotificationSettingsEvent.GroupToggled(NotificationGroup.HABITS, enabled = false))
        send(vm, NotificationSettingsEvent.MaxPerDayPicked(3))
        assertEquals(setOf(NotificationGroup.HABITS), vm.state.value.preferences.disabled)
        assertEquals(3, store.current.maxPerDay)

        send(vm, NotificationSettingsEvent.GroupToggled(NotificationGroup.HABITS, enabled = true))
        assertTrue(store.current.disabled.isEmpty())
    }
}

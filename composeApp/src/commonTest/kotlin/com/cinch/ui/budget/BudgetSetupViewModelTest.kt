package com.cinch.ui.budget

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.cinch.navigation.BudgetSetupRoute
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeBudgetRepository
import androidx.lifecycle.viewModelScope
import com.cinch.ui.withTestMain
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSetupViewModelTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private fun testVm(
        existing: BudgetConfig? = null,
        block: suspend TestScope.(BudgetSetupViewModel, FakeBudgetRepository) -> Unit,
    ) = withTestMain {
        runTest {
            val repo = FakeBudgetRepository(existing)
            val vm = BudgetSetupViewModel(BudgetSetupRoute(isOnboarding = existing == null), repo)
            runCurrent()
            block(vm, repo)
            vm.viewModelScope.cancel()
        }
    }

    private fun TestScope.type(field: TextFieldState, text: String) {
        field.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun onboarding_savesFormAndFinishes() = testVm { vm, repo ->
        assertTrue(vm.state.value.isOnboarding)
        type(vm.state.value.weeklyLimit, "1.500")
        type(vm.state.value.salaryDay, "15")
        vm.onEvent(BudgetSetupEvent.WeekStartPicked(DayOfWeek.SUNDAY))
        vm.onEvent(BudgetSetupEvent.Save)

        assertEquals(BudgetSetupEffect.Saved, vm.effect.first())
        assertEquals(
            BudgetConfig(weeklyLimit = tl(1500), weekStart = DayOfWeek.SUNDAY, salaryDay = 15),
            repo.config.value,
        )
    }

    @Test
    fun dailyHint_followsTypedLimit() = testVm { vm, _ ->
        type(vm.state.value.weeklyLimit, "700")
        assertEquals(tl(100), vm.state.value.dailyHint)

        type(vm.state.value.weeklyLimit, "abc")
        assertNull(vm.state.value.dailyHint)
    }

    @Test
    fun invalidLimit_showsErrorUntilEdited() = testVm { vm, repo ->
        vm.onEvent(BudgetSetupEvent.Save)
        runCurrent()
        assertTrue(vm.state.value.limitError)
        assertNull(repo.config.value)

        type(vm.state.value.weeklyLimit, "0")
        assertFalse(vm.state.value.limitError)
        vm.onEvent(BudgetSetupEvent.Save)
        runCurrent()
        assertTrue(vm.state.value.limitError) // sıfır sınır da geçersiz
    }

    @Test
    fun salaryDayOutOfRange_isRejected() = testVm { vm, repo ->
        type(vm.state.value.weeklyLimit, "700")
        type(vm.state.value.salaryDay, "40")
        vm.onEvent(BudgetSetupEvent.Save)
        runCurrent()

        assertTrue(vm.state.value.salaryDayError)
        assertFalse(vm.state.value.limitError)
        assertNull(repo.config.value)
    }

    @Test
    fun edit_prefillsFormAndKeepsHiddenFields() = testVm(
        existing = BudgetConfig(
            weeklyLimit = tl(700),
            weekStart = DayOfWeek.SUNDAY,
            weeklyGoal = tl(500),
            salaryDay = 1,
        ),
    ) { vm, repo ->
        val state = vm.state.value
        assertFalse(state.isOnboarding)
        assertFalse(state.isLoading)
        assertEquals("700", state.weeklyLimit.text.toString())
        assertEquals("1", state.salaryDay.text.toString())
        assertEquals(DayOfWeek.SUNDAY, state.weekStart)

        type(state.weeklyLimit, "800")
        vm.onEvent(BudgetSetupEvent.Save)

        assertEquals(BudgetSetupEffect.Saved, vm.effect.first())
        assertEquals(
            BudgetConfig(weeklyLimit = tl(800), weekStart = DayOfWeek.SUNDAY, weeklyGoal = tl(500), salaryDay = 1),
            repo.config.value,
        )
    }

    @Test
    fun edit_clearingSalaryDayRemovesIt() = testVm(
        existing = BudgetConfig(weeklyLimit = tl(700), salaryDay = 15),
    ) { vm, repo ->
        type(vm.state.value.salaryDay, "")
        vm.onEvent(BudgetSetupEvent.Save)

        assertEquals(BudgetSetupEffect.Saved, vm.effect.first())
        assertNull(repo.config.value?.salaryDay)
    }
}

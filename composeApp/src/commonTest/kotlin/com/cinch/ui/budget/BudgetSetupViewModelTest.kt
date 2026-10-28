package com.cinch.ui.budget

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.cinch.navigation.BudgetSetupRoute
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.Income
import com.cinch.shared.budget.ActiveFixedExpenses
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeFixedExpenseRepository
import com.cinch.ui.FakeIncomeRepository
import androidx.lifecycle.viewModelScope
import com.cinch.ui.withTestMain
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
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
        incomeList: List<Income> = emptyList(),
        fixedList: List<FixedExpense> = emptyList(),
        block: suspend TestScope.(BudgetSetupViewModel, FakeBudgetRepository) -> Unit,
    ) = withTestMain {
        runTest {
            val repo = FakeBudgetRepository(existing)
            val incomes = FakeIncomeRepository().apply { incomeList.forEach { add(it) } }
            val fixed = FakeFixedExpenseRepository().apply { fixedList.forEach { add(it) } }
            val vm = BudgetSetupViewModel(
                BudgetSetupRoute(isOnboarding = existing == null),
                repo,
                incomes,
                ActiveFixedExpenses(fixed, FakeExpenseRepository()),
                FakeClock(LocalDate(2025, 1, 15)),
            )
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
        vm.onEvent(BudgetSetupEvent.WeekStartPicked(DayOfWeek.SUNDAY))
        vm.onEvent(BudgetSetupEvent.Save)

        assertEquals(BudgetSetupEffect.Saved, vm.effect.first())
        assertEquals(
            BudgetConfig(weeklyLimit = tl(1500), weekStart = DayOfWeek.SUNDAY),
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
    fun edit_prefillsFormAndKeepsHiddenFields() = testVm(
        existing = BudgetConfig(
            weeklyLimit = tl(700),
            weekStart = DayOfWeek.SUNDAY,
            weeklyGoal = tl(500),
        ),
    ) { vm, repo ->
        val state = vm.state.value
        assertFalse(state.isOnboarding)
        assertFalse(state.isLoading)
        assertEquals("700", state.weeklyLimit.text.toString())
        assertEquals(DayOfWeek.SUNDAY, state.weekStart)

        type(state.weeklyLimit, "800")
        vm.onEvent(BudgetSetupEvent.Save)

        assertEquals(BudgetSetupEffect.Saved, vm.effect.first())
        assertEquals(
            BudgetConfig(weeklyLimit = tl(800), weekStart = DayOfWeek.SUNDAY, weeklyGoal = tl(500)),
            repo.config.value,
        )
    }

    @Test
    fun suggestion_fromIncomeAndFixedExpenses() = testVm(
        incomeList = listOf(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15)),
        fixedList = listOf(
            FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 1, kind = FixedExpenseKind.RENT),
            FixedExpense(name = "Faturalar", amount = tl(7_000), dayOfMonth = 10, kind = FixedExpenseKind.BILL),
        ),
    ) { vm, _ ->
        val suggestion = vm.state.value.suggestion!!
        assertEquals(tl(22_000), suggestion.monthlyFixed)
        assertEquals(tl(4_100), suggestion.weekly) // 18.000 / 4,35 ≈ 4.139 → 50'liğe aşağı

        vm.onEvent(BudgetSetupEvent.UseSuggestion)
        runCurrent()
        assertEquals("4100", vm.state.value.weeklyLimit.text.toString())
    }

    @Test
    fun noSuggestion_withoutIncome() = testVm { vm, _ ->
        assertNull(vm.state.value.suggestion)
    }
}

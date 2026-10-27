package com.cinch.ui.month

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.IncomeEditRoute
import com.cinch.shared.domain.Income
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeIncomeRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeEditViewModelTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private fun testVm(
        existing: Income? = null,
        block: suspend TestScope.(IncomeEditViewModel, FakeIncomeRepository) -> Unit,
    ) = withTestMain {
        runTest {
            val repo = FakeIncomeRepository()
            val id = existing?.let { repo.add(it) }
            val vm = IncomeEditViewModel(IncomeEditRoute(incomeId = id), repo)
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
    fun newIncome_saves() = testVm { vm, repo ->
        type(vm.state.value.name, "Maaş")
        type(vm.state.value.amount, "40.000")
        vm.onEvent(IncomeEditEvent.DayPicked(15))
        vm.onEvent(IncomeEditEvent.Save)

        assertEquals(IncomeEditEffect.Done, vm.effect.first())
        assertEquals(Income(id = 1, name = "Maaş", amount = tl(40_000), dayOfMonth = 15), repo.all.value.single())
    }

    @Test
    fun missingNameOrAmount_isRejected() = testVm { vm, repo ->
        type(vm.state.value.amount, "0")
        vm.onEvent(IncomeEditEvent.Save)
        runCurrent()

        assertTrue(vm.state.value.nameError && vm.state.value.amountError)
        assertTrue(repo.all.value.isEmpty())
    }

    @Test
    fun migratedSalaryWithoutAmount_opensWithEmptyAmount() = testVm(
        existing = Income(name = "Maaş", amount = tl(0), dayOfMonth = 15),
    ) { vm, repo ->
        val state = vm.state.value
        assertEquals("Maaş", state.name.text.toString())
        assertEquals("", state.amount.text.toString())
        assertEquals(15, state.dayOfMonth)

        type(state.amount, "42000")
        vm.onEvent(IncomeEditEvent.Save)

        assertEquals(IncomeEditEffect.Done, vm.effect.first())
        assertEquals(tl(42_000), repo.all.value.single().amount)
    }

    @Test
    fun delete_afterConfirmation() = testVm(existing = Income(name = "Kira geliri", amount = tl(8_000))) { vm, repo ->
        vm.onEvent(IncomeEditEvent.DeleteRequested)
        assertTrue(vm.state.value.isConfirmingDelete)

        vm.onEvent(IncomeEditEvent.DeleteConfirmed)

        assertEquals(IncomeEditEffect.Done, vm.effect.first())
        assertTrue(repo.all.value.isEmpty())
    }
}

package com.cinch.ui.month

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.FixedEditRoute
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.FakeFixedExpenseRepository
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
class FixedEditViewModelTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private fun testVm(
        existing: FixedExpense? = null,
        block: suspend TestScope.(FixedEditViewModel, FakeFixedExpenseRepository) -> Unit,
    ) = withTestMain {
        runTest {
            val repo = FakeFixedExpenseRepository()
            val cards = FakeCardRepository().apply { add(Card(name = "QNB", carriedBalance = tl(0))) }
            val id = existing?.let { repo.add(it) }
            val vm = FixedEditViewModel(FixedEditRoute(fixedId = id), repo, cards)
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
    fun newFixed_namePrefilledFromKind_andSavesWithCard() = testVm { vm, repo ->
        assertEquals("Kira", vm.state.value.name.text.toString())
        assertEquals(1, vm.state.value.cards.size)

        vm.onEvent(FixedEditEvent.KindPicked(FixedExpenseKind.SUBSCRIPTION))
        assertEquals("Abonelik", vm.state.value.name.text.toString()) // kullanıcı yazmadıysa ad türle değişir
        type(vm.state.value.name, "Netflix")
        vm.onEvent(FixedEditEvent.KindPicked(FixedExpenseKind.BILL))
        assertEquals("Netflix", vm.state.value.name.text.toString()) // yazılan ada dokunulmaz

        type(vm.state.value.amount, "229,99")
        vm.onEvent(FixedEditEvent.DayPicked(5))
        vm.onEvent(FixedEditEvent.CardPicked(1))
        vm.onEvent(FixedEditEvent.Save)

        assertEquals(FixedEditEffect.Done, vm.effect.first())
        assertEquals(
            FixedExpense(id = 1, name = "Netflix", amount = Money(22_999, Currency.TRY), dayOfMonth = 5, kind = FixedExpenseKind.BILL, cardId = 1),
            repo.all.value.single(),
        )
    }

    @Test
    fun missingAmountOrDay_isRejected() = testVm { vm, repo ->
        vm.onEvent(FixedEditEvent.Save)
        runCurrent()

        assertTrue(vm.state.value.amountError && vm.state.value.dayError)
        assertTrue(repo.all.value.isEmpty())
    }

    @Test
    fun edit_prefillsAndUpdates() = testVm(
        existing = FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 1, kind = FixedExpenseKind.RENT),
    ) { vm, repo ->
        val state = vm.state.value
        assertEquals("15000", state.amount.text.toString())
        assertEquals(1, state.dayOfMonth)
        assertEquals(FixedExpenseKind.RENT, state.kind)

        type(state.amount, "16.500")
        vm.onEvent(FixedEditEvent.Save)

        assertEquals(FixedEditEffect.Done, vm.effect.first())
        assertEquals(tl(16_500), repo.all.value.single().amount)
    }

    @Test
    fun installments_validatesCounts_andSaves() = testVm { vm, repo ->
        vm.onEvent(FixedEditEvent.PlanPicked(FixedPlan.INSTALLMENTS))
        assertEquals(FixedExpenseKind.LOAN, vm.state.value.kind) // varsayılan "Kira" borçta krediye döner
        type(vm.state.value.name, "Ablama borç")
        type(vm.state.value.amount, "5000")
        type(vm.state.value.totalInstallments, "6")
        type(vm.state.value.paidBefore, "7") // toplamdan fazla
        vm.onEvent(FixedEditEvent.DayPicked(10))
        vm.onEvent(FixedEditEvent.Save)
        runCurrent()
        assertTrue(vm.state.value.installmentsError)
        assertTrue(repo.all.value.isEmpty())

        type(vm.state.value.paidBefore, "4")
        vm.onEvent(FixedEditEvent.Save)

        assertEquals(FixedEditEffect.Done, vm.effect.first())
        val saved = repo.all.value.single()
        assertEquals(FixedPlan.INSTALLMENTS, saved.plan)
        assertEquals(6, saved.totalInstallments)
        assertEquals(4, saved.paidBefore)
        assertEquals(null, saved.debt)
    }

    @Test
    fun flexible_requiresDebt_usualAmountOptional() = testVm { vm, repo ->
        vm.onEvent(FixedEditEvent.PlanPicked(FixedPlan.FLEXIBLE))
        type(vm.state.value.name, "Eminevim")
        vm.onEvent(FixedEditEvent.DayPicked(5))
        vm.onEvent(FixedEditEvent.Save)
        runCurrent()
        assertTrue(vm.state.value.debtError)
        assertTrue(!vm.state.value.amountError) // "genelde ödediğim" boş olabilir

        type(vm.state.value.debt, "180.000")
        vm.onEvent(FixedEditEvent.Save)

        assertEquals(FixedEditEffect.Done, vm.effect.first())
        val saved = repo.all.value.single()
        assertEquals(FixedPlan.FLEXIBLE, saved.plan)
        assertEquals(tl(180_000), saved.debt)
        assertEquals(tl(0), saved.amount)
        assertEquals(null, saved.totalInstallments)
    }
}

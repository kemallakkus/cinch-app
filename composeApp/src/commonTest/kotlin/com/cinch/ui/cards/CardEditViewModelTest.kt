package com.cinch.ui.cards

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.CardEditRoute
import com.cinch.shared.domain.Card
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CardEditViewModelTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private fun testVm(
        existing: Card? = null,
        block: suspend TestScope.(CardEditViewModel, FakeCardRepository) -> Unit,
    ) = withTestMain {
        runTest {
            val repo = FakeCardRepository()
            val id = existing?.let { repo.add(it) }
            val vm = CardEditViewModel(CardEditRoute(cardId = id), repo)
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
    fun newCard_savesWithDefaults() = testVm { vm, repo ->
        type(vm.state.value.name, "  QNB ")
        vm.onEvent(CardEditEvent.Save)

        assertEquals(CardEditEffect.Saved, vm.effect.first())
        // Boş borç sıfır, oran varsayılan 4,9.
        assertEquals(Card(id = 1, name = "QNB", carriedBalance = tl(0), monthlyRatePercent = 4.9), repo.all.value.single())
    }

    @Test
    fun invalidFields_showErrorsAndDoNotSave() = testVm { vm, repo ->
        type(vm.state.value.statementDay, "40")
        type(vm.state.value.carriedBalance, "abc")
        type(vm.state.value.monthlyRate, "")
        vm.onEvent(CardEditEvent.Save)
        runCurrent()

        val state = vm.state.value
        assertTrue(state.nameError && state.statementDayError && state.balanceError && state.rateError)
        assertTrue(repo.all.value.isEmpty())

        type(state.name, "QNB")
        assertFalse(vm.state.value.nameError)
        assertTrue(vm.state.value.statementDayError) // diğer alanların hatası kalır
    }

    @Test
    fun edit_prefillsAndUpdates() = testVm(
        existing = Card(name = "QNB", carriedBalance = tl(12_500), monthlyRatePercent = 4.25, statementDay = 15),
    ) { vm, repo ->
        val state = vm.state.value
        assertFalse(state.isNew)
        assertEquals("QNB", state.name.text.toString())
        assertEquals("15", state.statementDay.text.toString())
        assertEquals("12500", state.carriedBalance.text.toString())
        assertEquals("4,25", state.monthlyRate.text.toString())

        type(state.carriedBalance, "11.000")
        type(state.statementDay, "")
        vm.onEvent(CardEditEvent.Save)

        assertEquals(CardEditEffect.Saved, vm.effect.first())
        assertEquals(
            Card(id = 1, name = "QNB", carriedBalance = tl(11_000), monthlyRatePercent = 4.25, statementDay = null),
            repo.all.value.single(),
        )
    }

    @Test
    fun delete_asksFirstThenRemoves() = testVm(existing = Card(name = "QNB", carriedBalance = tl(0))) { vm, repo ->
        vm.onEvent(CardEditEvent.DeleteRequested)
        assertTrue(vm.state.value.isConfirmingDelete)
        assertEquals(1, repo.all.value.size)

        vm.onEvent(CardEditEvent.DeleteConfirmed)

        assertEquals(CardEditEffect.Deleted(1), vm.effect.first())
        assertTrue(repo.all.value.isEmpty())
    }
}

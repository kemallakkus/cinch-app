package com.cinch.ui.subscriptions

import androidx.lifecycle.viewModelScope
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.prefs.InMemoryKeyValueStore
import com.cinch.shared.recurring.ObserveRecurringPayments
import com.cinch.shared.recurring.RecurringDismissals
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeFixedExpenseRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {

    private val today = LocalDate(2025, 4, 8)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val vm: SubscriptionsViewModel,
        val expenses: FakeExpenseRepository,
        val fixed: FakeFixedExpenseRepository,
        val effects: List<SubscriptionsEffect>,
    )

    private fun testVm(block: suspend TestScope.(Fixture) -> Unit) = withTestMain {
        runTest {
            val expenses = FakeExpenseRepository()
            val fixed = FakeFixedExpenseRepository()
            val dismissals = RecurringDismissals(InMemoryKeyValueStore())
            val clock = FakeClock(today)
            listOf(2, 3, 4).forEach { month ->
                expenses.add(
                    Expense(
                        date = LocalDate(2025, month, 5), grossAmount = tl(199), category = Category.EGLENCE,
                        tokens = listOf("netflix"), rawText = "199 netflix", cardId = 7,
                    ),
                )
            }
            val vm = SubscriptionsViewModel(ObserveRecurringPayments(expenses, fixed, dismissals, clock), fixed, expenses, dismissals, clock)
            val effects = mutableListOf<SubscriptionsEffect>()
            backgroundScope.launch { vm.state.collect {} }
            backgroundScope.launch { vm.effect.collect { effects += it } }
            runCurrent()
            block(Fixture(vm, expenses, fixed, effects))
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun found_andMadeFixed_convertsThisMonthsPayment() = testVm { f ->
        val item = f.vm.state.value.items.single()
        assertEquals(tl(199), f.vm.state.value.monthlyTotal)

        f.vm.onEvent(SubscriptionsEvent.MakeFixed(item))
        runCurrent()

        val created = f.fixed.all.value.single()
        assertEquals("Netflix", created.name)
        assertEquals(tl(199), created.amount)
        assertEquals(5, created.dayOfMonth)
        assertEquals(FixedExpenseKind.SUBSCRIPTION, created.kind)
        assertEquals(7, created.cardId)
        // Nisan'ın ödemesi sabit giderin ödemesi oldu; Şubat ve Mart serbest harcama kaldı.
        val april = f.expenses.all.value.single { it.date == LocalDate(2025, 4, 5) }
        assertEquals(created.id, april.fixedExpenseId)
        assertEquals(2, f.expenses.all.value.count { it.fixedExpenseId == null })
        assertTrue(f.vm.state.value.items.isEmpty()) // artık takipte
        assertTrue(f.effects.single() is SubscriptionsEffect.Message)
    }

    @Test
    fun notASubscription_isNotSuggestedAgain() = testVm { f ->
        f.vm.onEvent(SubscriptionsEvent.Dismiss(f.vm.state.value.items.single()))
        runCurrent()
        assertTrue(f.vm.state.value.items.isEmpty())
        assertTrue(f.fixed.all.value.isEmpty())
    }
}

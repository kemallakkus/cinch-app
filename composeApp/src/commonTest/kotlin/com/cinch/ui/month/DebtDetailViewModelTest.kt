package com.cinch.ui.month

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.DebtDetailRoute
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeFixedExpenseRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebtDetailViewModelTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(val vm: DebtDetailViewModel, val expenses: FakeExpenseRepository) {
        val state get() = vm.state.value
    }

    private fun testVm(fixed: FixedExpense, block: suspend TestScope.(Fixture) -> Unit) = withTestMain {
        runTest {
            val repo = FakeFixedExpenseRepository()
            val expenses = FakeExpenseRepository()
            val id = repo.add(fixed)
            val vm = DebtDetailViewModel(DebtDetailRoute(id), repo, expenses, FixedPayments(expenses), FakeClock(LocalDate(2025, 1, 20)))
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            block(Fixture(vm, expenses))
            vm.viewModelScope.cancel()
        }
    }

    private fun TestScope.pay(f: Fixture, amount: String) {
        f.vm.onEvent(DebtDetailEvent.AddPaymentClicked)
        f.state.payAmount.setTextAndPlaceCursorAtEnd(amount)
        Snapshot.sendApplyNotifications()
        runCurrent()
        f.vm.onEvent(DebtDetailEvent.PayConfirmed)
        runCurrent()
    }

    @Test
    fun flexible_variablePayments_untilFinished() = testVm(
        FixedExpense(name = "Eminevim", amount = tl(0), dayOfMonth = 5, plan = FixedPlan.FLEXIBLE, debt = tl(50_000)),
    ) { f ->
        f.vm.onEvent(DebtDetailEvent.AddPaymentClicked)
        assertEquals("", f.state.payAmount.text.toString()) // "genelde ödediğim" yok: boş açılır
        f.vm.onEvent(DebtDetailEvent.DialogDismissed)

        pay(f, "25.000")
        assertEquals(DebtDetailEffect.Paid(finished = false), f.vm.effect.first())
        pay(f, "2.400")
        assertEquals(DebtDetailEffect.Paid(finished = false), f.vm.effect.first())
        assertEquals(tl(22_600), f.state.progress!!.remainingDebt)
        assertEquals(2, f.state.payments.size) // aynı ay iki ödeme olabilir

        pay(f, "22.600")
        assertEquals(DebtDetailEffect.Paid(finished = true), f.vm.effect.first())
        assertTrue(f.state.progress!!.isFinished)
    }

    @Test
    fun deletingPayment_restoresDebt() = testVm(
        FixedExpense(name = "Ablama borç", amount = tl(5_000), dayOfMonth = 10, plan = FixedPlan.INSTALLMENTS, totalInstallments = 6, paidBefore = 4),
    ) { f ->
        pay(f, "5000")
        assertEquals(5, f.state.progress!!.paidCount)

        f.vm.onEvent(DebtDetailEvent.PaymentClicked(f.state.payments.single()))
        assertIs<DebtDialog.DeletePayment>(f.state.dialog)
        f.vm.onEvent(DebtDetailEvent.DeleteConfirmed)
        runCurrent()

        assertEquals(4, f.state.progress!!.paidCount)
        assertTrue(f.expenses.all.value.isEmpty())
    }
}

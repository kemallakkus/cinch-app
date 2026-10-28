package com.cinch.ui.cards

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.CardDetailRoute
import com.cinch.shared.debt.PayoffSimulator
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.reconcile.ReconciliationKind
import com.cinch.shared.reconcile.Reconciler
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.FakeCardStatementRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CardDetailViewModelTest {

    private val today = LocalDate(2025, 2, 3)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val vm: CardDetailViewModel,
        val expenses: FakeExpenseRepository,
        val statements: FakeCardStatementRepository,
        val cardId: Long,
    ) {
        val state get() = vm.state.value
    }

    private fun testVm(
        card: Card = Card(name = "QNB", carriedBalance = Money.of(10_000, Currency.TRY), monthlyRatePercent = 5.0, statementDay = 15),
        block: suspend TestScope.(Fixture) -> Unit,
    ) = withTestMain {
        runTest {
            val expenses = FakeExpenseRepository()
            val cards = FakeCardRepository(expenses)
            val id = cards.add(card)
            val statements = FakeCardStatementRepository()
            val vm = CardDetailViewModel(
                route = CardDetailRoute(id),
                cards = cards,
                expenses = expenses,
                statements = statements,
                reconciler = Reconciler(),
                simulator = PayoffSimulator(),
                clock = FakeClock(today),
            )
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            block(Fixture(vm, expenses, statements, id))
            vm.viewModelScope.cancel()
        }
    }

    private fun TestScope.type(field: TextFieldState, text: String) {
        field.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun periodTotal_countsOnlyThisStatementPeriod() = testVm { f ->
        f.expenses.add(Expense(date = LocalDate(2025, 1, 15), grossAmount = tl(999), cardId = f.cardId)) // önceki ekstre
        f.expenses.add(Expense(date = LocalDate(2025, 1, 16), grossAmount = tl(200), cardId = f.cardId))
        f.expenses.add(Expense(date = today, grossAmount = tl(100), chargeableFraction = 0.5, cardId = f.cardId))
        f.expenses.add(Expense(date = today, grossAmount = tl(50))) // nakit
        runCurrent()

        assertEquals(LocalDate(2025, 1, 16)..today, f.state.period)
        assertEquals(tl(300), f.state.periodTotal) // brüt: paylaşılan harcamanın tamamı karta girdi
        assertEquals(2, f.state.periodExpenses.size)
    }

    @Test
    fun payoff_followsTypedPayment() = testVm { f ->
        assertNull(f.state.payoff)

        type(f.state.monthlyPayment, "1.000")

        // 10.000 ₺, aylık %5, ayda 1.000 ₺: 14 ödemeden sonra ~201 ₺ kalır, 15. ayda biter.
        val payoff = assertNotNull(f.state.payoff)
        assertTrue(payoff.closes)
        assertEquals(15, payoff.months)
    }

    @Test
    fun payoff_paymentBelowInterestNeverCloses() = testVm { f ->
        type(f.state.monthlyPayment, "400") // aylık faiz 500 ₺

        assertFalse(f.state.payoff!!.closes)
        assertEquals(tl(500), f.state.firstMonthInterest)
    }

    @Test
    fun reconcile_missingDifferenceCanBeAdded() = testVm { f ->
        f.expenses.add(Expense(date = LocalDate(2025, 1, 20), grossAmount = tl(200), cardId = f.cardId))
        runCurrent()
        type(f.state.statedSpending, "303")
        f.vm.onEvent(CardDetailEvent.Reconcile)
        runCurrent()

        val result = f.state.reconciliation!!
        assertEquals(ReconciliationKind.MISSING_EXPENSE, result.kind)
        assertEquals(tl(103), result.difference)

        f.vm.onEvent(CardDetailEvent.AddMissingDifference)
        runCurrent()

        assertEquals(ReconciliationKind.MATCH, f.state.reconciliation!!.kind)
        val added = f.expenses.all.value.last()
        assertEquals(tl(103), added.grossAmount)
        assertEquals(f.cardId, added.cardId)
        assertEquals(today, added.date)
    }

    @Test
    fun reconcile_extraRecorded() = testVm { f ->
        f.expenses.add(Expense(date = LocalDate(2025, 1, 20), grossAmount = tl(200), cardId = f.cardId))
        runCurrent()
        type(f.state.statedSpending, "150")
        f.vm.onEvent(CardDetailEvent.Reconcile)
        runCurrent()

        assertEquals(ReconciliationKind.EXTRA_RECORDED, f.state.reconciliation!!.kind)
    }

    @Test
    fun reconcile_invalidAmountShowsError_andEditingHidesOldResult() = testVm { f ->
        f.vm.onEvent(CardDetailEvent.Reconcile)
        runCurrent()
        assertTrue(f.state.statedSpendingError)

        type(f.state.statedSpending, "0")
        f.vm.onEvent(CardDetailEvent.Reconcile)
        runCurrent()
        assertNotNull(f.state.reconciliation)

        type(f.state.statedSpending, "10")
        assertNull(f.state.reconciliation)
        assertFalse(f.state.statedSpendingError)
    }

    @Test
    fun statement_latestReading_andPaymentProgressForSameDueDate() = testVm { f ->
        assertNull(f.state.statement)
        val due = LocalDate(2025, 2, 6)
        f.statements.add(CardStatement(cardId = f.cardId, readOn = LocalDate(2025, 2, 1), dueDate = due, statementBalance = tl(52_331)))
        f.statements.add(CardStatement(cardId = f.cardId, readOn = LocalDate(2025, 2, 3), dueDate = due, statementBalance = tl(31_393)))
        runCurrent()

        assertEquals(tl(31_393), f.state.statement?.statementBalance)
        assertEquals(tl(52_331), f.state.statementStartBalance)
        assertEquals(tl(20_938), f.state.statementPaid) // 52.331 → 31.393
        assertEquals(3, f.state.statement?.daysUntilDue(today))
    }

    @Test
    fun statementHistory_oneBarPerDueDate_andRecordedShare() = testVm { f ->
        // Kart kesim günü 15: bugün (3 Şubat) dönem 16 Ocak'tan başlar.
        f.expenses.add(Expense(date = LocalDate(2025, 1, 20), grossAmount = tl(900), cardId = f.cardId))
        f.statements.add(CardStatement(cardId = f.cardId, readOn = LocalDate(2024, 12, 20), dueDate = LocalDate(2024, 12, 25), statementBalance = tl(4_000)))
        f.statements.add(CardStatement(cardId = f.cardId, readOn = LocalDate(2025, 1, 20), dueDate = LocalDate(2025, 1, 25), statementBalance = tl(5_000)))
        f.statements.add(CardStatement(cardId = f.cardId, readOn = LocalDate(2025, 1, 23), dueDate = LocalDate(2025, 1, 25), statementBalance = tl(1_000)))
        f.statements.add(
            CardStatement(cardId = f.cardId, readOn = LocalDate(2025, 2, 2), dueDate = LocalDate(2025, 2, 25), statementBalance = tl(6_000), periodSpending = tl(1_000)),
        )
        runCurrent()

        // Ocak ekstresi ödendikçe 5.000 → 1.000 okunmuş; grafikte ilk (tam) tutar.
        assertEquals(listOf(tl(4_000), tl(5_000), tl(6_000)), f.state.statementHistory.map { it.amount })
        assertEquals(0.9f, f.state.recordedShare) // banka 1.000 diyor, Cinch'te 900
    }
}

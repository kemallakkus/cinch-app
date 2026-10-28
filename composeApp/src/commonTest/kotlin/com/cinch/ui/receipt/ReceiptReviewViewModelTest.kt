package com.cinch.ui.receipt

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.ReceiptReviewRoute
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.InMemoryCorrectionStore
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.receipt.ReceiptParser
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeFixedExpenseRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptReviewViewModelTest {

    private val today = LocalDate(2025, 3, 15)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val vm: ReceiptReviewViewModel,
        val expenses: FakeExpenseRepository,
        val categorizer: Categorizer,
    ) {
        val state get() = vm.state.value
    }

    private class Seed(
        val cards: List<Card> = emptyList(),
        val fixed: List<FixedExpense> = emptyList(),
        val expenses: List<Expense> = emptyList(),
    )

    private fun testVm(text: String, seed: Seed = Seed(), block: suspend TestScope.(Fixture) -> Unit) = withTestMain {
        runTest {
            val expenses = FakeExpenseRepository()
            val cards = FakeCardRepository(expenses)
            val fixed = FakeFixedExpenseRepository()
            seed.cards.forEach { cards.add(it) }
            seed.fixed.forEach { fixed.add(it) }
            seed.expenses.forEach { expenses.add(it) }
            val categorizer = Categorizer(corrections = InMemoryCorrectionStore())
            val vm = ReceiptReviewViewModel(
                route = ReceiptReviewRoute(text),
                receiptParser = ReceiptParser(categorizer),
                entryParser = QuickEntryParser(categorizer),
                categorizer = categorizer,
                expenses = expenses,
                cards = cards,
                fixedExpenses = fixed,
                fixedPayments = FixedPayments(expenses),
                clock = FakeClock(today),
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            runCurrent()
            block(Fixture(vm, expenses, categorizer))
            vm.viewModelScope.cancel()
        }
    }

    private val receipt = "MİGROS TİCARET A.Ş.\nTARİH : 14/03/2025\nTOPLAM *96,15\nKREDİ KARTI *96,15"

    private val electricityBill =
        "ENERJİSA BOĞAZİÇİ\nAbone No 123\nFatura Tarihi 05.03.2025\nÖdenecek Tutar 912,40\nSon Ödeme Tarihi 20.03.2025"

    private val electricity = FixedExpense(name = "Elektrik", amount = Money.of(900, Currency.TRY), dayOfMonth = 20, kind = FixedExpenseKind.BILL)
    private val rent = FixedExpense(name = "Kira", amount = Money.of(15_000, Currency.TRY), dayOfMonth = 1, kind = FixedExpenseKind.RENT)

    @Test
    fun receipt_isPrefilled_andSavedAsExpense() = testVm(
        receipt,
        Seed(cards = listOf(Card(name = "QNB", carriedBalance = Money.of(0, Currency.TRY)))),
    ) { f ->
        assertEquals("96,15", f.state.amount.text.toString())
        assertEquals("Migros", f.state.text.text.toString())
        assertEquals(Category.MARKET, f.state.category)
        assertEquals(LocalDate(2025, 3, 14), f.state.date)
        assertEquals(1L, f.state.cardId) // fişte kartla ödendi yazıyor, tek kart var
        assertTrue(!f.state.showFixedLink) // fatura değil

        f.vm.onEvent(ReceiptReviewEvent.Save)

        assertEquals(ReceiptReviewEffect.Saved(), f.vm.effect.first())
        val saved = f.expenses.all.value.single()
        assertEquals(Money(9_615, Currency.TRY), saved.grossAmount)
        assertEquals("Migros", saved.rawText)
        assertEquals(listOf("migros"), saved.tokens)
        assertEquals(1L, saved.cardId)
        assertNull(saved.fixedExpenseId)
    }

    @Test
    fun bill_isLinkedToMatchingFixedExpense_andMarkedPaidToday() = testVm(
        electricityBill,
        Seed(fixed = listOf(rent, electricity)),
    ) { f ->
        assertTrue(f.state.showFixedLink)
        assertEquals("Elektrik", f.state.linkedFixed?.name)
        assertEquals(today, f.state.date) // faturanın tarihi değil, ödeme günü

        f.vm.onEvent(ReceiptReviewEvent.Save)

        assertEquals(ReceiptReviewEffect.Saved(fixedName = "Elektrik"), f.vm.effect.first())
        val payment = f.expenses.all.value.single()
        assertEquals(2L, payment.fixedExpenseId)
        assertEquals(Money(91_240, Currency.TRY), payment.grossAmount)
        assertEquals(today, payment.date)
        assertEquals(Category.FATURA, payment.category)
    }

    @Test
    fun bill_canBeSavedAsPlainExpense() = testVm(electricityBill, Seed(fixed = listOf(electricity))) { f ->
        f.vm.onEvent(ReceiptReviewEvent.FixedLinked(null))
        f.vm.onEvent(ReceiptReviewEvent.Save)

        assertEquals(ReceiptReviewEffect.Saved(), f.vm.effect.first())
        val saved = f.expenses.all.value.single()
        assertNull(saved.fixedExpenseId)
        assertEquals(Category.FATURA, saved.category)
    }

    @Test
    fun alreadyPaidThisMonth_isFlagged() = testVm(
        electricityBill,
        Seed(
            fixed = listOf(electricity),
            expenses = listOf(Expense(date = LocalDate(2025, 3, 2), grossAmount = Money.of(900, Currency.TRY), fixedExpenseId = 1)),
        ),
    ) { f ->
        assertTrue(1L in f.state.paidThisMonth)
    }

    @Test
    fun unreadAmount_mustBeTyped() = testVm("BULANIK BİR ŞEY") { f ->
        assertEquals("", f.state.amount.text.toString())
        f.vm.onEvent(ReceiptReviewEvent.Save)
        runCurrent()
        assertTrue(f.state.amountError)
        assertTrue(f.expenses.all.value.isEmpty())

        f.state.amount.setTextAndPlaceCursorAtEnd("45")
        Snapshot.sendApplyNotifications()
        runCurrent()
        f.vm.onEvent(ReceiptReviewEvent.Save)
        assertEquals(ReceiptReviewEffect.Saved(), f.vm.effect.first())
        assertEquals(tl(45), f.expenses.all.value.single().grossAmount)
    }

    @Test
    fun categoryCorrection_isLearned() = testVm("ZIPZIP DÜKKAN\nTOPLAM *50,00") { f ->
        assertEquals(Category.DIGER, f.state.category)
        f.vm.onEvent(ReceiptReviewEvent.CategoryPicked(Category.GIYIM))
        f.vm.onEvent(ReceiptReviewEvent.Save)

        assertEquals(ReceiptReviewEffect.Saved(), f.vm.effect.first())
        assertEquals(Category.GIYIM, f.categorizer.categorize("zıpzıp").category)
    }
}

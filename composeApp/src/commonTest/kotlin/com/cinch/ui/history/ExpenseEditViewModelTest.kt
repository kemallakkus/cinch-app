package com.cinch.ui.history

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.ExpenseEditRoute
import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.InMemoryCorrectionStore
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.QuickEntryParser
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseEditViewModelTest {

    private val today = LocalDate(2025, 1, 15)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val vm: ExpenseEditViewModel,
        val expenses: FakeExpenseRepository,
        val categorizer: Categorizer,
        val id: Long,
    ) {
        val state get() = vm.state.value
        val saved get() = expenses.all.value.single { it.id == id }
    }

    private fun testVm(
        existing: Expense?,
        fixed: FixedExpense? = null,
        block: suspend TestScope.(Fixture) -> Unit,
    ) = withTestMain {
        runTest {
            val expenses = FakeExpenseRepository()
            val cards = FakeCardRepository(expenses).apply { add(Card(name = "QNB", carriedBalance = tl(0))) }
            val fixedRepo = FakeFixedExpenseRepository()
            fixed?.let { fixedRepo.add(it) }
            val id: Long = existing?.let { expenses.add(it) } ?: 99L
            val categorizer = Categorizer(corrections = InMemoryCorrectionStore())
            val vm = ExpenseEditViewModel(
                route = ExpenseEditRoute(id),
                expenses = expenses,
                cards = cards,
                fixedExpenses = fixedRepo,
                parser = QuickEntryParser(categorizer),
                categorizer = categorizer,
                clock = FakeClock(today),
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            runCurrent()
            block(Fixture(vm, expenses, categorizer, id))
            vm.viewModelScope.cancel()
        }
    }

    private fun TestScope.type(field: TextFieldState, text: String) {
        field.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private val market = Expense(
        date = today,
        grossAmount = Money.of(195, Currency.TRY),
        category = Category.MARKET,
        rawText = "195 market",
        tokens = listOf("market"),
    )

    @Test
    fun prefills_thenAmountChangeUpdatesNumberInText() = testVm(market) { f ->
        assertEquals("195", f.state.amount.text.toString())
        assertEquals("195 market", f.state.text.text.toString())
        assertEquals(Category.MARKET, f.state.category)

        type(f.state.amount, "200")
        f.vm.onEvent(ExpenseEditEvent.Save)

        assertEquals(ExpenseEditEffect.Done, f.vm.effect.first())
        assertEquals(tl(200), f.saved.grossAmount)
        assertEquals("200 market", f.saved.rawText) // açıklamaya dokunulmadı: sayı da güncellendi
        assertEquals(listOf("market"), f.saved.tokens)
    }

    @Test
    fun editedText_isKeptAsTyped_andTokensRecomputed() = testVm(market) { f ->
        type(f.state.amount, "210")
        type(f.state.text, "migros")
        f.vm.onEvent(ExpenseEditEvent.Save)

        assertEquals(ExpenseEditEffect.Done, f.vm.effect.first())
        assertEquals("migros", f.saved.rawText)
        assertEquals(listOf("migros"), f.saved.tokens)
    }

    @Test
    fun categoryCorrection_isLearned() = testVm(
        Expense(date = today, grossAmount = Money.of(90, Currency.TRY), rawText = "90 zıpzıp", tokens = listOf("zipzip")),
    ) { f ->
        f.vm.onEvent(ExpenseEditEvent.CategoryPicked(Category.YEMEK))
        f.vm.onEvent(ExpenseEditEvent.Save)

        assertEquals(ExpenseEditEffect.Done, f.vm.effect.first())
        assertEquals(Category.YEMEK, f.saved.category)
        assertEquals(Category.YEMEK, f.categorizer.categorize("zıpzıp").category) // bir dahaki sefere doğru tahmin
    }

    @Test
    fun dateShareAndCard_areSaved_futureDateIsClamped() = testVm(market) { f ->
        f.vm.onEvent(ExpenseEditEvent.DatePicked(LocalDate(2025, 2, 1)))
        assertEquals(today, f.state.date) // gelecek olamaz

        f.vm.onEvent(ExpenseEditEvent.DatePicked(LocalDate(2025, 1, 10)))
        f.vm.onEvent(ExpenseEditEvent.SharePicked(0.5))
        f.vm.onEvent(ExpenseEditEvent.CardPicked(1))
        f.vm.onEvent(ExpenseEditEvent.Save)

        assertEquals(ExpenseEditEffect.Done, f.vm.effect.first())
        assertEquals(LocalDate(2025, 1, 10), f.saved.date)
        assertEquals(0.5, f.saved.chargeableFraction)
        assertEquals(1L, f.saved.cardId)
    }

    @Test
    fun invalidAmount_isRejected() = testVm(market) { f ->
        type(f.state.amount, "0")
        f.vm.onEvent(ExpenseEditEvent.Save)
        runCurrent()

        assertTrue(f.state.amountError)
        assertEquals(tl(195), f.saved.grossAmount)

        type(f.state.amount, "196")
        assertFalse(f.state.amountError) // yazınca hata kalkar
    }

    @Test
    fun fixedPayment_keepsCategoryAndShare() = testVm(
        Expense(date = today, grossAmount = tl(15_000), category = Category.EV, rawText = "Kira", fixedExpenseId = 1),
        fixed = FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 15),
    ) { f ->
        assertTrue(f.state.isFixedPayment)
        assertEquals("Kira", f.state.fixedName)

        f.vm.onEvent(ExpenseEditEvent.CategoryPicked(Category.YEMEK))
        f.vm.onEvent(ExpenseEditEvent.SharePicked(0.5))
        type(f.state.amount, "15.500")
        f.vm.onEvent(ExpenseEditEvent.Save)

        assertEquals(ExpenseEditEffect.Done, f.vm.effect.first())
        assertEquals(tl(15_500), f.saved.grossAmount)
        assertEquals(Category.EV, f.saved.category)
        assertEquals(1.0, f.saved.chargeableFraction)
        assertEquals(1L, f.saved.fixedExpenseId)
    }

    @Test
    fun delete_afterConfirm() = testVm(market) { f ->
        f.vm.onEvent(ExpenseEditEvent.DeleteRequested)
        assertTrue(f.state.isConfirmingDelete)
        f.vm.onEvent(ExpenseEditEvent.DeleteConfirmed)

        assertEquals(ExpenseEditEffect.Done, f.vm.effect.first())
        assertTrue(f.expenses.all.value.isEmpty())
    }

    @Test
    fun missingExpense_isReported() = testVm(existing = null) { f ->
        assertTrue(f.state.isMissing)
        assertFalse(f.state.isLoading)
    }

    @Test
    fun replaceAmountInText_cases() {
        assertEquals("200 market", replaceAmountInText("195 market", tl(195), tl(200)))
        assertEquals("dün 1300 migros", replaceAmountInText("dün 1.250 migros", tl(1_250), tl(1_300)))
        assertEquals("99 kahve", replaceAmountInText("120,50 kahve", Money(12_050, Currency.TRY), tl(99)))
        assertEquals("yarım kilo kahve", replaceAmountInText("yarım kilo kahve", tl(80), tl(90)))
        // Metindeki sayı tutar değilse dokunulmaz ("2 kahve" 80 TL).
        assertEquals("2 kahve", replaceAmountInText("2 kahve", tl(80), tl(90)))
    }
}

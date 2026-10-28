package com.cinch.ui.history

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.HistoryRoute
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val today = LocalDate(2025, 3, 20)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(val vm: HistoryViewModel, val expenses: FakeExpenseRepository) {
        val state get() = vm.state.value
    }

    private fun testVm(
        route: HistoryRoute = HistoryRoute(),
        seed: List<Expense> = emptyList(),
        block: suspend TestScope.(Fixture) -> Unit,
    ) = withTestMain {
        runTest {
            val expenses = FakeExpenseRepository()
            val cards = FakeCardRepository(expenses).apply { add(Card(name = "QNB", carriedBalance = tl(0))) }
            seed.forEach { expenses.add(it) }
            val vm = HistoryViewModel(route, expenses, cards, FakeClock(today))
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            block(Fixture(vm, expenses))
            vm.viewModelScope.cancel()
        }
    }

    private fun TestScope.search(f: Fixture, text: String) {
        f.state.query.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private val seed = listOf(
        Expense(date = LocalDate(2025, 3, 18), grossAmount = Money.of(250, Currency.TRY), category = Category.MARKET, rawText = "250 Gıda", cardId = 1),
        Expense(date = LocalDate(2025, 3, 18), grossAmount = Money.of(80, Currency.TRY), category = Category.ULASIM, rawText = "80 taksi"),
        Expense(date = LocalDate(2025, 3, 2), grossAmount = Money.of(600, Currency.TRY), chargeableFraction = 0.5, category = Category.YEMEK, rawText = "600 yemek"),
        Expense(date = LocalDate(2025, 2, 10), grossAmount = Money.of(120, Currency.TRY), category = Category.MARKET, rawText = "120 migros"),
    )

    @Test
    fun monthView_groupsByDayNewestFirst() = testVm(seed = seed) { f ->
        val days = f.state.days
        assertEquals(listOf(LocalDate(2025, 3, 18), LocalDate(2025, 3, 2)), days.map { it.date })
        assertEquals(listOf("80 taksi", "250 Gıda"), days[0].expenses.map { it.rawText }) // aynı günde son eklenen üstte
        assertEquals(tl(330), days[0].total)
        assertEquals(tl(300), days[1].total) // paylaşılan: yalnız kullanıcının payı
        assertEquals(3, f.state.count)
        assertEquals(tl(630), f.state.total)
        assertEquals(mapOf(1L to "QNB"), f.state.cardNames)
    }

    @Test
    fun monthSwitching_stopsAtCurrentMonth() = testVm(seed = seed) { f ->
        assertFalse(f.state.canGoNext)
        f.vm.onEvent(HistoryEvent.NextMonth)
        runCurrent()
        assertEquals(LocalDate(2025, 3, 1), f.state.month)

        f.vm.onEvent(HistoryEvent.PreviousMonth)
        runCurrent()
        assertEquals(LocalDate(2025, 2, 1), f.state.month)
        assertEquals(listOf("120 migros"), f.state.days.flatMap { it.expenses }.map { it.rawText })
        assertTrue(f.state.canGoNext)
    }

    @Test
    fun search_coversAllMonths_ignoresTurkishLetters_andMatchesCardName() = testVm(seed = seed) { f ->
        search(f, "gida")
        assertTrue(f.state.isSearching)
        assertEquals(listOf("250 Gıda"), f.state.days.flatMap { it.expenses }.map { it.rawText })

        search(f, "MİGROS")
        assertEquals(listOf("120 migros"), f.state.days.flatMap { it.expenses }.map { it.rawText }) // geçen aydan

        search(f, "qnb")
        assertEquals(listOf("250 Gıda"), f.state.days.flatMap { it.expenses }.map { it.rawText })

        search(f, "ulaşım")
        assertEquals(listOf("80 taksi"), f.state.days.flatMap { it.expenses }.map { it.rawText }) // kategori adı

        search(f, "")
        assertFalse(f.state.isSearching)
        assertEquals(3, f.state.count)
    }

    @Test
    fun routeOpensCategoryAndMonth() = testVm(
        route = HistoryRoute(categoryName = "MARKET", year = 2025, month = 2),
        seed = seed,
    ) { f ->
        assertEquals(Category.MARKET, f.state.category)
        assertEquals(LocalDate(2025, 2, 1), f.state.month)
        assertEquals(listOf("120 migros"), f.state.days.flatMap { it.expenses }.map { it.rawText })

        f.vm.onEvent(HistoryEvent.CategoryPicked(null))
        f.vm.onEvent(HistoryEvent.NextMonth)
        runCurrent()
        assertEquals(3, f.state.count)
    }

    @Test
    fun delete_thenUndo() = testVm(seed = seed) { f ->
        val taxi = f.state.days[0].expenses[0]
        f.vm.onEvent(HistoryEvent.DeleteExpense(taxi))
        runCurrent()

        assertEquals(HistoryEffect.ExpenseDeleted(taxi), f.vm.effect.first())
        assertEquals(2, f.state.count)

        f.vm.onEvent(HistoryEvent.UndoDelete(taxi))
        runCurrent()
        assertEquals(3, f.state.count)
    }
}

package com.cinch.ui.home

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.InMemoryCorrectionStore
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeToday
import androidx.lifecycle.viewModelScope
import com.cinch.ui.withTestMain
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class HomeViewModelTest {

    private val monday = LocalDate(2025, 1, 13)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val vm: HomeViewModel,
        val budgets: FakeBudgetRepository,
        val expenses: FakeExpenseRepository,
        val today: FakeToday,
    ) {
        val state get() = vm.state.value
    }

    private fun testVm(
        budget: BudgetConfig? = BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY)),
        today: LocalDate = monday,
        block: suspend TestScope.(Fixture) -> Unit,
    ) = withTestMain {
        runTest {
            val budgets = FakeBudgetRepository(budget)
            val expenses = FakeExpenseRepository()
            val clock = FakeToday(today)
            val categorizer = Categorizer(corrections = InMemoryCorrectionStore())
            val vm = HomeViewModel(
                budgets = budgets,
                expenses = expenses,
                observeBudgetStatus = ObserveBudgetStatus(budgets, expenses, BudgetEngine()),
                parser = QuickEntryParser(categorizer),
                categorizer = categorizer,
                todayProvider = clock,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            block(Fixture(vm, budgets, expenses, clock))
            vm.viewModelScope.cancel()
        }
    }

    /** Klavyeden yazmayı taklit eder; Compose dışında snapshot bildirimini elle göndermek gerekir. */
    private fun TestScope.type(f: Fixture, text: String) {
        f.state.entry.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.save(f: Fixture) {
        f.vm.onEvent(HomeEvent.SaveEntry)
        runCurrent()
    }

    @Test
    fun status_comesFromStoredExpenses() = testVm { f ->
        f.expenses.add(Expense(date = monday, grossAmount = tl(50)))
        runCurrent()

        val status = f.state.status!!
        assertEquals(tl(50), status.todaySpent)
        assertEquals(tl(50), status.todayRemaining) // 700 / 7 = 100 günlük pay
        assertEquals(1, f.state.weekExpenses.size)
        assertFalse(f.state.isLoading)
    }

    @Test
    fun typing_showsLivePreview() = testVm { f ->
        type(f, "195 market")

        assertEquals(tl(195), f.state.parsed?.grossAmount)
        assertEquals(Category.MARKET, f.state.entryCategory)
        assertTrue(f.state.canSave)

        type(f, "")
        assertNull(f.state.parsed)
        assertFalse(f.state.canSave)
    }

    @Test
    fun entryWithoutAmount_isNotSaved() = testVm { f ->
        type(f, "market")
        assertFalse(f.state.canSave)

        save(f)

        assertTrue(f.expenses.all.value.isEmpty())
    }

    @Test
    fun save_storesParsedExpenseAndClearsEntry() = testVm { f ->
        type(f, "dün 120 taksi")
        save(f)

        val saved = f.expenses.all.value.single()
        assertEquals(LocalDate(2025, 1, 12), saved.date)
        assertEquals(tl(120), saved.grossAmount)
        assertEquals(Category.ULASIM, saved.category)
        assertEquals("dün 120 taksi", saved.rawText)
        assertTrue("taksi" in saved.tokens)

        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals("", f.state.entry.text.toString())
        assertNull(f.state.parsed)
        assertFalse(f.state.isSaving)
    }

    @Test
    fun pickedCategory_isSavedAndLearned() = testVm { f ->
        type(f, "80 kırtasiye")
        assertEquals(Category.DIGER, f.state.entryCategory)

        f.vm.onEvent(HomeEvent.CategoryPicked(Category.EV))
        save(f)
        assertEquals(Category.EV, f.expenses.all.value.single().category)

        // Aynı kelime artık kendiliğinden doğru tahmin edilir.
        type(f, "45 kırtasiye")
        assertEquals(Category.EV, f.state.parsed?.category)
        assertNull(f.state.categoryOverride)
    }

    @Test
    fun pickedCategory_resetsWhenEntryCleared() = testVm { f ->
        type(f, "80 kırtasiye")
        f.vm.onEvent(HomeEvent.CategoryPicked(Category.EV))
        type(f, "")
        type(f, "80 kırtasiye")

        assertEquals(Category.DIGER, f.state.entryCategory)
    }

    @Test
    fun delete_removesAndUndoRestores() = testVm { f ->
        f.expenses.add(Expense(date = monday, grossAmount = tl(60), rawText = "60 büfe", category = Category.YEMEK))
        runCurrent()
        val expense = f.state.weekExpenses.single()

        f.vm.onEvent(HomeEvent.DeleteExpense(expense))
        runCurrent()
        assertTrue(f.state.weekExpenses.isEmpty())

        val effect = f.vm.effect.first() as HomeEffect.ExpenseDeleted
        f.vm.onEvent(HomeEvent.UndoDelete(effect.expense))
        runCurrent()

        val restored = f.state.weekExpenses.single()
        assertEquals(expense.copy(id = restored.id), restored)
    }

    @Test
    fun weekExpenses_newestFirstAndOnlyThisWeek() = testVm(today = LocalDate(2025, 1, 15)) { f ->
        val first = f.expenses.add(Expense(date = monday, grossAmount = tl(10)))
        val wednesday = f.expenses.add(Expense(date = LocalDate(2025, 1, 15), grossAmount = tl(20)))
        val second = f.expenses.add(Expense(date = monday, grossAmount = tl(30)))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 12), grossAmount = tl(40))) // geçen hafta
        runCurrent()

        assertEquals(listOf(wednesday, second, first), f.state.weekExpenses.map { it.id })
    }

    @Test
    fun weekExpenses_followBudgetWeekStart() = testVm(
        budget = BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY), weekStart = DayOfWeek.SUNDAY),
    ) { f ->
        f.expenses.add(Expense(date = LocalDate(2025, 1, 12), grossAmount = tl(40))) // Pazar: bu hafta
        runCurrent()

        assertEquals(1, f.state.weekExpenses.size)
        assertEquals(tl(40), f.state.status!!.weekSpent)
    }

    @Test
    fun resumeOnNewDay_refreshesToday() = testVm { f ->
        f.expenses.add(Expense(date = monday, grossAmount = tl(50)))
        runCurrent()
        assertEquals(tl(50), f.state.status!!.todaySpent)

        f.today.date = LocalDate(2025, 1, 14)
        f.vm.onEvent(HomeEvent.Resumed)
        runCurrent()

        assertEquals(LocalDate(2025, 1, 14), f.state.today)
        assertEquals(tl(0), f.state.status!!.todaySpent)
    }
}

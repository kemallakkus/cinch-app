package com.cinch.ui.progress

import androidx.lifecycle.viewModelScope
import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.SavingsGoal
import com.cinch.shared.gamification.Badge
import com.cinch.shared.gamification.BadgeStore
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.gamification.WeeklyLeftover
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.prefs.InMemoryKeyValueStore
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeFixedExpenseRepository
import com.cinch.ui.FakeSavingsRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressStripViewModelTest {

    private val today = LocalDate(2025, 1, 15) // Çarşamba; geçen hafta 6–12 Ocak
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val savings: FakeSavingsRepository,
        val expenses: FakeExpenseRepository,
        val badgeStore: BadgeStore,
        val prefs: InMemoryKeyValueStore,
        val gamification: ObserveGamification,
        val clock: FakeClock,
    )

    private fun testFixture(budget: BudgetConfig?, block: suspend TestScope.(Fixture) -> Unit) = withTestMain {
        runTest {
            val savings = FakeSavingsRepository()
            val expenses = FakeExpenseRepository()
            val badgeStore = BadgeStore(InMemoryKeyValueStore())
            val gamification = ObserveGamification(
                FakeBudgetRepository(budget),
                expenses,
                savings,
                FakeFixedExpenseRepository(),
                BackupTracker(InMemoryKeyValueStore()),
                badgeStore,
            )
            block(Fixture(savings, expenses, badgeStore, InMemoryKeyValueStore(), gamification, FakeClock(today)))
        }
    }

    /** VM'i kurar, durumu ve etkileri dinler. */
    private fun TestScope.start(f: Fixture, effects: MutableList<ProgressStripEffect>): ProgressStripViewModel {
        val vm = ProgressStripViewModel(f.gamification, f.badgeStore, f.savings, f.prefs, f.clock)
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.effect.collect { effects += it } }
        runCurrent()
        return vm
    }

    @Test
    fun leftover_goesToGoal_once_andStaysHandled() = testFixture(BudgetConfig(weeklyLimit = tl(700))) { f ->
        f.expenses.add(Expense(date = LocalDate(2025, 1, 9), grossAmount = tl(80)))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 10), grossAmount = tl(100)))
        val goalId = f.savings.addGoal(SavingsGoal(name = "Tatil", emoji = "🏖️", target = tl(20_000), createdOn = today))
        val effects = mutableListOf<ProgressStripEffect>()
        val vm = start(f, effects)
        assertEquals(WeeklyLeftover(LocalDate(2025, 1, 6), tl(520)), vm.state.value.leftover)

        vm.onEvent(ProgressStripEvent.LeftoverToGoal(goalId))
        runCurrent()
        vm.onEvent(ProgressStripEvent.LeftoverToGoal(goalId)) // ikinci dokunuş bir şey yapmaz
        runCurrent()

        val deposit = f.savings.deposits.value.single()
        assertEquals(tl(520), deposit.amount)
        assertEquals(today, deposit.date)
        assertTrue(deposit.note.startsWith("Haftalık artan"))
        assertNull(vm.state.value.leftover)
        assertEquals(tl(520).minor, vm.state.value.savedTotal)
        assertEquals(1, effects.count { it is ProgressStripEffect.Celebrate && "kumbarasına" in it.message })

        // Uygulama yeniden açılınca da öneri geri gelmez.
        val again = start(f, mutableListOf())
        assertNull(again.state.value.leftover)

        vm.viewModelScope.cancel()
        again.viewModelScope.cancel()
    }

    @Test
    fun dismissedLeftover_hides_withoutDeposit() = testFixture(BudgetConfig(weeklyLimit = tl(700))) { f ->
        f.expenses.add(Expense(date = LocalDate(2025, 1, 9), grossAmount = tl(80)))
        val vm = start(f, mutableListOf())

        vm.onEvent(ProgressStripEvent.LeftoverDismissed)
        runCurrent()

        assertNull(vm.state.value.leftover)
        assertTrue(f.savings.deposits.value.isEmpty())
        vm.viewModelScope.cancel()
    }

    @Test
    fun newBadge_isRecordedAndCelebratedOnce() = testFixture(budget = null) { f ->
        val effects = mutableListOf<ProgressStripEffect>()
        val vm = start(f, effects)
        assertTrue(effects.isEmpty())

        f.expenses.add(Expense(date = today, grossAmount = tl(50)))
        runCurrent()
        f.expenses.add(Expense(date = today, grossAmount = tl(20)))
        runCurrent()

        val message = (effects.single() as ProgressStripEffect.Celebrate).message
        assertTrue("Yeni rozet" in message && Badge.FIRST_ENTRY.title in message, message)
        assertEquals(today, f.badgeStore.earnedOn(Badge.FIRST_ENTRY))

        // Yeni bir ekran açılışında aynı rozet yeniden kutlanmaz.
        val laterEffects = mutableListOf<ProgressStripEffect>()
        val again = start(f, laterEffects)
        assertTrue(laterEffects.isEmpty())

        vm.viewModelScope.cancel()
        again.viewModelScope.cancel()
    }
}

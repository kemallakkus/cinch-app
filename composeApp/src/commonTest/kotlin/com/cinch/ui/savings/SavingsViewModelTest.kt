package com.cinch.ui.savings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.SavingsGoal
import com.cinch.shared.gamification.BadgeStore
import com.cinch.shared.gamification.ObserveGamification
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsViewModelTest {

    private val today = LocalDate(2025, 1, 15) // Çarşamba; geçen hafta 6–12 Ocak
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private class Fixture(
        val vm: SavingsViewModel,
        val savings: FakeSavingsRepository,
        val expenses: FakeExpenseRepository,
        val effects: List<SavingsEffect>,
    ) {
        val state get() = vm.state.value
    }

    private fun testVm(block: suspend TestScope.(Fixture) -> Unit) = withTestMain {
        runTest {
            val savings = FakeSavingsRepository()
            val expenses = FakeExpenseRepository()
            val gamification = ObserveGamification(
                FakeBudgetRepository(BudgetConfig(weeklyLimit = tl(700))),
                expenses,
                savings,
                FakeFixedExpenseRepository(),
                BackupTracker(InMemoryKeyValueStore()),
                BadgeStore(InMemoryKeyValueStore()),
            )
            val vm = SavingsViewModel(savings, gamification, FakeClock(today))
            val effects = mutableListOf<SavingsEffect>()
            backgroundScope.launch { vm.state.collect {} }
            backgroundScope.launch { vm.effect.collect { effects += it } }
            runCurrent()
            block(Fixture(vm, savings, expenses, effects))
            vm.viewModelScope.cancel()
        }
    }

    private fun TestScope.type(field: TextFieldState, text: String) {
        field.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.send(f: Fixture, event: SavingsEvent) {
        f.vm.onEvent(event)
        runCurrent()
    }

    @Test
    fun addGoal_validatesThenSaves() = testVm { f ->
        send(f, SavingsEvent.AddGoalClicked)
        assertEquals(SavingsDialog.EditGoal(null), f.state.dialog)

        send(f, SavingsEvent.GoalSaved)
        assertTrue(f.state.nameError)
        assertTrue(f.state.amountError)
        assertTrue(f.savings.goals.value.isEmpty())

        type(f.state.name, "Tatil")
        type(f.state.target, "20.000")
        send(f, SavingsEvent.EmojiPicked("🏖️"))
        send(f, SavingsEvent.GoalSaved)

        val goal = f.savings.goals.value.single()
        assertEquals("Tatil", goal.name)
        assertEquals("🏖️", goal.emoji)
        assertEquals(tl(20_000), goal.target)
        assertEquals(today, goal.createdOn)
        assertNull(f.state.dialog)
        assertEquals(1, f.state.goals.size)
    }

    @Test
    fun deposits_fillTheGoal_andCelebrateOnce() = testVm { f ->
        f.savings.addGoal(SavingsGoal(name = "Telefon", target = tl(1_000), createdOn = today))
        runCurrent()
        val goal = f.state.goals.single().goal

        send(f, SavingsEvent.AddMoneyClicked(goal))
        type(f.state.amount, "600")
        send(f, SavingsEvent.MoveConfirmed)
        assertEquals(tl(600), f.state.goals.single().saved)
        assertTrue(f.effects.isEmpty())

        send(f, SavingsEvent.AddMoneyClicked(goal))
        type(f.state.amount, "400")
        send(f, SavingsEvent.MoveConfirmed)
        assertTrue(f.state.goals.single().isReached)
        assertEquals(listOf<SavingsEffect>(SavingsEffect.GoalReached("Telefon")), f.effects)

        // Dolmuş hedefe ek para tekrar kutlanmaz.
        send(f, SavingsEvent.AddMoneyClicked(goal))
        type(f.state.amount, "50")
        send(f, SavingsEvent.MoveConfirmed)
        assertEquals(1, f.effects.size)
        assertEquals(tl(1_050).minor, f.state.total)
    }

    @Test
    fun withdraw_cannotExceedSaved() = testVm { f ->
        val id = f.savings.addGoal(SavingsGoal(name = "Tatil", target = tl(1_000), createdOn = today))
        f.savings.addDeposit(SavingsDeposit(goalId = id, amount = tl(300), date = today))
        runCurrent()
        val goal = f.state.goals.single().goal

        send(f, SavingsEvent.WithdrawClicked(goal))
        type(f.state.amount, "500")
        send(f, SavingsEvent.MoveConfirmed)
        assertTrue(f.state.amountError)
        assertIs<SavingsDialog.Move>(f.state.dialog)
        assertEquals(1, f.savings.deposits.value.size)

        type(f.state.amount, "200")
        assertFalse(f.state.amountError) // yazınca hata kalkar
        send(f, SavingsEvent.MoveConfirmed)

        assertNull(f.state.dialog)
        assertEquals(tl(100), f.state.goals.single().saved)
        val withdrawal = f.savings.deposits.value.last()
        assertEquals(tl(-200), withdrawal.amount)
        assertEquals("Çekildi", withdrawal.note)
        assertTrue(f.effects.isEmpty())
    }

    @Test
    fun lastWeeksLeftover_isOfferedAsAmount() = testVm { f ->
        f.expenses.add(Expense(date = LocalDate(2025, 1, 9), grossAmount = tl(80)))
        f.expenses.add(Expense(date = LocalDate(2025, 1, 10), grossAmount = tl(100)))
        f.savings.addGoal(SavingsGoal(name = "Tatil", target = tl(20_000), createdOn = today))
        runCurrent()
        assertEquals(tl(520), f.state.leftover?.amount) // 700 − 180

        send(f, SavingsEvent.AddMoneyClicked(f.state.goals.single().goal))
        send(f, SavingsEvent.UseLeftover)
        Snapshot.sendApplyNotifications()
        runCurrent()
        send(f, SavingsEvent.MoveConfirmed)

        val deposit = f.savings.deposits.value.single()
        assertEquals(tl(520), deposit.amount)
        assertEquals("Haftalık artan", deposit.note)
    }

    @Test
    fun deletingGoal_removesItsDeposits() = testVm { f ->
        val id = f.savings.addGoal(SavingsGoal(name = "Tatil", target = tl(1_000), createdOn = today))
        f.savings.addDeposit(SavingsDeposit(goalId = id, amount = tl(300), date = today))
        runCurrent()

        send(f, SavingsEvent.DeleteGoalClicked(f.state.goals.single().goal))
        assertIs<SavingsDialog.DeleteGoal>(f.state.dialog)
        send(f, SavingsEvent.DeleteConfirmed)

        assertTrue(f.state.goals.isEmpty())
        assertTrue(f.savings.deposits.value.isEmpty())
    }
}

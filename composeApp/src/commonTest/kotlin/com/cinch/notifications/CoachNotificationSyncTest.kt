package com.cinch.notifications

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.coach.CoachNotification
import com.cinch.shared.coach.CoachNotificationPlanner
import com.cinch.shared.coach.CoachRules
import com.cinch.shared.coach.NotificationType
import com.cinch.shared.coach.PlanCoachWeek
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Platform zamanlayıcısının bellek içi karşılığı: ne bekliyor, ne iptal edildi. */
private class FakeScheduler : NotificationScheduler {
    val pending = mutableMapOf<String, CoachNotification>()
    val cancelled = mutableListOf<String>()
    var scheduled = 0

    override suspend fun pendingKeys(): Set<String> = pending.keys.toSet()

    override suspend fun cancel(keys: Set<String>) {
        keys.forEach { pending.remove(it) }
        cancelled += keys
    }

    override suspend fun schedule(notifications: List<CoachNotification>) {
        scheduled += notifications.size
        notifications.forEach { pending[it.scheduleKey()] = it }
    }

    val pendingTypes get() = pending.values.map { it.type }.toSet()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoachNotificationSyncTest {

    private val wednesday = LocalDate(2025, 1, 15)
    private val budget = BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY))

    private class Fixture(
        val sync: CoachNotificationSync,
        val scheduler: FakeScheduler,
        val budgets: FakeBudgetRepository,
        val expenses: FakeExpenseRepository,
    )

    private fun fixture(scope: CoroutineScope, budget: BudgetConfig? = this.budget): Fixture {
        val budgets = FakeBudgetRepository(budget)
        val expenses = FakeExpenseRepository()
        val cards = FakeCardRepository()
        val clock = FakeClock(wednesday, LocalTime(12, 0))
        val plan = PlanCoachWeek(budgets, expenses, cards, BudgetEngine(), CoachRules())
        val scheduler = FakeScheduler()
        val sync = CoachNotificationSync(
            CoachNotificationPlanner(plan, clock), scheduler, budgets, expenses, cards, clock, scope,
        )
        return Fixture(sync, scheduler, budgets, expenses)
    }

    private fun TestScope.fixture(budget: BudgetConfig? = this@CoachNotificationSyncTest.budget) =
        fixture(backgroundScope, budget)

    @Test
    fun sync_schedulesThePlan() = runTest {
        val f = fixture()
        f.sync.sync()

        val midday = f.scheduler.pending.values.filter { it.type == NotificationType.MIDDAY_REMAINING }
        assertEquals(LocalDate(2025, 1, 15), midday.minOf { it.date }) // 13:00 henüz geçmedi
        assertTrue(NotificationType.FORGOT_LOG in f.scheduler.pendingTypes) // bugün kayıt yok
    }

    @Test
    fun sync_isIdempotent() = runTest {
        val f = fixture()
        f.sync.sync()
        val scheduledOnce = f.scheduler.scheduled

        f.sync.sync()

        assertEquals(scheduledOnce, f.scheduler.scheduled)
        assertTrue(f.scheduler.cancelled.isEmpty())
    }

    @Test
    fun sync_cancelsWhatLeftThePlan() = runTest {
        val f = fixture()
        f.sync.sync()
        f.expenses.add(Expense(date = wednesday, grossAmount = Money.of(50, Currency.TRY)))

        f.sync.sync()

        // Bugün kayıt girildi: yarınki "kayıt unuttun" düşer; tutarı değişen mesajlar yenilenir.
        assertTrue(NotificationType.FORGOT_LOG !in f.scheduler.pendingTypes)
        assertTrue(f.scheduler.cancelled.isNotEmpty())
        assertTrue(NotificationType.MIDDAY_REMAINING in f.scheduler.pendingTypes)
    }

    @Test
    fun sync_withoutBudget_clearsEverything() = runTest {
        val f = fixture()
        f.sync.sync()
        f.budgets.config.value = null

        f.sync.sync()

        assertTrue(f.scheduler.pending.isEmpty())
    }

    @Test
    fun start_resyncsAfterDataChanges() = runTest {
        val f = fixture(budget = null)
        f.sync.start()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(f.scheduler.pending.isEmpty())

        f.budgets.save(budget)
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(NotificationType.MIDDAY_REMAINING in f.scheduler.pendingTypes)
    }

    @Test
    fun scheduleKey_changesWithMessage() {
        val n = CoachNotification(wednesday, LocalTime(13, 0), NotificationType.MIDDAY_REMAINING, 50, "100 ₺")
        assertTrue(n.scheduleKey().startsWith(COACH_KEY_PREFIX))
        assertEquals(n.scheduleKey(), n.copy().scheduleKey())
        assertNotEquals(n.scheduleKey(), n.copy(message = "80 ₺").scheduleKey())
    }
}

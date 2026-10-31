package com.cinch.widget

import com.cinch.navigation.DeepLinks
import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.gamification.BadgeStore
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.prefs.InMemoryKeyValueStore
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeFixedExpenseRepository
import com.cinch.ui.FakeSavingsRepository
import com.cinch.ui.common.display
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetSupportTest {

    private val today = LocalDate(2025, 1, 15) // Çarşamba
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private fun loader(budgets: FakeBudgetRepository, expenses: FakeExpenseRepository): Pair<LoadTodaySnapshot, ObserveBudgetStatus> {
        val status = ObserveBudgetStatus(budgets, expenses, BudgetEngine())
        val gamification = ObserveGamification(
            budgets, expenses, FakeSavingsRepository(), FakeFixedExpenseRepository(),
            BackupTracker(InMemoryKeyValueStore()), BadgeStore(InMemoryKeyValueStore()),
        )
        return LoadTodaySnapshot(status, gamification, FakeClock(today)) to status
    }

    @Test
    fun snapshot_showsTodaysShare_andFlagsOverspending() = runTest {
        val budgets = FakeBudgetRepository(BudgetConfig(weeklyLimit = tl(700)))
        val expenses = FakeExpenseRepository()
        val (load, status) = loader(budgets, expenses)
        expenses.add(Expense(date = today, grossAmount = tl(50)))

        val calm = load()
        val expected = status(today).first()!!
        assertTrue(calm.hasBudget)
        assertEquals(today, calm.day)
        assertFalse(calm.over)
        assertEquals(expected.todayRemaining.display(), calm.todayText)
        assertEquals("Bu hafta ${tl(50).display()} / ${tl(700).display()}", calm.weekText)

        expenses.add(Expense(date = today, grossAmount = tl(500)))
        val over = load()
        assertTrue(over.over)
        assertFalse(over.todayText.startsWith("-")) // "aştın" metniyle birlikte işaretsiz gösterilir
    }

    @Test
    fun snapshot_withoutBudget() = runTest {
        val (load, _) = loader(FakeBudgetRepository(null), FakeExpenseRepository())
        assertEquals(TodaySnapshot.NO_BUDGET, load())
    }

    @Test
    fun quickEntry_savesLikeTheHomeScreen_andExplainsMissingAmount() = runTest {
        val expenses = FakeExpenseRepository()
        val cards = FakeCardRepository(expenses)
        val qnb = cards.add(Card(name = "QNB", carriedBalance = tl(0)))
        val save = SaveQuickEntry(QuickEntryParser(), expenses, cards, FakeClock(today))

        val saved = save("195 market qnb")
        val stored = expenses.all.value.single()
        assertEquals(stored, saved)
        assertEquals(tl(195), stored.grossAmount)
        assertEquals(Category.MARKET, stored.category)
        assertEquals(qnb, stored.cardId)
        assertEquals(today, stored.date)
        assertTrue(SaveQuickEntry.message("195 market qnb", saved).startsWith("Kaydedildi: ${tl(195).display()}"))

        // Bildirimdeki "dün kayıt yoktu"ya yanıt: tarih yazılmadıysa dün.
        val yesterday = LocalDate(2025, 1, 14)
        assertEquals(yesterday, save("40 taksi", fallbackDate = yesterday)?.date)
        expenses.all.value = expenses.all.value.filterNot { it.date == yesterday }

        assertNull(save("market"))
        assertEquals(1, expenses.all.value.size) // tutarsız metin kaydedilmez
        assertTrue("tutar bulamadım" in SaveQuickEntry.message("market", null))
    }

    @Test
    fun quickEntry_linksToAFixedExpenseByName() = runTest {
        val expenses = FakeExpenseRepository()
        val fixed = FakeFixedExpenseRepository()
        val netflix = fixed.add(FixedExpense(name = "Netflix", amount = tl(199), dayOfMonth = 5, kind = FixedExpenseKind.SUBSCRIPTION))
        val save = SaveQuickEntry(QuickEntryParser(), expenses, FakeCardRepository(expenses), FakeClock(today), fixedExpenses = fixed)

        val saved = save("199 netflix")

        assertEquals(netflix, saved?.fixedExpenseId)
        assertEquals(netflix, expenses.all.value.single().fixedExpenseId)
        assertTrue("ödemesi olarak işaretlendi" in SaveQuickEntry.message("199 netflix", saved))
    }

    @Test
    fun deepLinks_areConsumedOnce() {
        DeepLinks.open(DeepLinks.QUICK_ADD)
        assertEquals(DeepLinks.QUICK_ADD, DeepLinks.pending.value)
        DeepLinks.consume()
        assertNull(DeepLinks.pending.value)
    }
}

package com.cinch.shared.budget

import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthRecapTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private val budget = BudgetConfig(weeklyLimit = tl(700)) // günlük pay 100
    private val today = LocalDate(2025, 3, 2)

    @Test
    fun previousMonth_totalsChangeDaysFixedAndSavings() {
        val expenses = listOf(
            Expense(date = LocalDate(2025, 1, 10), grossAmount = tl(1_000), category = Category.MARKET), // ilk kayıt
            Expense(date = LocalDate(2025, 2, 3), grossAmount = tl(600), category = Category.MARKET),
            Expense(date = LocalDate(2025, 2, 4), grossAmount = tl(150), category = Category.YEMEK), // payı aştı
            Expense(date = LocalDate(2025, 2, 5), grossAmount = tl(50), category = Category.YEMEK),
            Expense(date = LocalDate(2025, 2, 1), grossAmount = tl(15_000), category = Category.EV, fixedExpenseId = 1),
        )
        val deposits = listOf(
            SavingsDeposit(goalId = 1, amount = tl(500), date = LocalDate(2025, 2, 20)),
            SavingsDeposit(goalId = 1, amount = tl(-100), date = LocalDate(2025, 2, 25)),
            SavingsDeposit(goalId = 1, amount = tl(999), date = LocalDate(2025, 3, 1)), // başka ay
        )

        val recap = MonthRecaps.compute(MonthRecaps.previousMonth(today), expenses, deposits, budget, today)

        assertEquals(LocalDate(2025, 2, 1), recap.month)
        assertEquals(tl(800), recap.spent)
        assertEquals(tl(1_000), recap.previousSpent)
        assertEquals(-0.2, recap.change!!, 0.0001)
        assertEquals(Category.MARKET, recap.topCategory?.category)
        assertEquals(28, recap.daysCounted)
        assertEquals(26, recap.daysOnTrack) // 3 Şubat (600) ve 4 Şubat (150) payı aştı
        assertEquals(tl(15_000), recap.fixedPaid)
        assertEquals(tl(400), recap.saved)
        assertEquals(3, recap.entries)
    }

    @Test
    fun emptyMonth_andNoBudget() {
        val recap = MonthRecaps.compute(LocalDate(2025, 2, 1), emptyList(), emptyList(), budget = null, today = today)
        assertTrue(recap.isEmpty)
        assertNull(recap.daysOnTrack)
        assertNull(recap.change)
    }
}

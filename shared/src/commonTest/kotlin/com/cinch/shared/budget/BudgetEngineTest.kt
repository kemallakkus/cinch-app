package com.cinch.shared.budget

import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetEngineTest {

    private val engine = BudgetEngine()
    private val try700 = BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY))

    private fun exp(date: LocalDate, units: Long, fraction: Double = 1.0) = Expense(
        date = date,
        grossAmount = Money.of(units, Currency.TRY),
        chargeableFraction = fraction,
        category = Category.DIGER,
    )

    @Test
    fun startOfWeek_monday() {
        // 2025-01-15 Çarşamba → hafta başı Pazartesi 2025-01-13
        val ws = BudgetEngine.startOfWeek(LocalDate(2025, 1, 15), DayOfWeek.MONDAY)
        assertEquals(LocalDate(2025, 1, 13), ws)
    }

    @Test
    fun weeklyTotals_withSharedExpense() {
        val today = LocalDate(2025, 1, 15)
        val expenses = listOf(
            exp(LocalDate(2025, 1, 13), 200),          // 200
            exp(LocalDate(2025, 1, 15), 100, 0.5),     // pay 50
            exp(LocalDate(2024, 12, 31), 999),         // hafta ve ay dışı
        )
        val s = engine.status(expenses, try700, today)

        assertEquals(LocalDate(2025, 1, 13), s.weekStart)
        assertEquals(LocalDate(2025, 1, 19), s.weekEnd)
        assertEquals(25000, s.weekSpent.minor)        // 20000 + 5000
        assertEquals(45000, s.weekRemaining.minor)
        assertEquals(5, s.daysRemaining)              // 15→19 dahil
        assertEquals(9000, s.dailyAllowance.minor)    // 45000 / 5
        assertEquals(5000, s.todaySpent.minor)
        assertEquals(5000, s.todayRemaining.minor)    // (70000 - 20000) / 5 - 5000
        assertEquals(25000, s.monthTotal.minor)       // Ocak 2025
        assertFalse(s.isOverLimit)
    }

    @Test
    fun sharedExpense_onlyUserShareCountsToLimit() {
        val today = LocalDate(2025, 1, 13)
        // 1000 TRY harcama ama yarısı geri geldi → sadece 500 sınıra sayılır
        val s = engine.status(listOf(exp(today, 1000, 0.5)), try700, today)
        assertEquals(50000, s.weekSpent.minor)
        assertFalse(s.isOverLimit)
    }

    @Test
    fun todayRemaining_countsTodaysSpendingOnce() {
        // Pazartesi, 700 ₺: günlük pay 100. 50 harcayınca bugün 50 kalır (42,86 değil).
        val today = LocalDate(2025, 1, 13)
        val s = engine.status(listOf(exp(today, 50)), try700, today)
        assertEquals(5000, s.todayRemaining.minor)
        assertEquals(9285, s.dailyAllowance.minor) // 65000 / 7: kalan günlerin payı
    }

    @Test
    fun overLimit() {
        val today = LocalDate(2025, 1, 13)
        val cfg = BudgetConfig(weeklyLimit = Money.of(100, Currency.TRY))
        val s = engine.status(listOf(exp(today, 150)), cfg, today)
        assertTrue(s.isOverLimit)
        assertEquals(-5000, s.weekRemaining.minor)
        assertEquals(0, s.dailyAllowance.minor) // negatif kalan → 0 bölüşüm
    }

    @Test
    fun emptyWeek() {
        val today = LocalDate(2025, 1, 13)
        val s = engine.status(emptyList(), try700, today)
        assertEquals(0, s.weekSpent.minor)
        assertEquals(70000, s.weekRemaining.minor)
        assertEquals(10000, s.dailyAllowance.minor) // 70000 / 7
        assertEquals(0.0, s.pacePercent)
    }
}

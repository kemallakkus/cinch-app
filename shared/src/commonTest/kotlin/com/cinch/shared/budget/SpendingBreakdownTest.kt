package com.cinch.shared.budget

import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpendingBreakdownTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private fun spend(date: LocalDate, units: Long, category: Category, raw: String = "", tokens: List<String> = emptyList()) =
        Expense(date = date, grossAmount = tl(units), category = category, rawText = raw, tokens = tokens)

    @Test
    fun currentMonth_isComparedWithSameDaysOfLastMonth() {
        val today = LocalDate(2025, 3, 20)
        val period = BreakdownPeriod.of(today, today)
        val expenses = listOf(
            spend(LocalDate(2025, 3, 5), 300, Category.YEMEK),
            spend(LocalDate(2025, 3, 18), 500, Category.MARKET),
            spend(LocalDate(2025, 2, 10), 200, Category.YEMEK),
            spend(LocalDate(2025, 2, 25), 1_000, Category.YEMEK), // 20'sinden sonra: kıyasa girmez
            // Sabit gider ödemesi (kira) "Bu ay" özetinde; burada sayılmaz.
            Expense(date = LocalDate(2025, 3, 1), grossAmount = tl(15_000), category = Category.EV, fixedExpenseId = 1),
        )

        val b = SpendingBreakdowns.compute(expenses, period, Currency.TRY)

        assertEquals(LocalDate(2025, 2, 20), period.previousEnd)
        assertEquals(tl(800), b.total)
        assertEquals(tl(200), b.previousTotal)
        assertEquals(3.0, b.change) // 200 → 800
        assertEquals(listOf(Category.MARKET, Category.YEMEK), b.categories.map { it.category })
        assertNull(b.categories[0].change) // geçen ay market yoktu
        assertEquals(0.5, b.categories[1].change) // 200 → 300
        assertEquals(0.625f, b.categories[0].share)
    }

    @Test
    fun previousEnd_isClampedToShortMonth() {
        val today = LocalDate(2025, 3, 31)
        assertEquals(LocalDate(2025, 2, 28), BreakdownPeriod.of(today, today).previousEnd)
    }

    @Test
    fun pastMonth_isComparedWithWholePreviousMonth() {
        val period = BreakdownPeriod.of(LocalDate(2025, 2, 14), today = LocalDate(2025, 3, 20))

        assertFalse(period.isCurrentMonth)
        assertEquals(LocalDate(2025, 2, 1), period.start)
        assertEquals(LocalDate(2025, 2, 28), period.end)
        assertEquals(LocalDate(2025, 1, 1), period.previousStart)
        assertEquals(LocalDate(2025, 1, 31), period.previousEnd)
    }

    @Test
    fun categoryDroppedThisMonth_staysInListButNotInDonut() {
        val today = LocalDate(2025, 3, 20)
        val expenses = listOf(
            spend(LocalDate(2025, 3, 2), 100, Category.MARKET),
            spend(LocalDate(2025, 2, 2), 400, Category.EGLENCE),
        )

        val b = SpendingBreakdowns.compute(expenses, BreakdownPeriod.of(today, today), Currency.TRY)

        assertEquals(listOf(Category.MARKET, Category.EGLENCE), b.categories.map { it.category })
        assertEquals(-1.0, b.categories.last().change)
        assertEquals(listOf(Category.MARKET), b.slices.map { it.category })
    }

    @Test
    fun sharedExpense_countsOnlyUsersShare() {
        val today = LocalDate(2025, 3, 20)
        val half = Expense(date = today, grossAmount = tl(600), chargeableFraction = 0.5, category = Category.YEMEK)

        val b = SpendingBreakdowns.compute(listOf(half), BreakdownPeriod.of(today, today), Currency.TRY)

        assertEquals(tl(300), b.total)
    }

    @Test
    fun topWords_rankByAmount_keepTypedSpelling() {
        val today = LocalDate(2025, 3, 20)
        val expenses = listOf(
            spend(today, 45, Category.YEMEK, raw = "45 Büfe", tokens = listOf("bufe")),
            spend(today, 30, Category.YEMEK, raw = "30 büfe", tokens = listOf("bufe")),
            spend(today, 900, Category.MARKET, raw = "900 migros", tokens = listOf("migros")),
            spend(today, 20, Category.DIGER, raw = "20 ve", tokens = listOf("ve")), // çok kısa / genel kelime
        )

        val words = SpendingBreakdowns.compute(expenses, BreakdownPeriod.of(today, today), Currency.TRY).topWords

        assertEquals(listOf("migros", "Büfe"), words.map { it.word })
        assertEquals(TopWord("Büfe", tl(75), 2), words[1])
    }

    @Test
    fun noExpensesAnywhere_isEmpty() {
        val today = LocalDate(2025, 3, 20)
        val b = SpendingBreakdowns.compute(emptyList(), BreakdownPeriod.of(today, today), Currency.TRY)
        assertTrue(b.isEmpty)
        assertNull(b.change)
    }
}

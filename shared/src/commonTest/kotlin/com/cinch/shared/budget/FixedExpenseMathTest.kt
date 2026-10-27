package com.cinch.shared.budget

import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FixedExpenseMathTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private val today = LocalDate(2025, 2, 10)

    private fun fixed(id: Long, day: Int, amount: Long = 1_000) =
        FixedExpense(id = id, name = "Gider $id", amount = tl(amount), dayOfMonth = day)

    @Test
    fun dueDate_shortMonthUsesLastDay() {
        assertEquals(LocalDate(2025, 2, 28), fixed(1, 31).dueDateIn(today))
    }

    @Test
    fun dayOutsideMonthRange_isRejected() {
        assertFailsWith<IllegalArgumentException> { fixed(1, 0) }
    }

    @Test
    fun statuses_paidDueOverdueUpcoming_sortedByDay() {
        val payment = Expense(id = 9, date = LocalDate(2025, 2, 3), grossAmount = tl(950), fixedExpenseId = 2)
        val statuses = fixedStatuses(
            fixed = listOf(fixed(1, 25), fixed(2, 1), fixed(3, 10), fixed(4, 5)),
            monthExpenses = listOf(payment),
            today = today,
        )

        assertEquals(listOf(2L, 4L, 3L, 1L), statuses.map { it.fixed.id })
        assertEquals(
            listOf(FixedPaymentState.PAID, FixedPaymentState.OVERDUE, FixedPaymentState.DUE_TODAY, FixedPaymentState.UPCOMING),
            statuses.map { it.state },
        )
        // Ödenen: gerçek tutar; ödenmeyen: planlanan.
        assertEquals(tl(950), statuses[0].amountForMonth)
        assertEquals(tl(1_000), statuses[1].amountForMonth)
    }

    @Test
    fun weeklySuggestion_isFreeMoneyOverWeeks_roundedDownTo50() {
        assertEquals(tl(4_100), WeeklyLimitSuggestion.suggest(tl(40_000), tl(22_000))) // 18.000 / 4,35 ≈ 4.139
    }

    @Test
    fun weeklySuggestion_nullWhenNothingLeft() {
        assertNull(WeeklyLimitSuggestion.suggest(tl(20_000), tl(20_000)))
        assertNull(WeeklyLimitSuggestion.suggest(tl(20_000), tl(25_000)))
        assertNull(WeeklyLimitSuggestion.suggest(tl(0), tl(0)))
    }
}

package com.cinch.shared.domain

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IncomeTest {

    private fun income(day: Int?) = Income(name = "Maaş", amount = Money.of(40_000, Currency.TRY), dayOfMonth = day)

    @Test
    fun paydays_withinRange() {
        assertEquals(
            listOf(LocalDate(2025, 1, 15), LocalDate(2025, 2, 15)),
            income(15).paydaysBetween(LocalDate(2025, 1, 13), LocalDate(2025, 2, 20)),
        )
    }

    @Test
    fun paydays_shortMonthUsesLastDay() {
        assertEquals(
            listOf(LocalDate(2025, 2, 28)),
            income(31).paydaysBetween(LocalDate(2025, 2, 1), LocalDate(2025, 2, 28)),
        )
    }

    @Test
    fun paydays_emptyWithoutDay() {
        assertTrue(income(null).paydaysBetween(LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)).isEmpty())
    }

    @Test
    fun dayOutsideMonthRange_isRejected() {
        assertFailsWith<IllegalArgumentException> { income(0) }
        assertFailsWith<IllegalArgumentException> { income(32) }
    }
}

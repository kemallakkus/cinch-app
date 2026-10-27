package com.cinch.shared.budget

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BudgetConfigTest {

    private fun config(salaryDay: Int?) =
        BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY), salaryDay = salaryDay)

    @Test
    fun salaryDates_withinRange() {
        assertEquals(
            listOf(LocalDate(2025, 1, 15), LocalDate(2025, 2, 15)),
            config(15).salaryDatesBetween(LocalDate(2025, 1, 13), LocalDate(2025, 2, 20)),
        )
    }

    @Test
    fun salaryDates_shortMonthUsesLastDay() {
        assertEquals(
            listOf(LocalDate(2025, 2, 28)),
            config(31).salaryDatesBetween(LocalDate(2025, 2, 1), LocalDate(2025, 2, 28)),
        )
    }

    @Test
    fun salaryDates_emptyWithoutSalaryDay() {
        assertTrue(config(null).salaryDatesBetween(LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)).isEmpty())
    }

    @Test
    fun salaryDay_outsideMonthRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> { config(0) }
        assertFailsWith<IllegalArgumentException> { config(32) }
    }
}

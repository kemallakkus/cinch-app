package com.cinch.shared.domain

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardTest {

    private fun card(statementDay: Int?) =
        Card(name = "QNB", carriedBalance = Money.zero(Currency.TRY), statementDay = statementDay)

    @Test
    fun statementDates_withinRange() {
        val dates = card(15).statementDatesBetween(LocalDate(2025, 1, 10), LocalDate(2025, 3, 20))
        assertEquals(
            listOf(LocalDate(2025, 1, 15), LocalDate(2025, 2, 15), LocalDate(2025, 3, 15)),
            dates,
        )
    }

    @Test
    fun statementDates_excludesDaysOutsideRange() {
        // 5'i aralığın başından önce, Şubat 5'i sonundan sonra.
        val dates = card(5).statementDatesBetween(LocalDate(2025, 1, 10), LocalDate(2025, 2, 4))
        assertTrue(dates.isEmpty())
    }

    @Test
    fun statementDates_clampsToMonthEnd() {
        // 31 Şubat yok → 28 Şubat (2025 artık yıl değil); 2024'te 29 Şubat.
        assertEquals(
            listOf(LocalDate(2025, 1, 31), LocalDate(2025, 2, 28), LocalDate(2025, 3, 31)),
            card(31).statementDatesBetween(LocalDate(2025, 1, 1), LocalDate(2025, 3, 31)),
        )
        assertEquals(
            listOf(LocalDate(2024, 2, 29)),
            card(30).statementDatesBetween(LocalDate(2024, 2, 1), LocalDate(2024, 2, 29)),
        )
    }

    @Test
    fun statementDates_emptyWithoutStatementDay() {
        assertTrue(card(null).statementDatesBetween(LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)).isEmpty())
    }
}

package com.cinch.shared.budget

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetStatusRangeTest {

    @Test
    fun weekInsideMonth_rangeIsWholeMonth() {
        // 2025-01-15 Çarşamba: hafta 13–19 Ocak, ay 1–31 Ocak.
        val range = BudgetEngine.statusRange(LocalDate(2025, 1, 15), DayOfWeek.MONDAY)
        assertEquals(LocalDate(2025, 1, 1), range.start)
        assertEquals(LocalDate(2025, 1, 31), range.endInclusive)
    }

    @Test
    fun weekStartsInPreviousMonth_rangeExtendsBack() {
        // 2025-02-02 Pazar: hafta 27 Ocak–2 Şubat.
        val range = BudgetEngine.statusRange(LocalDate(2025, 2, 2), DayOfWeek.MONDAY)
        assertEquals(LocalDate(2025, 1, 27), range.start)
        assertEquals(LocalDate(2025, 2, 28), range.endInclusive)
    }

    @Test
    fun weekEndsInNextMonth_rangeExtendsForward() {
        // 2025-01-30 Perşembe: hafta 27 Ocak–2 Şubat.
        val range = BudgetEngine.statusRange(LocalDate(2025, 1, 30), DayOfWeek.MONDAY)
        assertEquals(LocalDate(2025, 1, 1), range.start)
        assertEquals(LocalDate(2025, 2, 2), range.endInclusive)
    }
}
